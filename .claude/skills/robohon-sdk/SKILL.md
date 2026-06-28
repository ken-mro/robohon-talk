---
name: robohon-sdk
description: >-
  RoBoHoN（ロボホン / Sharp の人型ロボット電話）のアプリ開発に関するあらゆる作業で必ず使うナレッジベース。
  HVML（音声シナリオ）の記述・デバッグ、voiceui API（VoiceUIManager / VoiceUIListener / VoiceUIVariable）での
  発話・音声認識・scene/accost・変数解決、シナリオ登録、ロボホンのモーション/カメラ/歌/ダンス/メッセージ/
  プロジェクタ連携（Intent）、開発環境構築（Android Studio・JAR・adb・USBドライバ）、RbDevtool / KanaHighLow
  などの開発ツール、そしてClaude等のLLMをロボホンの会話に組み込む設計を扱うときに使用する。
  「ロボホン」「RoBoHoN」「HVML」「voiceui」「accost」「Lvcsr」「ロボホンSDK」「ロボホンアプリ」「ロボホン 発話/会話/音声認識」
  といった語が出たら、明示的にSDKと言われなくても必ずこのスキルを参照すること。本リポジトリ（robohon-intelligence）の
  LLM会話アプリ開発でも基盤として使う。
---

# RoBoHoN SDK ナレッジベース

RoBoHoN（ロボホン）アプリ開発の一次情報を構造化したスキル。**推測で答えず、必ず下記 references を読んで根拠に基づいて回答・実装する**こと。元データは公式SDK v2.0.0（同梱コード・HVMLスキーマ）と公式ドキュメント（スタートガイド/HVMLリファレンス/開発ガイドライン/開発ツールマニュアル/APIリファレンス Build 3.1.0）。

## まず押さえる要点（誤りやすい事実）

- **RoBoHoN標準会話はHVML（XML）のシナリオ方式**で、生成AIのような自由会話・学習はしない。自由会話化するには自作アプリ＋外部LLMが必要。
- **発話の動的テキスト**はHVMLの `${パッケージ名:変数}` をアプリが `onVoiceUIResolveVariable` で `setStringValue` して注入する（＝LLM応答の差し込み口）。
- **音声認識**は `${Lvcsr:Basic}`（かな漢字）/`${Lvcsr:Kana}`（カタカナ）等への大語彙連続認識。条件演算子は `eq` / `in` / `include` / `near`（4文字以上）/ `and`。Yes/Noは `dict="Reply"`。
- **発話の文字数上限はHVML公式仕様には存在しない**（「200字」等は先行事例の実用知見であり仕様ではない）。一方 **自動モーション `assign` は読み<30文字の発話に付与**される。長文は複数 `action`/`topic` への分割が無難。
- **クラウド音声認識の月間アクセス回数に上限あり**（具体数は公式非公開。超過すると音声起点のシナリオ選択が止まる）。多用設計では要注意。
- **トリガーは `user-word` と `env-event`** が実用。`timer` はネイティブ非対応（日時変数 or アプリ起点 `accost` で代替）。
- **対象機種/OS**: 1stgen SR01MW/SR02MW（Android 5.0.2 / API21）、2ndgen SR03M/SR04M/SR05M（Android 8.1 / API27）。**開発はAPI level 21ターゲット**、実機必須（エミュレータ不可）。クラウド機能はCocoro/ビジネスプラン契約が必要。
- **必須挙動**: ホームボタン（`ACTION_CLOSE_SYSTEM_DIALOGS`）で必ず `finish()`、`onPause` で発話停止・scene無効化。常駐Serviceの処理周期は30分未満禁止。
- HVMLファイル名は **パッケージ名のドットを `_` に置換した接頭辞**で始まりASCII・UTF-8。`<hvml version="2.0">` 固定。

## どの reference を読むか（用途別ルーティング）

| やりたいこと | 読むファイル |
|---|---|
| HVMLを書く/読む・タグや認識/発話/モーションの意味を知る | [references/hvml-reference.md](references/hvml-reference.md)（全タグ・演算子・変数・モーションID表） |
| HVMLの要素/属性/列挙値を素早く確認（早見表） | [references/hvml-schema-notes.md](references/hvml-schema-notes.md) |
| Javaから発話/認識/scene/記憶/言語/連携を実装・APIシグネチャ確認 | [references/api-reference.md](references/api-reference.md)（voiceui中心＋各Util） |
| アプリ全体構造・会話の実行フロー・テンプレ/サンプルの対応 | [references/code-map.md](references/code-map.md) |
| 開発環境構築（Android Studio/JAR/adb/USBドライバ/構文チェックツール） | [references/develop-start-guide.md](references/develop-start-guide.md) |
| 設計/挙動ルール・**全制約と数値上限**・権限・キャラクタ性 | [references/develop-guideline.md](references/develop-guideline.md)（「制約・制限」節） |
| 実機での発話テスト・疑似音声認識注入・抑揚調整 | [references/devtool-manual.md](references/devtool-manual.md) |

SDKの実体コード（テンプレ/サンプル/JAR/スキーマ）は `vendor/RoBoHoN_SDK_2_0_0/` にある。具体実装を確認するときはそこを直接読む（特に `template/TemplateFull`）。

## 作業の進め方

1. **HVMLを書くとき**: まず hvml-schema-notes で構造を確認し、タグの意味/認識式/モーションは hvml-reference を参照。ファイル名規則・1ホームシナリオ制約・priority範囲(78–84)を守る。実例は `vendor/.../template/TemplateFull/app/src/main/assets/hvml/` を流用。
2. **Java実装のとき**: code-map で会話フロー（registerScenario → enableScene → startSpeech → コールバック）を把握し、api-reference でメソッド確認。`onVoiceUIResolveVariable` での変数解決＝動的発話、`onVoiceUIActionEnd` の `function` 分岐＝アプリ連携。
3. **デバッグ/実機確認のとき**: devtool-manual の RbDevtool Web UI（`http://<device-ip>:48000`）で発話・疑似認識を注入。マイク無しでもシナリオ分岐をテストできる。
4. **LLM会話アプリ（本プロジェクト）**: 認識テキスト→中継サーバ→Claude→応答を `${pkg:speech}` に注入して発話、tool use結果を `control`/Intent にマップ。設計の指針は code-map 末尾「LLM会話アプリへの応用ポイント」を参照。常に上記の制約（認識回数上限・必須挙動）を踏まえる。

## ライセンス注意
`vendor/` のSDKと `references/source/` の原本PDF/HTMLはシャープの配布物。**ローカル利用に留め、公開リポジトリ等へ再配布しない**こと（リポジトリ管理する場合は `.gitignore` 済み）。
