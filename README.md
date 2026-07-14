# robohon-intelligence

RoBoHoN（ロボホン）の会話のベースを **Claude（LLM）** に担わせ、必要に応じた応答とアプリ連携を自作するためのプロジェクト。標準のHVMLスクリプト会話を、起動ワードで入る自作アプリ＋外部LLMで“自由会話化”するのが狙い。

計画: `~/.claude/plans/robohon-llm-moonlit-stream.md`（ナレッジ整備 → 設計 → PoC → アプリ連携、各ゲートで継続判断）。

## 構成
```
robohon-intelligence/
├── .claude/skills/robohon-sdk/   RoBoHoN SDKナレッジベース兼Skill（HVML/API/制約の一次情報）
├── vendor/RoBoHoN_SDK_2_0_0/     公式SDK展開物（gitignore・再配布禁止）
├── robohon-app/                  自作Androidアプリ（現状: Templateを近代化した音声UI雛形）
├── docs/                         手順・設計メモ
│   └── workstream2-device-run.md 実機(SR-S05BJ)での実行手順
└── README.md
```

## クローン後のセットアップ

シャープ配布物（SDK・jar）や機密（.env, local.properties）は git 管理外のため、クローン直後はビルドできない。

```powershell
pwsh -File scripts/setup.ps1
```

で復元する（冪等）。`RoBoHoN_SDK_2_0_0.zip` だけは再配布禁止のため自動取得できない — RoBoHoN開発者サポートサイト（要開発者登録）から入手してリポジトリ直下に置いてから実行する。Claude Code に「セットアップして」と頼んでもよい（[CLAUDE.md](CLAUDE.md) に手順を記載）。

## 進捗
- **Workstream 1（SDKナレッジベース＋Skill）**: 完了。`robohon-sdk` スキルでHVML/API/制約を参照可能。
- **Workstream 2（環境構築・サンプル実機動作）**: PC側ビルド確立済み。`robohon-app` がCLIでビルド成功（APK生成）。実機(SR-S05BJ)での起動・発話確認が残ゲート → [docs/workstream2-device-run.md](docs/workstream2-device-run.md)。

## robohon-app のビルド

### 対象機
SR-S05BJ（第2世代「ロボホンlite」／Android 8.1 / API27 / Wi-Fi専用）。SDK対象機種。

### ツールチェーン（このPCで確認済み）
- Android Studio（同梱JBR = JDK17）, Android SDK（platform android-34, build-tools 34, platform-tools/adb）
- AGP **8.5.2** / Gradle **8.14** / compileSdk **34** / minSdk・targetSdk **21**
- Sharp voice-UI framework JAR は `compileOnly`（実機が実行時に提供、APKには非梱包）

### ビルド
```powershell
# Android Studio: robohon-app/ を開いて Run 'app'（推奨）
# もしくは CLI:
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
cd robohon-app
.\gradlew.bat assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### プロキシ/TLSに関する注意（重要）
この開発PCはTLS傍受プロキシ配下のため、素のJDKだとGradle/AGPのHTTPS取得が証明書エラー（PKIX）になる。対策として **Windowsルート証明書から作成した信頼ストア** を使う設定を **ユーザーレベル** に置いてある（リポジトリには含めない）:
- `~/.gradle/robohon-truststore.jks`（Windowsルートを取り込んだJBR cacertsのコピー）
- `~/.gradle/gradle.properties` に `systemProp.javax.net.ssl.trustStore=...` を設定済み

別PCで再現する場合は同様の信頼ストアを作って `~/.gradle/gradle.properties` に設定する（手順は docs 参照）。Android Studioも `~/.gradle/gradle.properties` を読むため同設定で解決する。

## ライセンス
`vendor/` のSDKと `.claude/skills/robohon-sdk/references/source/` の原本ドキュメントはシャープの配布物。**ローカル利用のみ・再配布禁止**（gitignore済み）。
