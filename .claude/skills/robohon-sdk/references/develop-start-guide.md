# RoBoHoN アプリ開発スタートガイド (App Development Start Guide)

> Source: `RoBoHoN_Develop_Start_Guide.pdf` — Version **2.0.0**, Last update **2019/8/7**, © SHARP CORPORATION.
> This Markdown is a faithful, structured rendering of the official "RoBoHoN アプリ開発スタートガイド" used as a knowledge base when setting up the RoBoHoN dev environment and building apps. Japanese is primary; English clarifications are added in parentheses.

## Overview (概要)

This guide covers everything needed to *start* developing RoBoHoN (ロボホン) applications:

- Basic information about the RoBoHoN hardware/software (models, specs, parts, the voice-UI/HVML architecture).
- Setting up the PC development environment (Android Studio, JDK, SDK/Build Tools, adb driver, HVML extension + syntax-check tool).
- Configuring the RoBoHoN device for development (USB debugging, no-sleep).
- Building apps via three routes: the bundled **sample**, the **template**, and a **new project from scratch**.

Unless otherwise noted, the content targets RoBoHoN **build number 03.01.00 or later**. Before developing, update the device to the latest version via **設定 – 端末情報 – ソフトウェア更新** (Settings – Device info – Software update).

### Related reference documents (参考資料)

| No. | Title |
|-----|-------|
| [1] | `RoBoHoN_API リファレンス` (RoBoHoN_API Reference) |
| [2] | `RoBoHoN_HVML リファレンス` (RoBoHoN_HVML Reference) |
| [3] | `RoBoHoN_アプリ開発ガイドライン` (RoBoHoN App Development Guideline) |

> Note: Several detailed rules referenced from this Start Guide live in document **[3]** — e.g. the full **HVML file naming rules (4.1 HVML ファイル命名規則)** and hardware-related software cautions. Usage limits such as speech-recognition monthly call caps or utterance-length limits are **not stated in this Start Guide**; consult [1]/[3] for those.

---

## Table of Contents (目次)

- [1. はじめに (Introduction)](#1-はじめに-introduction)
  - [1.1 本資料の目的 (Purpose)](#11-本資料の目的-purpose)
  - [1.2 著作権 (Copyright)](#12-著作権-copyright)
  - [1.3 免責事項 (Disclaimer)](#13-免責事項-disclaimer)
  - [1.4 表記関係について (Notation)](#14-表記関係について-notation)
  - [1.5 用語の定義 (Glossary)](#15-用語の定義-glossary)
  - [1.6 参考資料 (References)](#16-参考資料-references)
- [2. ロボホンの基本情報 (RoBoHoN Basics)](#2-ロボホンの基本情報-robohon-basics)
  - [2.1 モデル種別 (Model types)](#21-モデル種別-model-types)
  - [2.2 スペック (Specifications)](#22-スペック-specifications)
  - [2.3 ハードウェア概要 (Hardware overview)](#23-ハードウェア概要-hardware-overview)
  - [2.4 ソフトウェア概要 (Software overview)](#24-ソフトウェア概要-software-overview)
- [3. 開発環境の準備 (Dev Environment Setup)](#3-開発環境の準備-dev-environment-setup)
  - [3.1 PC (Android Studio 環境) のセットアップ](#31-pc-android-studio-環境-のセットアップ)
    - [3.1.1 JDK のダウンロード・インストール](#311-jdk-のダウンロードインストール)
    - [3.1.2 Android Studio のインストール](#312-android-studio-のインストール)
    - [3.1.3 SDK、Build Tools の追加インストール](#313-sdkbuild-tools-の追加インストール)
    - [3.1.4 adb driver のインストール](#314-adb-driver-のインストール)
    - [3.1.5 Instant Run 設定の無効化](#315-instant-run-設定の無効化)
    - [3.1.6 HVML ファイルの拡張子を追加](#316-hvml-ファイルの拡張子を追加)
    - [3.1.7 HVML 構文チェックツールの導入](#317-hvml-構文チェックツールの導入)
  - [3.2 ロボホンの設定](#32-ロボホンの設定)
    - [3.2.1 USB デバッグの設定](#321-usb-デバッグの設定)
    - [3.2.2 スリープモードにしない設定](#322-スリープモードにしない設定)
- [4. アプリのビルド手順 (Building an App)](#4-アプリのビルド手順-building-an-app)
  - [4.1 サンプルアプリのビルド](#41-サンプルアプリのビルド)
  - [4.2 テンプレートアプリのビルド](#42-テンプレートアプリのビルド)
  - [4.3 新規プロジェクトのビルド](#43-新規プロジェクトのビルド)
  - [4.4 その他の注意点](#44-その他の注意点)
    - [4.4.1 レイアウトの編集](#441-レイアウトの編集)
    - [4.4.2 HVML ファイル新規作成時の文字コード](#442-hvml-ファイル新規作成時の文字コード)
- [5. 最後に (Closing)](#5-最後に-closing)

---

## 1. はじめに (Introduction)

### 1.1 本資料の目的 (Purpose)

This document provides the information needed to begin RoBoHoN application development. For basic device info, see ch.2. To start building immediately, jump to ch.3.

Unless otherwise stated, content targets RoBoHoN **build number 03.01.00 and later**. Update via **設定 – 端末情報 – ソフトウェア更新** before developing.

### 1.2 著作権 (Copyright)

Copyright belongs to SHARP Corporation. Reproduction or reprinting of any part without permission is prohibited.

### 1.3 免責事項 (Disclaimer)

SHARP does not guarantee correct or continuous operation of the material; contents may change without notice; SHARP bears no liability for developer damages.

### 1.4 表記関係について (Notation)

Company/product names are trademarks of their owners. ©, ®, ™ marks are omitted in the body text.

### 1.5 用語の定義 (Glossary)

| 用語 (Term) | 意味 (Meaning) |
|------------|----------------|
| ロボホン (RoBoHoN) | Collective name for ロボホン(3G・LTE), ロボホン(Wi-Fi), and ロボホンライト (RoBoHoN Light). |
| 第1世代ロボホン (1st-gen) | Refers to **SR01MW / SR02MW**. |
| 第2世代ロボホン (2nd-gen) | Refers to **SR03M / SR04M / SR05M**. |
| SDK | Software Development Kit. |
| Android | Google's mobile operating system. |
| 対話 (Dialogue) | The user utterance ⇒ RoBoHoN response exchange. Spans from the start of HVML execution until all HVML `topic` execution finishes. |
| モーション (Motion) | RoBoHoN's gestures, dances, and other physical movements. |
| (対話)シナリオ (Scenario) | An HVML file describing a dialogue flow. |
| HVML | Abbreviation of **Hyper Voice Markup Language**. |
| TTS | Text-to-Speech. Reads text aloud via speech synthesis. |
| IME | Input Method Editor. Software assisting character input. |

### 1.6 参考資料 (References)

See [Related reference documents](#related-reference-documents-参考資料) above — [1] API Reference, [2] HVML Reference, [3] App Development Guideline.

---

## 2. ロボホンの基本情報 (RoBoHoN Basics)

RoBoHoN provides functions mainly through dialogue: voice input, voice output, and gestures (motion). Each app's dialogue is implemented by the developer. Confirm all relevant terms/agreements before use.

### 2.1 モデル種別 (Model types)

RoBoHoN comes in three types:

- **3G・LTE model** — `SR01MW` / `SR03M`
- **Wi-Fi model** (no phone function) — `SR02MW` / `SR04M`
- **RoBoHoN Light** (no bipedal walking) — `SR05M`

`SR01MW/SR02MW` = **1st generation**; `SR03M/SR04M/SR05M` = **2nd generation**.

### 2.2 スペック (Specifications)

**表2-1 スペック一覧 (Spec list)**

| 項目 (Item) | SR01MW / SR02MW (1st gen) | SR03M / SR04M / SR05M (2nd gen) |
|------------|---------------------------|---------------------------------|
| **OS** | Android **5.0.2** | Android **8.1** |
| **CPU** | Qualcomm Snapdragon 400 | Qualcomm Snapdragon 430 |
| メモリ (Memory) | ROM 16GB / RAM 2GB | ROM 16GB / RAM 2GB |
| ディスプレイ (Display) | ~2.0 inch QVGA (240×320 px) | ~2.6 inch QVGA (240×320 px) |
| プロジェクター (Projector) | HD (1,280×720 px) equivalent | ― (none) |
| カメラ (Camera) | ~8 MP CMOS | ~8 MP CMOS |
| Wi-Fi | IEEE802.11b/g/n (2.4GHz only) | IEEE802.11a/b/g/n (2.4G/5GHz)/ac |
| Bluetooth | 4.0 BLE (Peripheral not supported) | 4.2 |
| GPS | 対応 (supported) | 対応 (supported) |
| センサー (Sensors) | 9-axis (accel 3 / geomag 3 / gyro 3), illuminance sensor | (same) |
| サーボモーター (Servos) | 13 | 3G・LTE & Wi-Fi: **13**; RoBoHoN Light: **7** |
| NFC/Felica | 非対応 (not supported) | 非対応 |
| ワンセグ/フルセグ (1seg/Full-seg) | 非対応 | 非対応 |
| SDカード (SD card) | 非対応 | 非対応 |

> **Key dev implications:** the only supported runtime is on-device (no emulator for behavior); min OS is Android 5.0.2 (API 21) on 1st gen, Android 8.1 on 2nd gen. The display is small QVGA 240×320.

### 2.3 ハードウェア概要 (Hardware overview)

Software-related cautions about the hardware are in reference [3], "3. ハードウェア関連". Part numbers [1]–[15] map to the external diagrams (図2-1) for SR01M/SR02M and SR03M/SR04M/SR05M.

**表2.2 ロボホンの各部の説明 (Parts description)**

| # | 名称 (Name) | 機能概略 (Function) |
|---|-------------|---------------------|
| [1] | 頭ボタン (Head button) | App-exit / utterance-interrupt button. |
| [2] | 電源ボタン (Power button) | Display off/on; long-press to power ON/OFF. |
| [3] | マナースイッチ (Manner switch) | Toggles manner (silent) mode. |
| [4] | イヤホンマイク端子 (Earphone-mic jack) | 3.5Φ. **Not on SR03M/04M/05M.** |
| [5] | microUSB 端子 (microUSB port) | microUSB cable / charger. **On SR03M/04M/05M it is on the side.** |
| [6] | ディスプレイ (Display) | Display + touch operation. |
| [7] | プロジェクター (Projector) | **Not on SR03M/04M/05M.** |
| [8] | カメラ (Camera) | - |
| [9] | LED (目 / eyes) | 3-color LED; lights/blinks during motion and notifications. |
| [10] | LED (口 / mouth) | Single-color LED; blinks with speech. **App control not possible.** |
| [10] | レシーバー (Receiver) | Hears the other party during a call. |
| [10] | 照度センサー (Illuminance sensor) | - |
| [11] | マイク (音声認識・動画撮影) | Mic for speech recognition and video recording. |
| [12] | スピーカー (Speaker) | - |
| [13] | マイク (通話) | Call mic. |
| [14] | nanoSIM カードスロット | nanoSIM card. **3G・LTE model only.** |
| [15] | 充電台 (Charging cradle) | **Sold separately for SR03M/04M/05M.** |

### 2.4 ソフトウェア概要 (Software overview)

RoBoHoN provides cloud-based features: speech recognition, messaging, content (news, etc.), app download/update (app management), backup/restore. **Using cloud services requires a contract** — either the **ココロプラン (Cocoro Plan)** or a corporate **ビジネスプラン (Business Plan)**. Check plan contracts on the RoBoHoN portal site.

Architecture:

- RoBoHoN is built on **Android** plus SHARP's proprietary framework that realizes dialogue (the **音声UI / voice UI**).
- Each app holds **HVML-format files (対話シナリオ / dialogue scenarios)** describing the dialogue flow, and **registers them with the voice UI at install time**. This enables user voice input, RoBoHoN speech, and motions.
- *(図2.2 — image of Android app vs. RoBoHoN app)*

Dialogue flow (図2.3):

1. The voice UI continuously listens for external voice input.
2. On detecting/recognizing user speech (**Speech to Text**), it uses the recognized text to select the **most appropriate registered scenario**.
3. It then executes the speech/motion described in the selected scenario.

Beyond the voice UI, several RoBoHoN-specific extension APIs let you build varied apps. For details on APIs, HVML spec, and implementation, see the reference docs.

Memory information used for RoBoHoN speech and installed-app info are part of RoBoHoN's **backup target data**. When backup is **ON**, apps are restored even after a data reset (e.g. repair) without the app needing to do anything special. See the instruction manual for backup details.

---

## 3. 開発環境の準備 (Dev Environment Setup)

### Prerequisites (必要なもの)

- **ロボホン本体 (RoBoHoN device):** required for testing. **There is NO emulator for behavior verification.**
- **ネットワーク環境 (Network):** nanoSIM card or wireless LAN + a communication line. Provider/communication costs are the developer's responsibility.
- **クラウドサービスの契約 (Cloud service contract):** Cocoro Plan or corporate Business Plan required (check the portal).
- **PC:** System requirements follow Android Studio's system requirements — <https://developer.android.com/studio/>.

This chapter covers PC (Android Studio environment) and RoBoHoN setup.

### 3.1 PC (Android Studio 環境) のセットアップ

RoBoHoN apps are developed with **Android Studio**. Configure the environment in this order:

1. JDK download/install — **必須 (required)**
2. Android Studio install — **必須 (required)**
3. SDK & Build Tools additional install — **必須 (required)**
4. adb driver install — 必要に応じて (as needed)
5. Disable Instant Run — 必要に応じて (as needed)
6. Add the HVML file extension — 推奨 (recommended)
7. Introduce the HVML syntax-check tool — 推奨 (recommended)

#### 3.1.1 JDK のダウンロード・インストール

JDK = Java Development Kit. Download and install from:

```
http://www.oracle.com/technetwork/java/javase/downloads/index.html
```

Confirmed working with **JDK 7 / 8**.

#### 3.1.2 Android Studio のインストール

Download and install from:

```
https://developer.android.com/studio/
```

- Use **Android Studio version 1.5 or later** for RoBoHoN app development.
- The guide's procedures are written against **Android Studio 3.3.2** (the latest at time of writing).

#### 3.1.3 SDK、Build Tools の追加インストール

Install the Android SDK and the **SDK Build-Tools** needed for building. In Android Studio go to **File → Settings**, then navigate to:

```
Appearance & Behavior > System Settings > Android SDK
```

Then:

- **SDK Platforms tab:** check **"Android 5.0 (Lollipop) — API 21"**.
- **SDK Tools tab:** select **"Android SDK Build Tools"**, check **"Show Package Details"** at the bottom (so versions appear), and check **Build-Tools version 21 or later**.

#### 3.1.4 adb driver のインストール

The adb driver step is needed on **Windows only** (not required on Mac OS X). Confirmed working on **Windows 7, Windows 8.1, Windows 10**.

1. Turn **ON** USB debugging on RoBoHoN (see 3.2.1).
2. Extract the USB driver (`usb_driver_SHARP_RoBoHoN_rx.x.zip`) on the local PC.
3. Connect RoBoHoN to the PC with a USB cable.
4. The automatic driver install will fail — open **Device Manager (デバイスマネージャー)** and find the **Android ADB Interface** entry.
   - ※ Depending on Windows version it may appear as "不明なデバイス" (unknown device), "(Android) ADB Interface" with a "!" mark, or "ADB Interface" without a "!" mark.
5. Right-click **Android ADB Interface** → **"ドライバーソフトウェアの更新"** (Update driver software).
6. Choose **"コンピュータを参照してドライバーソフトウェアを検索します"** (Browse my computer for driver software).
7. Press **参照 (Browse)**, select the folder extracted in step 2, and press **次へ (Next)**.
8. Check **"\"Sharp Corporation\"からのソフトウェアを常に信頼する"** (Always trust software from "Sharp Corporation") and press **インストール (Install)**.
9. When finished, press **閉じる (Close)**.

The adb driver install is then complete.

#### 3.1.5 Instant Run 設定の無効化

On Android Studio **2.x**, debug runs may fail to execute the intended scenario. If so, disable Instant Run:

1. Menu **[File] → [Settings…]**.
2. In Settings, double-click **[Build, Execution, Deployment]**.
3. Click **[Instant Run]**.
4. Uncheck **[Enable Instant Run to hot swap code/resource changes on deploy (default enabled)]**.
5. Click **OK**.

If the problem persists after this, **uninstall the app from RoBoHoN once** — the setting change then takes effect and the issue may resolve.

#### 3.1.6 HVML ファイルの拡張子を追加

RoBoHoN scenarios are implemented as `hvml` files. Register the extension in Android Studio's file types to enable editing and syntax checking:

1. **File → Settings** to open the Settings window.
2. **Editor → File Types**; under **Recognized File Types**, select **"XML"**.
3. Press the **+** button under **Registered Patterns**. *(図3-1)*
4. In the text box, enter **`*.hvml`** and press **OK**. Press **OK** again in the Settings window to finish. *(図3-2 Add Wildcard)*

#### 3.1.7 HVML 構文チェックツールの導入

> Referenced from elsewhere — this is the HVML syntax-check tool setup.

In addition to 3.1.6, performing the following when actually creating/editing an `hvml` file enables **HVML-specific syntax checking**. **This must be configured per HVML file.**

> ※ This does not provide a complete check — use it together with references [2] and [3] when authoring.

1. Extract **`schema.zip`** on the PC.
2. In the HVML editor, **right-click the [✓] mark at the top-right** of the edit pane and click **[Customize Highlighting Level]**, *or* click the icon at the bottom-right of the Android Studio window. *(図3-3 HVML edit screen)*
3. In **Relax-NG Schema Association**, select the **`hvml_schema_Verxxx.xsd`** included in the schema extracted in step 1. *(図3-4)*
4. Edit the HVML file. Errors can now be detected for cases such as:
   - Tags not defined in HVML.
   - Incorrect tag placement.
   - Missing required attributes on a tag.

*(図3-5 shows the HVML edit screen with an invalid format / error highlighting.)*

### 3.2 ロボホンの設定

Device-side settings needed on the RoBoHoN used for testing. Required **per connected device**; once set, they stay in effect until changed.

#### 3.2.1 USB デバッグの設定

So the PC recognizes the USB-connected RoBoHoN, change device settings as follows:

1. On the back LCD menu, tap **「設定」 (Settings)**.
2. Tap **「端末情報」 (Device info)**.
3. Tap **「ビルド番号」 (Build number)** **7 times**.
4. Return to the Settings menu and tap **「その他」 (Other)** at the bottom.
5. **「開発者向けオプション」 (Developer options)** now appears — tap it and change **OFF → ON**.
6. When asked **「開発用の設定を許可しますか」** (Allow development settings?), tap **OK** (bottom-right).
7. In the same menu, tap **「USB デバッグ」 (USB debugging)** and turn the check **ON**.
8. When asked **「USB デバッグを許可しますか」** (Allow USB debugging?), tap **OK** (bottom-right).
9. When connecting RoBoHoN to the PC via USB, if the **「USB デバッグを許可しますか？」** dialog appears, check **「このパソコンからのUSB がデバッグを常に許可する」** (Always allow USB debugging from this PC) and select **OK**. *(図3-6)*

USB debugging setup is then complete.

#### 3.2.2 スリープモードにしない設定

RoBoHoN turns off the screen and enters sleep mode after a period of inactivity. To prevent sleep during development, in **「開発者向けオプション」 (Developer options)** (see 3.2.1) check **「スリープモードにしない」 (Stay awake / don't sleep)**.

---

## 4. アプリのビルド手順 (Building an App)

Building a RoBoHoN app requires several fixed steps. Sample and template apps are provided to simplify this — pick the route that matches your goal:

- **4.1 サンプルアプリのビルド** — for those who just want to try a sample app.
- **4.2 テンプレートアプリのビルド** — for those creating a new app.
- **4.3 新規プロジェクトのビルド** — for those building completely from scratch or RoBoHoN-enabling an existing app (advanced).

### 4.1 サンプルアプリのビルド

Builds the sample bundled in the SDK (`sample.zip`). The zip contains a full project per API as sample code. Procedure below uses **SampleSimple**.

1. Extract `sample.zip` to any location on the PC.
2. Launch Android Studio and choose **"Open an existing Android Studio project"**. *(図4-1)*
3. Select **`SampleSimple`** from the extracted folder and press **OK**. *(図4-2)*
4. If a proxy-settings screen appears, configure as needed and press **OK**.
5. A Gradle update screen may appear — **ignore it**.
   - ※ If you do **Update**, Warnings/Errors may occur; fix `build.gradle` etc. appropriately. *(図4-3 Gradle Plugin Update Recommended)*
6. When the Gradle **Build** completes (**"Gradle build finished"** shown at the bottom-left), with RoBoHoN connected via USB, press **Run 'app'**. *(図4-4)*
7. In **Select Deployment Target**, if the connected RoBoHoN (e.g. **`SHARP SR01MW`**) appears under **Connected Devices**, select it and press **OK**. *(図4-5)*
   - If it does not appear, RoBoHoN may not be USB-connected, the adb driver may not be installed, or USB debugging may be OFF — re-check **3.2.1 USB デバッグの設定** and **3.1.4 adb driver のインストール**.
8. *(step 8 implicit — deployment proceeds)*
9. The app screen launches on RoBoHoN; pressing the **発話 (ACCOST) button** triggers speech → install complete. *(図4-6)*
   - If it doesn't speak, check that it is **not in manner (silent) mode**, and review **3.1.5 Instant Run 設定の無効化**.

### 4.2 テンプレートアプリのビルド

Builds a new app by changing the template app's package name.

Two templates are provided — pick whichever suits your app:

- **Template** — minimal; uses only the **音声UI (voice UI) API**. Package `com.robohon.template`.
- **TemplateFull** — for using **all APIs**.

Example below: rename **Template (`com.robohon.template`)** → **RobohonApp (`jp.co.sharp.robohon.app`)**.

1. Extract `template.zip` to any location (example uses `C:\work\`).
2. Rename the project root folder from `C:\work\template\Template` to **`RobohonApp`**.
3. Change the Java source folder structure to match the new package:
   ```
   C:\work\template\RobohonApp\app\src\main\java\com\robohon\template\
   →
   C:\work\template\RobohonApp\app\src\main\java\jp\co\sharp\robohon\app\
   ```
4. Edit the `package` name in `RobohonApp\app\src\main\AndroidManifest.xml`:
   ```xml
   <!-- AndroidManifest.xml -->
   <manifest xmlns:android="http://schemas.android.com/apk/res/android"
             package=" jp.co.sharp.robohon.app "
             android:versionCode="10000"
             android:versionName="1.0.0">
   ```
5. Edit `applicationId` in `RobohonApp\app\build.gradle`:
   ```groovy
   // build.gradle
   defaultConfig {
       applicationId " jp.co.sharp.robohon.app "
       minSdkVersion 21
       targetSdkVersion 21
   }
   ```
6. Rename the HVML files under `C:\work\template\RobohonApp\app\src\main\assets\hvml` per the **HVML naming rules**. For details see reference **[3] "4.1 HVML ファイル命名規則"**.
   ```
   com_robohon_template_home.hvml  → jp_co_sharp_robohon_app_home.hvml
   com_robohon_template_hello.hvml → jp_co_sharp_robohon_app_hello.hvml
   ```
   > **HVML naming rule (key constraint):** the file name must **start with the package name with dots (`.`) replaced by underscores (`_`)** (e.g. `jp.co.sharp.robohon.app` → `jp_co_sharp_robohon_app_*`). Use ASCII only. (Full rules in [3] §4.1.)
7. Replace package-name occurrences inside the Java/HVML files: `com.robohon.template` → `jp.co.sharp.robohon.app`. ※ Using **grep** to batch-replace is easiest.

   **Files to change (line numbers in parentheses; highlighted = where replacement is needed):**
   ```
   jp_co_sharp_robohon_app_home.hvml(4):
       <producer>jp.co.sharp.robohon.app</producer>
   jp_co_sharp_robohon_app_home.hvml(22):
       <data key="package_name" value="jp.co.sharp.robohon.app"/>
   jp_co_sharp_robohon_app_home.hvml(23):
       <data key="class_name" value="jp.co.sharp.robohon.app.MainActivity"/>
   jp_co_sharp_robohon_app_hello.hvml(4):
       <producer>jp.co.sharp.robohon.app</producer>
   jp_co_sharp_robohon_app_hello.hvml(6):
       <scene value="jp.co.sharp.robohon.app.scene_common"/>
   jp_co_sharp_robohon_app_hello.hvml(11):
       <accost priority="75" topic_id="say" word="jp.co.sharp.robohon.app.hello.say"/>
   MainActivity.java(1):
       package jp.co.sharp.robohon.app;
   MainActivity.java(13):
       import jp.co.sharp.robohon.app.voiceui.ScenarioDefinitions;
   MainActivity.java(14):
       import jp.co.sharp.robohon.app.voiceui.VoiceUIListenerImpl;
   MainActivity.java(15):
       import jp.co.sharp.robohon.app.voiceui.VoiceUIManagerUtil;
   RegisterScenarioService.java(1):
       package jp.co.sharp.robohon.app.voiceui;
   RequestScenarioReceiver.java(1):
       package jp.co.sharp.robohon.app.voiceui;
   ScenarioDefinitions.java(1):
       package jp.co.sharp.robohon.app.voiceui;
   ScenarioDefinitions.java(42):
       protected static final String PACKAGE = "jp.co.sharp.robohon.app";
   VoiceUIListenerImpl.java(1):
       package jp.co.sharp.robohon.app.voiceui;
   VoiceUIManagerUtil.java(1):
       package jp.co.sharp.robohon.app.voiceui;
   VoiceUIManagerUtil.java(12):
       import static jp.co.sharp.robohon.app.voiceui.ScenarioDefinitions.TAG_ACCOST;
   VoiceUIVariableUtil.java(1):
       package jp.co.sharp.robohon.app.voiceui;
   ```
8. Launch Android Studio and choose **"Open an existing Android Studio project"**. *(図4-7)*
9. Select **`RobohonApp`** and press **OK**. From here, follow **4.1** from step 4 onward. *(図4-8)*

> **Project structure recap (template):** Java sources under `app/src/main/java/<package path>/` (with a `voiceui` sub-package for `RegisterScenarioService`, `RequestScenarioReceiver`, `ScenarioDefinitions`, `VoiceUIListenerImpl`, `VoiceUIManagerUtil`, `VoiceUIVariableUtil`); HVML scenarios under `app/src/main/assets/hvml`. Scenarios are registered with the voice UI via the `RegisterScenarioService` / `RequestScenarioReceiver`. (Locale-specific HVML folders such as `hvml/home`, `hvml/other`, and `hvml_en_US` are described in the HVML Reference [2] / Guideline [3], not in this Start Guide.)

### 4.3 新規プロジェクトのビルド

For a new project from scratch, or adding RoBoHoN APIs to an existing project (advanced).

1. Open a project in Android Studio. This procedure uses **"Start a new Android Studio project"**. To reuse an existing package, use **"Open an existing Android Studio project"**. *(図4-9)*
2. Select **"Empty Activity"** and press **Next**. *(図4-10)*
3. Enter a suitable package name; set **Language = Java**, **Minimum API level = API level 21**. *(図4-11)*
4. Switch the Project pane label (top-left dropdown) from **"Android"** to **"Project"**. *(図4-12)*
5. Select **`<app name>\app\`**, right-click → **New → Directory**, and create a **`jar`** folder. *(図4-13)*
6. Copy the required API **jar** files into the `jar` folder. RoBoHoN jars are in **`jar.zip`** inside the SDK — pick jars per the APIs you use. (Here, only the voice UI is copied.)

   **Framework JARs (API → jar file):**
   ```
   音声UI (Voice UI)   : jp.co.sharp.android.voiceui.framework.jar
   プロジェクター(Projector): jp.co.sharp.android.rb.projector.framework.jar
   電話帳 (Address book) : jp.co.sharp.android.rb.addressbook.framework.jar
   カメラ (Camera)      : jp.co.sharp.android.rb.cameralibrary.jar
   ダンス (Dance)       : jp.co.sharp.android.rb.rbdance.framework.jar
   メッセージ (Messaging) : jp.co.sharp.android.rb.messaging.framework.jar
   アクション (Action)    : jp.co.sharp.android.rb.action.framework.jar
   歌 (Song)           : jp.co.sharp.android.rb.song.framework.jar
   ```
7. Right-click the copied jar(s) → **"Add As Library…"** → press **OK** in the dialog. *(図4-14)*
8. Select **File → Project Structure…** and open the **Dependencies** tab for **app**.
9. Select the added jar(s), change **Scope** to **"Compile only"**, and press **OK**. *(図4-15)*

   > **Why "Compile only":** the framework jars are *provided by the device at runtime*, so they must be compile-time only (not packaged), analogous to `provided`/`compileOnly`.
10. Reuse the template's voice-UI implementation. Copy the folder:
    ```
    \Template\app\src\main\java\com\robohon\template\voiceui
    → <app name>\app\src\main\java\<package path>\
    ```
    Fix any package-name errors to match your created package. **Also fix the package name defined in `ScenarioDefinitions`.** *(図4-16)*
11. Reuse the template's HVML files too. Copy:
    ```
    \Template\app\src\main\assets
    → <app name>\app\src\main\
    ```
    Then, per **4.2 steps 6 & 7**, rename the files and fix package-name references. *(図4-17)*
12. Add to `AndroidManifest.xml`: a `uses-permission`, a `uses-library`, the app-icon `intent-filter`, plus the scenario-registration `receiver` and `service`. For details on what to add, see references [1] and [3].
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.robohon.app">

        <uses-permission android:name="jp.co.sharp.android.permission.VOICEUI" />

        <application
            android:allowBackup="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/ic_launcher_round"
            android:supportsRtl="true"
            android:theme="@style/AppTheme">
            <activity android:name=".MainActivity">
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                    <category android:name="jp.co.sharp.android.rb.intent.category.LAUNCHER"/>
                </intent-filter>
            </activity>
            <service
                android:name=".voiceui.RegisterScenarioService"
                android:enabled="true"
                android:exported="false" >
            </service>
            <receiver android:name=".voiceui.RequestScenarioReceiver" >
                <intent-filter>
                    <action android:name="jp.co.sharp.android.voiceui.REQUEST_SCENARIO" />
                </intent-filter>
            </receiver>
            <uses-library
                android:name="jp.co.sharp.android.voiceui.framework"
                android:required="true" />
        </application>
    </manifest>
    ```
    Key elements:
    - **Permission:** `jp.co.sharp.android.permission.VOICEUI`
    - **Launcher category (so the app shows on RoBoHoN):** `jp.co.sharp.android.rb.intent.category.LAUNCHER` (in addition to the standard `MAIN`/`LAUNCHER`).
    - **Scenario registration:** `service` `.voiceui.RegisterScenarioService` (enabled, not exported) and `receiver` `.voiceui.RequestScenarioReceiver` listening for action `jp.co.sharp.android.voiceui.REQUEST_SCENARIO`.
    - **Framework:** `<uses-library android:name="jp.co.sharp.android.voiceui.framework" android:required="true" />`.
13. Set `targetSdkVersion` to **21** in `app\build.gradle`.
    > ※ Google Play requires API level 26+, **but RoBoHoN uses API level 21 in principle** to prioritize compatibility between old and new RoBoHoN devices.
14. Add the minimum required implementation to `MainActivity.java` (copy/paste from Template is fine):
    - Voice-UI instance creation.
    - HOME (home-button) event handling.
    - Scene enable/disable.

    Implement the rest per the reference docs. From here, follow **4.1 from step 6 onward**.

    ```java
    // MainActivity.java
    public class MainActivity extends AppCompatActivity {
        /** 音声UI 制御 (voice UI control) */
        private VoiceUIManager mVUIManager = null;
        /** ホームボタンイベント検知 (home-button event detection). */
        private HomeEventReceiver mHomeEventReceiver;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            // ホームボタンの検知登録 (register home-button detection).
            mHomeEventReceiver = new HomeEventReceiver();
            IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            registerReceiver(mHomeEventReceiver, filterHome);
        }

        @Override
        public void onResume() {
            super.onResume();
            // VoiceUIManager インスタンス生成 (create instance).
            if (mVUIManager == null) {
                mVUIManager = VoiceUIManager.getService(this);
            }
            // Scene 有効化 (enable scene).
            VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
            // アプリ起動時に発話実行 (speak on app start).
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_HELLO);
        }

        @Override
        public void onPause() {
            super.onPause();
            // バックに回ったら発話を中止する (stop speech when backgrounded).
            VoiceUIManagerUtil.stopSpeech();
            // Scene 無効化 (disable scene).
            VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            // ホームボタンの検知破棄 (unregister home-button detection).
            this.unregisterReceiver(mHomeEventReceiver);
            // インスタンスのごみ掃除 (clean up instance).
            mVUIManager = null;
        }

        /**
         * ホームボタンの押下イベントを受け取るためのBroadcast レシーバークラス.<br>
         * (Broadcast receiver for the home-button press event.)
         */
        private class HomeEventReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.v(TAG, "Receive Home button pressed");
                // ホームボタン押下でアプリ終了する (finish app on home-button press).
                finish();
            }
        }
    }
    ```

### 4.4 その他の注意点

#### 4.4.1 レイアウトの編集

When editing layouts via Android Studio's GUI, set the **virtual device to "Galaxy Nexus"** and the **API level to 21**. *(図4-18)*

#### 4.4.2 HVML ファイル新規作成時の文字コード

When creating a new HVML file in Android Studio, set the **character encoding to UTF-8**. If you copy a template/sample scenario, UTF-8 is already set, so just edit it. To create a new file with the correct encoding:

1. Right-click the folder where the HVML file goes → **New → File**, and enter the file name. *(図4-19)*
2. On the first line of the editor, write one of:
   ```xml
   <?xml version="1.0" encoding="utf-8" ?>
   ```
   or
   ```xml
   <?xml version="1.0" ?>
   ```
   *(図4-20)*

---

## 5. 最後に (Closing)

To maintain a **common UX across apps**, RoBoHoN defines rules for character/worldview, dialogue phrasing, and implementation methods — see reference **[3] RoBoHoN_アプリ開発ガイドライン (App Development Guideline)**, which also contains useful information for RoBoHoN app development.

---

## Quick-reference: stated support & limits

- **Device models:** SR01MW, SR02MW (1st gen); SR03M, SR04M, SR05M (2nd gen). Types: 3G・LTE (SR01MW/SR03M), Wi-Fi (SR02MW/SR04M), RoBoHoN Light (SR05M).
- **OS:** Android **5.0.2** (1st gen) / Android **8.1** (2nd gen). Develop against **API level 21** (`minSdkVersion 21`, `targetSdkVersion 21`).
- **Target build:** RoBoHoN **build number 03.01.00 or later**.
- **Toolchain:** Android Studio **1.5+** (guide uses 3.3.2); **JDK 7/8**; SDK Platform **Android 5.0 (API 21)**; **Build-Tools 21+**.
- **adb driver:** Windows only (Windows 7 / 8.1 / 10); not needed on Mac OS X.
- **No emulator** for behavior verification — a physical device is required.
- **Cloud contract required** (Cocoro Plan or corporate Business Plan) to use cloud features (speech recognition, messaging, content, app management, backup).
- **Usage limits** (speech-recognition monthly call caps, utterance length, etc.) are **NOT stated in this Start Guide** — see references [1] API Reference and [3] App Development Guideline.
- **HVML file naming:** must start with the package name with `.` replaced by `_`, ASCII only; full rules in [3] §4.1. New HVML files must be UTF-8.
