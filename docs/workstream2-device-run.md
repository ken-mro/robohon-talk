# Workstream 2 — 実機(SR-S05BJ)でサンプルを動かす手順

対象機: **SR-S05BJ**（公式仕様グループ SR-06M／SR-S05BJ／SR-S06BJ／SR-S07BJ。CPU Snapdragon 430・**サーボ13個＝二足歩行モデル**・Wi-Fi a/b/g/n(2.4/5GHz)/ac）。SDK同梱ドキュメントの機種一覧（〜SR05M, 2019年）より新しい型番だが同系のvoiceuiフレームワークと見られ、SDK動作は本ゲートで確定する。本リポジトリの `robohon-app/`（Templateを現行ツールに近代化した音声UIアプリ）をインストールし「起動ワード→発話」が通ることを確認する。

> 出典: 公式仕様 https://jp.sharp/support/robohon/doc/web_mn/sr06m_srs05bj/09-08.html ／ 手順はスキル `robohon-sdk` の develop-start-guide.md（3.1.4 / 3.2.1 / 3.2.2 / 4.1）。
> 注: 当初「lite/7サーボ/歩行不可」と記載していたのは誤り（別機種SR-05M-Yとの取り違え）。SR-S05BJは13サーボで二足歩行可能。

## 前提（PC側・確認済み）
- Android Studio 導入済み、Android SDK（platform-tools/adb, platform android-34, build-tools 34）あり。
- `robohon-app/` は AGP 8.5.2 / Gradle 8.14 / JDK17(JBR) / compileSdk34・min/target21 で構成済み。
- 社内プロキシのTLS傍受があるため、**CLIビルドはWindowsルート証明書から作った信頼ストアを使用**（Android Studio経由なら通常そのまま通る）。

## A. ロボホン本体の準備（ユーザ操作・要実機）
SR-S05BJ の背面LCDで操作します。

1. **ソフト更新**: 設定 →「端末情報」→「ソフトウェア更新」で最新へ（ビルド番号 03.01.00 以降が前提）。
2. **開発者向けオプションを表示**: 設定 →「端末情報」→「ビルド番号」を**7回タップ**。
3. 設定に戻り最下部の「その他」→「**開発者向けオプション**」を **OFF→ON**。確認ダイアログは「OK」。
4. 「**USBデバッグ**」を **ON**（確認は「OK」）。
5. 「**スリープモードにしない**」も **ON**（開発中の自動スリープ防止）。
6. **マナースイッチをOFF**（マナーモードだと発話・モーションが出ない）。
7. **Wi-Fi接続**を確立（SR-S05BJはWi-Fi専用。後のクラウド機能/LLM中継に必須）。

## B. PCと接続（ユーザ操作＋PC）
8. **microUSBケーブル**でロボホンとPCを接続（SR-S05BJのmicroUSB端子は側面）。
9. 本体に「**USBデバッグを許可しますか？**」が出たら「このPCを常に許可」にチェックして「OK」。
10. **adb認識確認**（PowerShell）:
    ```powershell
    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
    ```
    - 端末が `device` として表示されればOK。
    - 表示されない/`unauthorized` の場合 → 手順9のダイアログ許可、USBケーブル（データ通信対応か）、下記Cのドライバを確認。

## C. USBドライバ（Windowsで未認識のときのみ）
adbに出てこない場合、SDK同梱ドライバを当てる:
1. `usb_driver_SHARP_RoBoHoN_r4.1.zip`（Downloads）を展開。
2. デバイスマネージャーで「**Android ADB Interface**」（不明なデバイス/「!」付き等）を右クリック →「ドライバーの更新」。
3. 「コンピューターを参照」→ 展開フォルダを指定 → 「Sharp Corporation を常に信頼」にチェックしてインストール。

## D. アプリのインストールと実行
**方法1: Android Studio（推奨・証明書も通りやすい）**
1. Android Studio で `c:\Source\Repos\robohon-intelligence\robohon-app` を開く（Open an existing project）。
2. Gradle同期が完了したら、接続中の **SHARP SR-S05BJ** を選び **Run 'app'**。

**方法2: CLIでビルド済みAPKを直接インストール**
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "c:\Source\Repos\robohon-intelligence\robohon-app\app\build\outputs\apk\debug\app-debug.apk"
```

## E. 動作確認（ゲート判定）
インストール後、シナリオ登録(`RegisterScenarioService`)が走り、ホーム起動ワードが有効になります。
1. ロボホンに「**{名前}**」/「**ねえねえ{名前}**」と話しかける（home.hvmlの起動ワード `WORD_APPLICATION` / `WORD_APPLICATION_FREEWORD`。{名前}は端末のロボホン名。詳細は末尾「起動ワード（現行）」）。
2. アプリが起動し、`onResume` の `startSpeech` で greet（「**{名前}だよー。なになにー？**」）を発話すればゲート達成。※初回起動時は先に外部送信の同意ダイアログが背中の画面に出るので、選択してから会話が始まる。
3. 何か話しかけると認識テキストが中継サーバ(Claude)へ渡り、応答が発話される（音声認識＋LLM会話の確認）。
4. 終了は **頭ボタン**（ホーム）押下、または「ばいばい/またね」等の終了ワード、バックグラウンドで `finish()`。

### マイクを使わないテスト（任意・DevTool）
`RbDevtool_V01_00_00.apk` を入れてONにすると `http://<ロボホンのIP>:48000` のWeb UIから「発話させる」「話しかける(Lvcsr:Basic/Kana注入)」が可能。マイク無しでシナリオ分岐を検証できる（devtool-manual.md参照）。

## トラブルシュート
- **発話しない** → マナーモードOFF、音量、Instant Run無効（旧AS）、アプリ再インストール。
- **起動ワードが効かない** → シナリオ未登録の可能性。アプリを一度起動（Android Studioから直接Activity起動）してから再度音声起動、または再インストール。
- **adb unauthorized** → 本体の許可ダイアログ承認、`adb kill-server; adb start-server`。
- **モーション** → SR-S05BJは13サーボの歩行モデルなので歩行系 `behavior` も利用可（マナーモード/充電中/USB接続中など実行不可条件はガイドライン参照）。

## 起動ワード（現行）
起動ワードは**その端末に設定されているロボホンの名前**（かな）で決まる。固定文言ではない。
- 標準: 「**{名前}**」（`${Local:WORD_APPLICATION} eq {名前}`）
- フリーワード: 「**ねえねえ{名前}**」（`${Local:WORD_APPLICATION_FREEWORD} eq ねえねえ{名前}`）
- 定義: `app/src/main/assets/hvml/home/com_robohon_template_home.hvml`（プレースホルダ `__ROBO_KANA__`）
- **名前の決まり方**: HVMLの `__ROBO_KANA__` を、インストール時に `RegisterScenarioService` が
  アドレス帳のロボホン名（`RoboProfileData.getRbname()`、かな）へ置換する。取得できなければ既定「**ろぼほん**」。
  → 名前が「たろう」なら起動ワードは「**たろう**」/「**ねえねえたろう**」。
- **この端末での実際の名前を確認**: インストール時のログに出る。
  `adb logcat -d | Select-String "home launch word kana"` で置換後の名前がわかる。
- **操作のコツ**: 認識が不安定なときは**頭ボタンを押して待ち受け状態にしてから**話す。
  誤認識が続く場合は本体でロボホンの名前を認識しやすい別名（あいぼう/くろーど 等）に変更すると安定する。

## ゲート結果（2026-06-28 達成）
- [x] adb で実機認識（SR06M / Android8.1 / serial 355986300612273）
- [x] `robohon-app` ビルド成功（APK生成）
- [x] 実機へインストール成功
- [x] 起動ワード（ロボコン）でアプリ起動＆発話（ログ: startTTSBuffer / onStartSpeech）
- [x] 音声認識で応答分岐（まずまず）
→ **Workstream 2 GO**。次は Phase A（設計確定）。
