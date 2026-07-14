# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

RoBoHoN（シャープの人型ロボット電話・実機 SR-S05BJ）の会話を Claude LLM に担わせるプロジェクト。3コンポーネント構成:

- **robohon-app/** — Android アプリ（Java, minSdk/targetSdk 21, compileSdk 34, AGP 8.5.2/Gradle 8.14/JDK17）。起動ワードで立ち上がり、音声認識テキストを中継サーバへ POST し、応答を発話する。
- **relay-server/** — Node/TS 中継サーバ（pnpm）。`/chat` で Claude Haiku 4.5 を呼び、応答を ~150 字に分割した `utterances[]` と tool use 由来の `action` を返す。`/digest` はナレッジベース更新（Sonnet 5）。本番は Cloudflare Workers にデプロイ済み。
- **.claude/skills/robohon-sdk/** — SDK ナレッジベース。**ロボホン・HVML・voiceui に関する作業では必ずこのスキルを使う**（推測禁止、references/ が一次情報）。

全体設計は [docs/architecture.md](docs/architecture.md)、実機手順は [docs/workstream2-device-run.md](docs/workstream2-device-run.md)、既知の見送り課題は [docs/deferred-tasks.md](docs/deferred-tasks.md)。

## クローン直後のセットアップ（「セットアップして」と言われたら）

git 管理外のファイル（シャープ配布物・機密）が欠けているので、まず実行:

```powershell
pwsh -File scripts/setup.ps1   # 冪等。SDK展開・jarコピー・.env作成・pnpm install・local.properties雛形
```

自動化できないもの（スクリプトが残タスクとして表示する）:

1. **`RoBoHoN_SDK_2_0_0.zip`** — シャープの RoBoHoN 開発者サポートサイト（要開発者登録）から手動入手し、リポジトリ直下に置いて再実行。**再配布禁止のためネットから自動取得できない**。これが無いと `vendor/`（スキルが参照するテンプレ/サンプル/スキーマ）と `robohon-app/app/jar/*.jar`（コンパイル必須の compileOnly jar 6 本）を復元できず、**robohon-app はビルド不能**。relay-server の開発だけなら SDK 不要。
2. **`relay-server/.env`** の `ANTHROPIC_API_KEY` — 未設定でもモックモードで動く。
3. **`robohon-app/local.properties`** の `relay.url` / `relay.token` — 本番 Workers の URL とトークン（機密、コミット厳禁）。`BuildConfig.RELAY_URL` / `RELAY_TOKEN` に注入される。

検証: `cd relay-server; pnpm test`（キー不要）と `cd robohon-app; .\gradlew.bat assembleDebug`。

## よく使うコマンド

### relay-server（pnpm）

```powershell
cd relay-server
pnpm test              # gen-prompts → tsc → node --test dist/test/*.test.js
pnpm build             # gen-prompts → tsc
$env:MOCK="1"; pnpm start   # モックモードでローカル起動（ポート8787）
pnpm run start:env     # .env の実キーで起動
pnpm gen               # prompts/*.md → src/prompts/*.gen.ts の再生成のみ
pnpm run cf:dev        # wrangler dev
pnpm deploy            # Cloudflare Workers へデプロイ（wrangler deploy）
```

単一テスト: `pnpm build` 後に `node --test dist/test/split.test.js`。

Workers のシークレット: `npx wrangler secret put ANTHROPIC_API_KEY` / `npx wrangler secret put RELAY_TOKEN`。

### robohon-app（Gradle / 実機必須・エミュレータ不可）

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"   # JDK17(JBR)
cd robohon-app
.\gradlew.bat assembleDebug    # → app/build/outputs/apk/debug/app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

インストール（再インストール）でシステムの REQUEST_SCENARIO が飛び HVML シナリオが登録される（adb からの broadcast は protected で不可）。動作確認・トラブルシュートは docs/workstream2-device-run.md。

## アーキテクチャの要点

### データフロー

起動ワード（端末のロボホン名、HVML `home` シナリオ）→ `MainActivity` → 認識テキスト `${Lvcsr:Basic}` → OkHttp で relay の `POST /chat {sessionId, text, history, knowledge...}` → Claude 応答を `splitUtterances` で分割 → アプリが HVML 変数 `${com.robohon.template:speech}` を `onVoiceUIResolveVariable` で解決して順次発話 → `action` は Android Intent / 各 Util（歌・ダンス・カメラ等）にマップ。

### relay-server の二重ランタイム（最重要の落とし穴）

- ロジックは **`src/core.ts`（フレームワーク非依存）** に置く。`src/worker.ts` は Workers 用の**素の fetch ハンドラ**、`src/index.ts`/`app.ts` はローカル専用の Express。
- **Workers 側に Express を import してはならない**。body-parser→iconv-lite がバンドルで壊れる（`require_streams is not a function`）。`wrangler.toml` の `nodejs_compat` は Anthropic SDK 用。
- **ステートレス設計**: 会話履歴の権威は端末側。アプリが毎ターン直近履歴を送り、サーバ内 Map はローカル用フォールバック（Workers は isolate 間で共有されない）。
- **認証**: `/chat` `/digest` はヘッダ `X-Relay-Token` == シークレット `RELAY_TOKEN` 必須（未設定は 503 の fail-closed、不一致 401）。`/health` のみ無認証。**この認証を退行させない**。

### プロンプトはビルド時埋め込み

ペルソナ等の正データは `prompts/*.md`。`scripts/gen-prompts.mjs` が `src/prompts/*.gen.ts` を生成する（build/test/cf:dev/deploy で自動実行）。**`*.gen.ts` を直接編集しない**。Workers に実行時 fs が無いための方式。

### robohon-app の規約由来の必須挙動

- Sharp framework jar は **`compileOnly`**（実行時は端末が提供。APK に同梱しない）。
- ホームボタン（頭）で必ず `finish()`、`onPause` で発話停止・scene 無効化。
- HVML ファイル名はパッケージ名のドットを `_` にした接頭辞（`com_robohon_template_*.hvml`）。
- 詳細な制約・API は必ず `robohon-sdk` スキル経由で確認する。

## 機密・ライセンス（公開リポジトリなので特に注意）

- **コミット禁止**: `vendor/`、`RoBoHoN_SDK_2_0_0.zip`、`robohon-app/app/jar/*.jar`、`.claude/skills/robohon-sdk/references/source/`（いずれもシャープ配布物・再配布禁止）、`.env`、`local.properties`。すべて gitignore 済みだが、`git add -A` の前に `git status` で確認する習慣を守る（過去に実 URL が漏れた事故あり）。
- 本番 Workers の URL・`RELAY_TOKEN` はトラッキング対象ファイルに書かない（`local.properties` のみ）。
- APK の外部配布・営利利用はシャープ開発者規約で不可（私的利用の範囲で運用）。

## マシン固有の注意

開発PC固有の事情（プロキシ・証明書・ツールのパス等）は `CLAUDE.local.md`（gitignore 済み）に書く。このファイルには全環境共通の内容だけを置く。
