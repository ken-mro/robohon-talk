# 設計確定メモ（Phase A）— RoBoHoN × Claude LLM会話アプリ

Workstream 2 のGate通過後の設計確定。実装方針：**Node.js (TypeScript) 中継サーバ** ＋ **Claude Haiku 4.5**（音声会話の低遅延重視）＋ 初期アプリ連携ツール **他アプリ起動**。SDK仕様は `claude-api` スキルに準拠。

## 全体データフロー
```
[RoBoHoN実機 / robohon-app (Android)]
  起動ワード「ロボコンを起動して/オッケーロボコン」(home.hvml)
   → MainActivity起動 → 待受(listen) でユーザ発話
   → ${Lvcsr:Basic} 認識テキスト取得 (頭ボタンで待受が安定)
   → HTTP POST /chat {sessionId, text} を中継サーバへ(同一Wi-Fi)
                         │
[中継サーバ relay-server (Node/TS)]
   → 会話履歴に user追加 → Claude Messages API (haiku) [+tool use]
   → 応答を ~150字で分割、tool使用時はアプリ向けactionに変換
   ← {utterances:[...], action?:{...}, done}
                         │
[robohon-app]
   → onVoiceUIResolveVariable で ${com.robohon.template:speech} に
     utterancesを順次 setStringValue して連続発話(＋モーション)
   → action があれば Android Intent 実行（他アプリ起動）
   → listen=true なら次発話を待受けてループ／終了ワードで停止
```

## コンポーネント責務

### robohon-app（Android / 既存の `robohon-app/` を拡張）
- **会話ループ**：起動時挨拶 → 待受 → 認識テキスト送信 → 応答発話 → 待受…。
- **発話注入**：HVMLの `<speech>${com.robohon.template:speech}</speech>` を持つtopicを用意し、`onVoiceUIResolveVariable` で relay の `utterances` を1つずつ解決して発話（`code-map.md` の動的発話パターン）。長文は複数 `action`/topic 連鎖で連続発話。
- **アプリ連携**：relay の `action` を Android `Intent` にマップして `startActivity`（初期＝他アプリ起動）。
- **HTTP通信**：OkHttp等で relay に POST。タイムアウト・失敗時フォールバック発話（「ごめんね、今うまく考えられないみたい」）。
- **必須挙動**：頭/ホームボタンで `finish`、`onPause` で発話停止・scene無効化（ガイドライン）。

### relay-server（Node.js + TypeScript・PoC中は開発PCでローカル稼働）
- **Claude呼び出し**：`@anthropic-ai/sdk`、`client.messages.create`。**APIキーは `.env`（`ANTHROPIC_API_KEY`）でサーバ保持**、端末に置かない。
- **会話履歴管理**：`sessionId` 毎に `messages[]` を保持（メモリMap、PoCはプロセス内）。ステートレスAPIなので毎回全履歴送信。
- **応答整形**：本文を **~150字** で文末（。！？）優先分割し `utterances[]` に。
- **tool use 実行**：Claudeのtool呼び出しを受け、サーバ完結ツール（時刻等）は結果を返して継続、端末操作ツール（他アプリ起動）は `action` としてアプリへ返す。

## 中継サーバ API契約

### `POST /chat`
リクエスト:
```json
{ "sessionId": "string", "text": "ユーザの認識テキスト", "reset": false }
```
レスポンス:
```json
{
  "utterances": ["~150字の発話片", "..."],
  "action": { "type": "launch_app", "app": "camera" },   // 無い場合は null
  "done": false                                            // 会話終了示唆
}
```
- `reset:true` で履歴クリア（アプリ再起動時）。
- `action.type` 初期は `launch_app` のみ。`app` は論理名（後述）。

## Claude 呼び出し設計（Haiku 4.5）
- **モデル**: `claude-haiku-4-5`。
- **thinking/effort は使わない**（Haiku 4.5は `effort`/adaptive 非対応、かつ音声は低遅延優先）。
- `max_tokens`: 512程度（短い口語応答）。必要に応じstreamingでTTFB改善も可（PoCは非streamで可）。
- **system プロンプト**（要点）：
  - ロボホンらしい一人称「僕」・親しみやすい短い口語。専門的・長文を避ける。
  - **1発話は短く（目安40〜60字、最大でも数文）**。箇条書き・記号・URLは読み上げに不向きなので避ける。
  - 道具が必要な要求（写真を撮る等）は対応ツールを呼ぶ。
- **会話履歴**: `messages` に user/assistant を積む。コンテキスト200K上限、PoCでは直近N往復に丸めても可。

## tool use 設計（初期＝他アプリ起動）
ツール定義（`input_schema`、`strict:true`）:
```json
{
  "name": "launch_app",
  "description": "ユーザが他アプリ/機能の起動を求めたとき呼ぶ（例:写真を撮って→camera）",
  "strict": true,
  "input_schema": {
    "type": "object",
    "properties": { "app": { "type": "string", "enum": ["camera","album","clock","settings"] } },
    "required": ["app"],
    "additionalProperties": false
  }
}
```
- relay: `tool_use` を受けたら `tool_result`（「起動します」等の確認文）を返して会話継続しつつ、レスポンスの `action:{type:"launch_app", app}` をアプリへ返す。
- robohon-app: `app` 論理名 → Android Intent/Component にマップ（マッピング表はアプリ側で保持。標準アプリはパッケージ/ComponentName、ロボホン機能は各Utilクラス→`api-reference.md`）。Intent不可の機能は会話で代替し報告。

## セキュリティ／運用
- APIキーは relay の `.env`（gitignore済）。端末アプリやリポジトリに置かない。
- relay は開発PCでローカル起動、実機と同一Wi-Fi。将来クラウド移設可（要HTTPS・認証）。
- コスト監視：Haikuは低単価だが会話量に比例。`max_tokens`・履歴丸めで抑制。

## エラー処理／フォールバック
- relay→Claude失敗（ネット/429/5xx）：SDK自動リトライ後も失敗ならアプリへ定型フォールバック発話を返す。
- 認識空振り/タイムアウト：アプリ側で「もう一回言ってね」。
- `action` 実行不可：その機能のみ落として会話で説明（ガイドライン準拠）。

## 制約の再確認（設計に織り込み済）
- 発話文字数の公式上限は無いが**~150字分割**を採用（自然さ・先行事例）。自動モーション`assign`は読み<30字。
- クラウド音声認識は**月間上限**あり → PoCは多用を避け、必要なら独自ASR検討（Phase B/C課題）。
- 起動ワードは「ロボコン」（`docs/workstream2-device-run.md`）。「ロボホン」と紛れる場合は変更。
- SR-S05BJ=歩行モデル（13サーボ）。モーション/歩行演出を発話に絡め可。

## 検証（Phase B PoCのGate）
実機で「起動→話しかける→Claude応答が分割発話される→終了ワードで停止」が安定往復すればGate達成。`launch_app`は「写真を撮って」→cameraが起動するかで確認。レイテンシ・認識精度・往復成功率をチェックリスト化。

## 次（Phase B 実装範囲）
1. relay-server 雛形（/chat, Claude haiku呼び出し, 履歴, 150字分割）＋ローカル単体テスト（モック認識→分割JSON）。
2. robohon-app に会話Activity/HVML（待受topic＋`${pkg:speech}`解決）＋HTTPクライアントを追加。
3. 実機で往復確認（まずtool use無し）→ 続けて `launch_app`（Phase C入口）。
