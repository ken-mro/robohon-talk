# RoBoHoN SDK コードマップ（テンプレート/サンプルの構造と会話フロー）

SDK同梱の `template/TemplateFull` と `sample/applications/*` を読み、「HVMLファイル ↔ Javaクラス ↔ 担う機能」と音声対話の実行フローを整理。実体は `vendor/RoBoHoN_SDK_2_0_0/` 配下。新規アプリはこの構造を踏襲する。

## SDKディレクトリ
```
vendor/RoBoHoN_SDK_2_0_0/
├── jar/                     8つのフレームワークJAR（コンパイル専用=Compile only スコープ）
│   ├── jp.co.sharp.android.voiceui.framework.jar   ★音声対話の中核
│   ├── jp.co.sharp.android.rb.action.framework.jar
│   ├── jp.co.sharp.android.rb.addressbook.framework.jar
│   ├── jp.co.sharp.android.rb.cameralibrary.jar
│   ├── jp.co.sharp.android.rb.messaging.framework.jar
│   ├── jp.co.sharp.android.rb.projector.framework.jar
│   ├── jp.co.sharp.android.rb.rbdance.framework.jar
│   └── jp.co.sharp.android.rb.song.framework.jar
├── schema/hvml_schema_Ver110.xsd     HVML構文チェック用（→ hvml-schema-notes.md）
├── template/Template                 最小テンプレ（voiceuiのみ）
├── template/TemplateFull             全API入りテンプレ（多言語hvml付き）★主要参照
├── sample/applications/              機能別サンプル10種
└── tool/  RbDevtool_V01_00_00.apk(端末用), KanaHighLow_V01_00_00.exe(抑揚調整) → devtool-manual.md
```

## アプリのファイル構成（TemplateFull）
```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── hvml/home/  com_robohon_template_home.hvml        ← ホーム起動シナリオ(必須・1個)
│   ├── hvml/other/ com_robohon_template_hello.hvml 他    ← アプリ内会話シナリオ
│   └── hvml_en_US/ hvml_ko_KR/ hvml_zh_CN/ ...           ← ロケール別（無ければ hvml/ にフォールバック）
└── java/com/robohon/template/
    ├── MainActivity.java                      会話の本体（Activity）
    └── voiceui/
        ├── ScenarioDefinitions.java           scene/accost/target/function/変数名の定数定義
        ├── RegisterScenarioService.java       assetsのhvmlを端末ローカルへコピーし登録
        ├── VoiceUIListenerImpl.java           VoiceUIListener実装（コールバック→Activityへ中継）
        ├── VoiceUIManagerUtil.java            VoiceUIManager操作のラッパ群
        └── VoiceUIVariableUtil.java           variableリスト探索ヘルパ
```
> HVMLファイル名規則: **パッケージ名のドットを `_` に置換した接頭辞**で始まること・ASCIIのみ・UTF-8。例 `com.robohon.template` → `com_robohon_template_*.hvml`。`RegisterScenarioService` がこの規則を満たさないファイルをコピー対象外にする。

## クラス別の役割

### ScenarioDefinitions（定数定義）
HVMLとJavaで共有する文字列を一元管理。要点:
- 共通タグ名: `TAG_SCENE="scene"`, `TAG_ACCOST="accost"`, `TAG_MEMORY_P="memory_p:"`, `ATTR_TARGET="target"`, `ATTR_FUNCTION="function"`
- アプリ固有: `PACKAGE`, `TARGET=PACKAGE`, `SCENE_COMMON=PACKAGE+".scene_common"`, `ACC_HELLO=PACKAGE+".hello.say"`, `FUNC_END_APP="end_app"`, `RESOLVE_SPEECH=PACKAGE+":speech"`
- **規則**: scene名・memory_pキー・resolve変数名・accostのwordは必ずパッケージ名を含める。

### RegisterScenarioService（シナリオ登録）
- `RegisterScenarioService.start(context, baseIntent, CMD_REQUEST_SCENARIO)` で起動。
- assets配下の `hvml[/_locale]/home`・`/other` から命名規則に合うhvmlをアプリのlocalフォルダへコピー。
- `VoiceUIManager.registerHomeScenario(path)`（home用）/ `registerScenario(path)`（other用）で登録。
- アプリ初回/シナリオ更新時に1回実行すれば端末に常駐登録される（ホーム起動ワードが効くようになる）。

### VoiceUIManagerUtil（VoiceUIManager操作ラッパ）
| メソッド | 内部API | 用途 |
|---|---|---|
| registerVoiceUIListener / unregisterVoiceUIListener | registerVoiceUIListener / unregister… | リスナー登録/解除 |
| enableScene(vm, scene) / disableScene | updateAppInfo（SCENE_ENABLE/DISABLE をsetExtraInfo） | sceneの有効/無効 |
| startSpeech(vm, accost) | updateAppInfoAndSpeech | **accostを指定して発話実行** |
| stopSpeech() | VoiceUIManager.stopSpeech | 発話中止 |
| setMemory(vm,key,value) / clearMemory | updateAppInfo（`memory_p:`+key）/ removeVariable | 長期記憶の設定/削除 |
| setAsr(vm,locale) / setTts(vm,locale) | setAsrLanguage / setTtsLanguage | 認識/発話の言語切替（LANG_JAPANESE等） |

### VoiceUIListenerImpl（コールバック中継）
`VoiceUIListener` を実装し、`ScenarioCallback`（Activityが実装）へ橋渡し。イベント種別定数:
| 定数 | 元コールバック | 意味 |
|---|---|---|
| ACTION_START(0) | onVoiceUIEvent | controlタグ付きactionの**開始時**（発話と同時に処理したい時） |
| ACTION_END(1) | onVoiceUIActionEnd | controlタグ付きactionの**完了時**（発話後に処理したい時） |
| RESOLVE_VARIABLE(2) | onVoiceUIResolveVariable | **アプリ変数解決**（`${pkg:var}` の値をアプリが埋める）★動的発話の要 |
| ACTION_CANCELLED(3) | onVoiceUIActionCancelled | 高priorityシナリオに割込まれた |
| ACTION_REJECTED(4) | onVoiceUIRejection | priority負け等で発話棄却 |
- 注意: コールバックは**時間のかかる処理を避けて即座に抜ける**こと（重い処理は別スレッド/サービスへ）。

### MainActivity（会話本体）ライフサイクル
- `onResume`:
  1. `VoiceUIManager.getService(this)` でインスタンス取得
  2. `registerVoiceUIListener` でリスナー登録
  3. `enableScene(SCENE_COMMON)` でscene有効化
  4. `startSpeech(ACC_HELLO)` で起動時発話
- `onPause`: `stopSpeech` → `disableScene` → `unregisterVoiceUIListener` → 言語を既定に戻す → `finish()`（単一Activityは終了）
- `onCreate`: `ACTION_CLOSE_SYSTEM_DIALOGS` を受けるHomeEventReceiverを登録 → **ホームボタンで必ず finish()**（ガイドライン必須挙動）
- `onScenarioEvent(event, variables)`:
  - `ACTION_END` で `function` を見て分岐（`end_app`→finish 等）
  - `RESOLVE_VARIABLE` で `variable.getName()` が `RESOLVE_SPEECH` なら `variable.setStringValue(...)` に**動的テキストを設定** ← ここがLLM応答の差し込み口

## 音声対話の実行フロー（全体像）

```
[初回] RegisterScenarioService → registerHomeScenario/registerScenario で端末にHVML登録
                                   ↓
[起動] ユーザ「(ロボホン)、てんぷれーと起動して」
   → home.hvml の <situation>${Local:WORD_APPLICATION} eq てんぷれーと</situation> 発火
   → topic"start" の <control function="start_activity" data(package,class)> でActivity起動
                                   ↓
[会話] MainActivity.onResume → enableScene → startSpeech(accost)
   → other.hvml の topic が発話。<speech>${pkg:speech}</speech> があれば
        onVoiceUIResolveVariable コールバック → アプリが setStringValue(応答文) で解決
   → topic listen="true" ならユーザ発話を待受 → <situation>${Lvcsr:Basic} include[...]> で次topicへ
                                   ↓
[終了] 「アプリ終了して」→ control function="end_app" → onVoiceUIActionEnd → finish()
        または ホームボタン → HomeEventReceiver → finish()
```

## LLM会話アプリへの応用ポイント（本プロジェクト）
1. **起動**: home.hvml に起動ワード（`WORD_APPLICATION`/`WORD_APPLICATION_FREEWORD`）を1組定義し、`start_activity` で会話Activityを起動。
2. **ユーザ発話の取得**: 会話topicを `listen="true"` にし、`${Lvcsr:Basic}`（かな漢字）/`${Lvcsr:Kana}` の認識結果を取得。自由発話は `recog_type="#robohonprocess"` や Lvcsr を活用。DevTool Web UI（`http://<device-ip>:48000`）で疑似認識を注入してテスト可能。
3. **LLM応答の発話**: 認識テキストを中継サーバ経由でClaudeへ → 返答を `onVoiceUIResolveVariable` で `${pkg:speech}` に `setStringValue` して発話。長文は複数 `action`/topic に分割（公式仕様に文字数上限は無いが、自然さ・先行事例から ~150字単位の分割が無難 / 自動モーション`assign`は読み<30文字に付与）。
4. **アプリ連携**: Claudeの tool use 結果を `control`/Android Intent にマップ（他アプリ起動・カメラ・歌・ダンス等は各Utilクラス → api-reference.md）。
5. **必須挙動**: ホームボタンで finish、onPauseで発話停止・scene無効化（ガイドライン）。
