# robohon-talk

RoBoHoN（ロボホン・シャープの人型ロボット電話）の会話を **Claude（LLM）** に担わせる自作プロジェクト。標準のHVMLシナリオ会話を、起動ワードで立ち上がる自作アプリ＋外部LLMで“自由会話化”する。

できること:

- **自由会話**: 音声認識テキストを中継サーバ経由で Claude Haiku 4.5 に渡し、ロボホンらしい口調で応答・発話（応答が速ければ相づちなしで即答）。
- **2種類の記憶**: 端末に全件残る会話履歴（短期）と、会話から Claude Sonnet 5 が毎日抽出・統合するナレッジベース「いつものこと/さいきんのこと」（長期）。会話するほど話が通じるようになる。
- **会話の流れでアクション**: tool use で純正アプリ起動（カメラ・アルバム・占い など約20種）、歌・ダンス・モーション再生、日記の書き込み、Web検索。
- **名前入りハッピーバースデー**: 会話の文脈から相手を判断し、宛名入りでハッピーバースデーを歌う（メロディを2分割し、間に宛名をTTSで挟む純正同等の構造）。

## 構成

```
robohon-talk/
├── robohon-app/                  自作Androidアプリ（Java）。起動ワードで立ち上がり、認識テキストを
│                                 中継サーバへPOSTして応答を発話。記憶・アクション実行も担う。
├── relay-server/                 Node/TS 中継サーバ。/chat (Haiku 4.5) と /digest (Sonnet 5)。
│                                 Cloudflare Workers にデプロイ可（ローカルExpress起動も可）。
├── .claude/skills/robohon-sdk/   RoBoHoN SDKナレッジベース兼スキル（HVML/API/制約の一次情報）
├── vendor/RoBoHoN_SDK_2_0_0/     公式SDK展開物（gitignore・再配布禁止）
├── scripts/setup.ps1             クローン後セットアップ（冪等）
└── docs/                         設計メモ・実機手順
```

全体設計は [docs/architecture.md](docs/architecture.md)、実機での動かし方は [docs/workstream2-device-run.md](docs/workstream2-device-run.md)。

## 必要なもの

| もの | 備考 |
|---|---|
| RoBoHoN 実機 | 開発済み確認は SR-S05BJ（Android 8.1 / Wi-Fi専用）。エミュレータ不可 |
| RoBoHoN 開発者登録 + SDK | [開発者サポートサイト](https://robohon.com/biz/develop.php)から `RoBoHoN_SDK_2_0_0.zip` を入手（再配布禁止のため各自入手） |
| Anthropic API キー | 中継サーバが Claude を呼ぶのに使用。未設定でもモックモードで動作確認は可能 |
| Cloudflare アカウント | 中継サーバを Workers で常時稼働させる場合（無料枠で可）。PCローカル起動でも代替可 |
| Android Studio + Android SDK | JDK17（同梱JBRでよい）/ platform android-34 / platform-tools(adb) |
| Node.js + pnpm | relay-server 用（`corepack enable` 推奨） |
| PowerShell (pwsh) | セットアップスクリプト用 |

## インストール方法

### 1. クローンとセットアップ

シャープ配布物（SDK・jar）や機密（`.env`, `local.properties`）は git 管理外のため、クローン直後はそのままではビルドできない。入手した `RoBoHoN_SDK_2_0_0.zip` をリポジトリ直下（または Downloads）に置いて:

```powershell
git clone https://github.com/ken-mro/robohon-talk.git
cd robohon-talk
pwsh -File scripts/setup.ps1
```

スクリプトは冪等で、SDK展開・フレームワークjarコピー・`.env` 雛形作成・`pnpm install`・`local.properties` 雛形生成まで行い、残りの手動タスクを表示する。Claude Code に「セットアップして」と頼んでもよい（[CLAUDE.md](CLAUDE.md) に手順を記載）。

### 2. 中継サーバ（relay-server）

まずローカルで動作確認（APIキー不要のモックモード）:

```powershell
cd relay-server
pnpm test            # 単体テスト
$env:MOCK="1"; pnpm start    # ポート8787で起動
```

本番は Cloudflare Workers へデプロイ:

```powershell
npx wrangler login
npx wrangler secret put ANTHROPIC_API_KEY   # Claude APIキー
npx wrangler secret put RELAY_TOKEN         # 任意のランダム文字列（アプリと共有する認証トークン）
pnpm deploy
# → https://robohon-talk.<subdomain>.workers.dev が払い出される
curl https://robohon-talk.<subdomain>.workers.dev/health   # {"ok":true,...} なら成功
```

`/chat` `/digest` はヘッダ `X-Relay-Token` による認証必須（`RELAY_TOKEN` 未設定時は fail-closed）。ローカル起動で使う場合は `.env` に `ANTHROPIC_API_KEY` と `RELAY_TOKEN` を設定する。

### 3. Androidアプリ（robohon-app）

`robohon-app/local.properties`（gitignore済み・コミット厳禁）に中継サーバの接続先を設定:

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
relay.url=https://robohon-talk.<subdomain>.workers.dev/chat
relay.token=<RELAY_TOKEN に設定した値>
```

ビルドしてインストール:

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
cd robohon-app
.\gradlew.bat assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

（Android Studio で `robohon-app/` を開いて Run 'app' でもよい。）インストール時に HVML シナリオが自動登録され、起動ワードが有効になる。

### 4. 実機の準備と起動

実機側は USBデバッグの有効化・Wi-Fi接続・マナースイッチOFF などが必要。詳細手順とトラブルシュートは [docs/workstream2-device-run.md](docs/workstream2-device-run.md)。

起動ワードは**端末に設定されているロボホンの名前**（例:「たろう」「ねえねえたろう」）。話しかけるとアプリが立ち上がり、「はーい！なにー？」の後は自由会話。終了は頭ボタンか「ばいばい」。

## 開発メモ

- relay-server のロジックは `src/core.ts`（フレームワーク非依存）。Workers 用エントリは `src/worker.ts`、ローカル用 Express は `src/index.ts`。**Workers 側に Express を import しない**こと。
- ペルソナ等のプロンプト正データは `relay-server/prompts/*.md`。ビルド時に `src/prompts/*.gen.ts` へ埋め込まれる（`*.gen.ts` は直接編集しない）。
- 会話履歴・ナレッジベースの権威は端末側（ステートレス設計）。サーバは保存しない。
- Sharp framework jar は `compileOnly`（実行時は端末が提供。APKに同梱しない）。
- TLS傍受プロキシ配下のPCでは、Windowsルート証明書から作った信頼ストアを `~/.gradle/gradle.properties`（`systemProp.javax.net.ssl.trustStore=...`）と `NODE_EXTRA_CA_CERTS` に設定すると Gradle/pnpm のHTTPS取得が通る。

## ライセンス・注意

- `vendor/` のSDK・フレームワークjar・`.claude/skills/robohon-sdk/references/source/` の原本ドキュメントはシャープの配布物。**ローカル利用のみ・再配布禁止**（gitignore済み）。
- ビルドしたAPKの外部配布・営利利用はシャープの開発者規約で不可。**私的利用の範囲**で運用すること。
- `RELAY_TOKEN`・Workers の実URL・APIキーはコミットしない（`local.properties` / `.env` / Workers シークレットのみに置く）。
