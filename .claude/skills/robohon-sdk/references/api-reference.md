# RoBoHoN SDK API Reference

Build version: **3.1.0** (Doclava-generated Javadoc, &copy; 2018 SHARP CORPORATION).

This reference covers the public RoBoHoN SDK surface used to build voice-conversation apps and to trigger built-in robot features (action, camera, song, dance, messaging, projector, address book) via Intents/Broadcasts.

Common conventions:
- The `voiceui` package is the **core API for voice conversation** (TTS speech, ASR recognition, scenario/HVML control). Use it through a `VoiceUIManager` instance obtained from `VoiceUIManager.getService(Context)`.
- The `jp.co.sharp.android.rb.*` utility classes are mostly **constant holders**. They define `ACTION_*` (Broadcast Intent actions), `EXTRA_*` (Intent extra keys), `RESULT_*` (result codes), and `PACKAGE`/`PACKAGE_NAME`/`CLASS_NAME` (target component). The app launches a feature by building an Intent with the action and extras and calling `sendBroadcast` (or `startService` for the projector).
- Result-code convention across the `rb.*` utility classes: `RESULT_OK = -1` (0xffffffff), `RESULT_CANCELED = 0`.

## Packages

| Package | Purpose |
|---|---|
| `jp.co.sharp.android.voiceui` | Voice UI: speech (TTS), recognition (ASR), HVML scenario control, app/robot data exchange. **Core conversation API.** |
| `jp.co.sharp.android.rb.action` | Launch built-in robot actions (motions). |
| `jp.co.sharp.android.rb.addressbook` | Address book / owner profile / RoBoHoN profile access. |
| `jp.co.sharp.android.rb.addressbook.AddressBookVariable` | Data holder classes for address-book/profile records. |
| `jp.co.sharp.android.rb.camera` | Camera: still/video shooting and face/pet detection. |
| `jp.co.sharp.android.rb.messaging` | Launch the message (mail) send feature. |
| `jp.co.sharp.android.rb.projectormanager` | Control the built-in projector. |
| `jp.co.sharp.android.rb.rbdance` | Launch dance feature. |
| `jp.co.sharp.android.rb.song` | Launch song feature. |

---

# Package: jp.co.sharp.android.voiceui (core voice conversation)

This is the primary API for building conversational RoBoHoN apps. Typical lifecycle:

1. Register a `BroadcastReceiver` for `VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED`; when received, (re)acquire the manager and (re)register listeners.
2. On `ACTION_VOICEUI_REQUEST_SCENARIO`, call `registerScenario` / `registerHomeScenario` to install your HVML scenario files.
3. `getService(Context)` to obtain the `VoiceUIManager` instance.
4. `registerVoiceUIListener` / `registerVoiceUIStateListener` to receive callbacks.
5. Speak/trigger with `updateAppInfoAndSpeech` (accost), pass data with `updateAppInfo`, stop with `stopSpeech`.

## Class: VoiceUIManager

`public class VoiceUIManager extends Object`

音声UIの開始/停止/シナリオ変更/記憶の取得などを行うメソッドを提供するクラス. — Provides methods to start/stop the voice UI, change scenarios, and store/retrieve memory variables. Obtain an instance via `getService(Context)` before calling instance methods.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `ACTION_VOICEUI_REQUEST_SCENARIO` | `"jp.co.sharp.android.voiceui.REQUEST_SCENARIO"` | Broadcast action sent at boot and on package add/update. Trigger to (re)register your scenarios. 端末起動時、パッケージ追加/更新時に通知。 |
| String | `ACTION_VOICEUI_SERVICE_STARTED` | `"jp.co.sharp.android.voiceui.VOICEUI_SERVICE_STARTED"` | Broadcast action signalling the voice UI service started (first start or restart after abnormal end). On receipt, re-acquire instance (`getService`) and re-register listeners. |
| String | `LANG_CHINESE` | `"chinese"` | Set recognition/speech language to Chinese. |
| String | `LANG_ENGLISH` | `"english"` | Set recognition/speech language to English. |
| String | `LANG_JAPANESE` | `"japanese"` | Set recognition/speech language to Japanese. |
| String | `LANG_KOREAN` | `"korean"` | Set recognition/speech language to Korean. |
| String | `SCENE_DISABLE` | `"0"` | Value to disable/unregister a scene (used with `VoiceUIVariable.setExtraInfo`). |
| String | `SCENE_ENABLE` | `"1"` | Value to enable/register a scene. |
| int | `STATE_ACTIVE` | `2` | Operating state: voice recognition available. |
| int | `STATE_ACTIVE_NOLISTEN` | `3` | Operating state: recognition stopped. |
| int | `STATE_STOP` | `1` | Stopped state. |
| int | `VOICEUI_SUCCESS` | `0` | Processing succeeded (return value). |
| int | `VOICEUI_ERROR` | `1` | Processing failed. |
| int | `VOICEUI_ERROR_MANNER` | `2` | Processing failed because manner (silent) mode is on. |

### Methods

Most instance methods return `int` (`VOICEUI_SUCCESS` / `VOICEUI_ERROR`, and may declare `throws RemoteException`).

| Return | Signature | Description |
|---|---|---|
| static `VoiceUIManager` | `getService(Context ctx)` | Obtain the voice UI service instance. **Call this first**; use the returned instance for the other methods. Returns the `VoiceUIManager` instance. |
| int | `registerVoiceUIListener(VoiceUIListener listener)` | Register a listener to receive callback notifications (recognition results, action end/cancel, etc.). |
| int | `unregisterVoiceUIListener(VoiceUIListener listener)` | Unregister a previously registered `VoiceUIListener`. |
| int | `registerVoiceUIStateListener(VoiceUIStateListener listener)` | Register a listener to receive voice-UI state-change notifications. |
| int | `unregisterVoiceUIStateListener(VoiceUIStateListener listener)` | Unregister a `VoiceUIStateListener`. |
| int | `updateAppInfo(List<VoiceUIVariable> variable)` | Notify the voice UI of app info (variables needed for scenario selection). **Does not speak.** Used to set scenes and long-term memory (`memory_p:XXX`). |
| int | `updateAppInfoAndSpeech(List<VoiceUIVariable> variable)` | Notify app info **and speak** (accost / app-initiated trigger). The UI stays in speaking state until `onVoiceUIActionEnd`. Differs from `updateAppInfo` by allowing accost. |
| static int | `stopSpeech()` | Stop ongoing speech / servo motion. Cancels the running scenario (your own app's scenario only, matched by producer/package). During an action, triggers `onVoiceUIActionCancelled`. Returns success even if not currently speaking. |
| int | `removeVariable(String name)` | Delete a long-term memory variable (`memory_p`) previously set via `updateAppInfo`/`updateAppInfoAndSpeech`. Pass the name **without** the `memory_p:` prefix. |
| int | `setAsrLanguage(String language)` | Set the speech-**recognition** (ASR) language. Pass `LANG_JAPANESE`/`LANG_ENGLISH`/`LANG_CHINESE`/`LANG_KOREAN`. Use only while your app is foreground; reset on exit (home transition auto-resets to device language). |
| int | `setTtsLanguage(String language)` | Set the **speech** (TTS) language. Same language constants and foreground-only caveat as `setAsrLanguage`. |
| int | `registerScenario(String filePath)` | Register an HVML scenario file. Call in response to `ACTION_VOICEUI_REQUEST_SCENARIO`. Files unchanged by `<version>` are not re-installed. Store scenarios under `/data/data/<package>/`; prefix filename with package name (dots → underscores), e.g. `jp_co_sharp_android_sample.hvml`. |
| int | `unregisterScenario(String filePath)` | Unregister a scenario by file path. |
| int | `registerHomeScenario(String filePath)` | Register a **home** scenario file (scenarios whose `scene` tag is `home`, reacting on the home screen). Same spec as `registerScenario`. |
| int | `unregisterHomeScenario(String filePath)` | Unregister a home scenario by file path. |
| int | `notifyEnableMic()` | Turn the mic **on** (re-enable recognition). Use to recover from `notifyDisableMic`. Foreground use only. |
| int | `notifyDisableMic()` | Turn the mic **off** (disable recognition so the app can control mic). Always re-enable on exit; home transition auto-restores mic on. |
| int | `wakeAndDimIfNeeded()` | Notify PowerManager to extend the dim/backlight period so the device does not sleep (default backlight 1 min). |

## Interface: VoiceUIListener

`public interface VoiceUIListener`

音声認識結果などをアプリに通知するコールバックインターフェース. — Callback interface delivering recognition results and scenario events to the app. **Callbacks must return quickly** (avoid time-consuming work inside them).

| Return | Callback | When called |
|---|---|---|
| void | `onVoiceUIEvent(List<VoiceUIVariable> variable)` | A scenario was selected and contains a `<control>` tag. `variable` holds the control tag contents (e.g. `target`, `function`, and `<data>` key/value pairs — read via `getName()`/`getStringValue()`). Not called if the scenario has no control tag. |
| void | `onVoiceUIActionEnd(List<VoiceUIVariable> variable)` | An action (speech / servo motion) completed. Not called if the action was already cancelled, or if the scenario has no control tag. `variable` equals what `onVoiceUIEvent` delivered. |
| void | `onVoiceUIActionCancelled(List<VoiceUIVariable> variable)` | An action was cancelled — e.g. interrupted by a higher-priority scenario, skipped in manner mode after `updateAppInfoAndSpeech`, cancelled by the head button, or via `stopSpeech()`. Use to retry accost or clean up. `variable` may be size 0 (no control tag, manner-key cancel, or `stopSpeech` while listening). |
| void | `onVoiceUIRejection(VoiceUIVariable variable)` | An accost speech requested via `updateAppInfoAndSpeech` was **rejected** (e.g. lower priority than a current utterance). `variable` is the accost variable (single, **not** a List). Delivered to all registered listeners regardless of control tag. |
| void | `onVoiceUIResolveVariable(List<VoiceUIVariable> variable)` | A scenario references an added variable that needs resolving (e.g. `${pkg:resolve}`). The owning package is asked to supply the value. See HVML2.0 spec. |
| void | `onVoiceUISchedule(int scheduleID)` | Schedule-registration result. Override is required but no implementation is needed. |

## Interface: VoiceUIStateListener

`public interface VoiceUIStateListener`

音声UIの状態をアプリに通知するコールバックインターフェース. — Callback interface for voice-UI state changes. Callbacks must return quickly.

| Return | Callback | Description |
|---|---|---|
| void | `onVoiceUIStateChanged(int state)` | Voice-UI state changed. `state` is one of `VoiceUIManager.STATE_ACTIVE`, `STATE_ACTIVE_NOLISTEN`, or `STATE_STOP`. |

## Class: VoiceUIVariable

`public class VoiceUIVariable extends Object implements Parcelable`

アプリと音声UI間のデータ受け渡しのメソッドを提供するクラス. — Carries named typed values between the app and the voice UI (used in `updateAppInfo`/`updateAppInfoAndSpeech` arguments and in listener callbacks).

Nested type: `VoiceUIVariable.VariableType` (enum).

### Constructors

| Signature | Description |
|---|---|
| `VoiceUIVariable(String name, VoiceUIVariable.VariableType type)` | Create with a name and a type (no value yet). `name` must not be null. |
| `VoiceUIVariable(String name, String stringValue)` | Create with a name and a String value. |
| `VoiceUIVariable(String name, float floatValue)` | Create with a name and a float value. |
| `VoiceUIVariable(String name, boolean booleanValue)` | Create with a name and a boolean value. |

### Methods

| Return | Signature | Description |
|---|---|---|
| String | `getName()` | Get the variable name. |
| void | `setName(String name)` | Set/replace the variable name. |
| `VoiceUIVariable.VariableType` | `getVariableType()` | Get the variable type. |
| void | `setVariableType(VoiceUIVariable.VariableType type)` | Set/replace the variable type. |
| String | `getStringValue()` | Get the String value. |
| void | `setStringValue(String value)` | Set the String value. |
| float | `getFloatValue()` | Get the float value. |
| void | `setFloatValue(float value)` | Set the float value. |
| boolean | `getBooleanValue()` | Get the boolean value. |
| void | `setBooleanValue(boolean value)` | Set the boolean value. In scenarios, `true`→`1`, `false`→`0` (compare with `1`/`0`, not `true`/`false`). |
| String | `getExtraInfo()` | Get current scene state: returns `VoiceUIManager.SCENE_ENABLE` (registered) or `SCENE_DISABLE` (released). |
| void | `setExtraInfo(String value)` | Register/release a scene. Pass `VoiceUIManager.SCENE_ENABLE` or `SCENE_DISABLE`. |

Also implements `Parcelable` (`describeContents()`, `writeToParcel(Parcel, int)`).

### Enum: VoiceUIVariable.VariableType

`public static final enum VoiceUIVariable.VariableType`

VariableTypeの定義値 — variable type definitions.

| Value | Meaning |
|---|---|
| `STRING` | 文字列 — string |
| `FLOAT` | 数値 — numeric (float) |
| `BOOLEAN` | boolean |

Standard enum methods: `static VariableType[] values()`, `static VariableType valueOf(String name)`.

### Example: speak (accost)

```java
VoiceUIManager mgr = VoiceUIManager.getService(context);
ArrayList<VoiceUIVariable> list = new ArrayList<>();
VoiceUIVariable v = new VoiceUIVariable("accost", VariableType.STRING);
v.setStringValue("jp.co.sharp.sample.sample_accost"); // matches <accost word=.../> in scenario
list.add(v);
int ret = mgr.updateAppInfoAndSpeech(list);
```

### Example: set a scene / long-term memory

```java
// scene enable
VoiceUIVariable s = new VoiceUIVariable("scene", VariableType.STRING);
s.setStringValue("jp.co.sharp.sample");
s.setExtraInfo(VoiceUIManager.SCENE_ENABLE);

// long-term memory variable
VoiceUIVariable m = new VoiceUIVariable("memory_p:jp.co.sharp.sample.test", VariableType.STRING);
m.setStringValue("java");

mgr.updateAppInfo(Arrays.asList(s, m));
```

---

# Package: jp.co.sharp.android.rb.action

## Class: ActionUtil

`public class ActionUtil extends Object`

アクションアプリの起動要求利用時に必要な定義値を提供するクラス. — Constants for requesting built-in robot actions (motions) via Broadcast.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `ACTION_REQUEST_ACTION` | `"jp.co.sharp.android.rb.action.action.REQUEST_ACTION"` | Action-request broadcast action (use with `sendBroadcast`). |
| String | `EXTRA_REQUEST_ID` | `"jp.co.sharp.android.rb.action.extra.REQUEST_ID"` | Requested action ID. Nonexistent or unspecified ID → not executed. |
| String | `EXTRA_REPLYTO_ACTION` | `"jp.co.sharp.android.rb.action.extra.REPLYTO_ACTION"` | Action for the result-notification Intent. If unspecified, no result notification. |
| String | `EXTRA_REPLYTO_PKG` | `"jp.co.sharp.android.rb.action.extra.REPLYTO_PKG"` | Target package for result notification. If unspecified, broadcasts to all. |
| String | `EXTRA_RESULT_CODE` | `"jp.co.sharp.android.rb.action.extra.RESULT_CODE"` | Result code key. `RESULT_OK`=completed, `RESULT_CANCELED`=interrupted. |
| String | `EXTRA_RESULT_ID` | `"jp.co.sharp.android.rb.action.extra.RESULT_ID"` | Executed action ID. |
| String | `EXTRA_RESULT_NAME` | `"jp.co.sharp.android.rb.action.extra.RESULT_NAME"` | Executed action name. |
| String | `PACKAGE` | `"jp.co.sharp.android.rb.action"` | Target package name. |
| int | `RESULT_OK` | `-1` | Action completed. |
| int | `RESULT_CANCELED` | `0` | Action cancelled. |

### Methods

| Return | Signature | Description |
|---|---|---|
| static `LinkedHashMap<Integer, String>` | `getInfo(Context context)` | Get the list of available actions as (action ID → action name). |

---

# Package: jp.co.sharp.android.rb.camera

## Class: ShootMediaUtil

`public class ShootMediaUtil extends Object`

カメラアプリの撮影機能利用時に必要な定義値を提供するクラス. — Constants for triggering camera shooting (still image / video) via Broadcast.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `ACTION_SHOOT_IMAGE` | `"jp.co.sharp.android.rb.camera.action.SHOOT_IMAGE"` | Take a still image. |
| String | `ACTION_SHOOT_MOVIE` | `"jp.co.sharp.android.rb.camera.action.SHOOT_MOVIE"` | Record video. |
| String | `ACTION_SHOOT_CANCEL` | `"jp.co.sharp.android.rb.camera.action.SHOOT_CANCEL"` | Cancel shooting. |
| String | `EXTRA_FACE_DETECTION` | `"jp.co.sharp.android.rb.camera.extra.FACE_DETECTION"` | (still) `true`=recognize face then shoot; `false`=shoot immediately. Default `false`. |
| String | `EXTRA_CONTACTID` | `"jp.co.sharp.android.rb.camera.extra.CONTACTID"` | Target contact ID (1–201) for face recognition. Only valid with `EXTRA_FACE_DETECTION=true`. Unspecified/invalid → all detected faces. |
| int | `EXTRA_CONTACTID_OWNER` | `201` | Recognize/shoot the owner. |
| String | `EXTRA_MOVIE_LENGTH` | `"jp.co.sharp.android.rb.camera.extra.MOVIE_LENGTH"` | Video duration in seconds (positive int). Invalid/unspecified → no auto-stop. |
| String | `EXTRA_PHOTO_TAKEN_PATH` | `"jp.co.sharp.android.rb.camera.extra.PHOTO_TAKEN_PATH"` | (result) Saved still-image path on `RESULT_OK`, e.g. `/storage/emulated/0/DCIM/100SHARP/robohon_yyyyMMdd_HHmmss.jpg`. |
| String | `EXTRA_VIDEO_TAKEN_PATH` | `"jp.co.sharp.android.rb.camera.extra.VIDEO_TAKEN_PATH"` | (result) Saved video path on `RESULT_OK`, e.g. `...robohon_yyyyMMdd_HHmmss.mp4`. |
| String | `EXTRA_REPLYTO_ACTION` | `"jp.co.sharp.android.rb.camera.extra.REPLYTO_ACTION"` | Result-notification Intent action. Unspecified → no notification. |
| String | `EXTRA_REPLYTO_PKG` | `"jp.co.sharp.android.rb.camera.extra.REPLYTO_PKG"` | Result-notification target package. Unspecified → broadcast to all. |
| String | `EXTRA_RESULT_CODE` | `"jp.co.sharp.android.rb.camera.extra.RESULT_CODE"` | Result code key (`RESULT_OK`/`RESULT_CANCELED`). |
| String | `PACKAGE` | `"jp.co.sharp.android.rb.camera"` | Target package name. |
| int | `RESULT_OK` | `-1` | Shooting completed. |
| int | `RESULT_CANCELED` | `0` | Shooting cancelled. |

No public methods (constants only).

## Class: FaceDetectionUtil

`public class FaceDetectionUtil extends Object`

カメラアプリの顔検出機能利用時に必要な定義値を提供するクラス. — Constants for face/pet detection via Broadcast.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `ACTION_FACE_DETECTION_MODE` | `"jp.co.sharp.android.rb.camera.action.FACE_DETECTION_MODE"` | Start face detection. |
| String | `ACTION_FACE_DETECTION_MODE_CANCEL` | `"jp.co.sharp.android.rb.camera.action.FACE_DETECTION_MODE_CANCEL"` | Cancel face detection. |
| String | `EXTRA_DETECTION_TARGET` | `"jp.co.sharp.android.rb.camera.extra.DETECTION_TARGET"` | Detection target(s); combine the flag values below. Unspecified → all targets. |
| int | `EXTRA_DETECTION_TARGET_ADDRESSBOOK` | `1` | Detect contacts with registered face photos. |
| int | `EXTRA_DETECTION_TARGET_OWNER` | `2` | Detect the owner. |
| int | `EXTRA_DETECTION_TARGET_PET` | `4` | Detect pets (cats, dogs). |
| String | `EXTRA_FACE_DETECTION_LENGTH` | `"jp.co.sharp.android.rb.camera.extra.FACE_DETECTION_LENGTH"` | Detection duration. Unspecified → normal. |
| String | `EXTRA_FACE_DETECTION_LENGTH_NORMAL` | `"normal"` | Detect for 5 seconds. |
| String | `EXTRA_FACE_DETECTION_LENGTH_LONG` | `"long"` | Detect for 10 seconds. |
| String | `EXTRA_MOVE_HEAD` | `"jp.co.sharp.android.rb.camera.extra.MOVE_HEAD"` | `"TRUE"`=scan around (move head) while detecting; `"FALSE"`=no scanning. Default `FALSE`. |
| String | `EXTRA_MAP_FACE_DETECTION` | `"jp.co.sharp.android.rb.camera.extra.MAP_FACE_DETECTION"` | (result) `HashMap<String,String>` of detected face contact IDs (-1 unregistered, 1–200 contacts, 201 owner); order is undefined. |
| String | `EXTRA_PET_DETECTION` | `"jp.co.sharp.android.rb.camera.extra.PET_DETECTION"` | (result) Pet detection result (int): `CAT_DETECTION`/`DOG_DETECTION`/`BOTH_DETECTION`. |
| int | `CAT_DETECTION` | `0` | Cat detected. |
| int | `DOG_DETECTION` | `1` | Dog detected. |
| int | `BOTH_DETECTION` | `2` | Both cat and dog detected. |
| String | `EXTRA_REPLYTO_ACTION` | `"jp.co.sharp.android.rb.camera.extra.REPLYTO_ACTION"` | Result-notification Intent action. Unspecified → no notification. |
| String | `EXTRA_REPLYTO_PKG` | `"jp.co.sharp.android.rb.camera.extra.REPLYTO_PKG"` | Result-notification target package. Unspecified → no notification. |
| String | `EXTRA_RESULT_CODE` | `"jp.co.sharp.android.rb.camera.extra.RESULT_CODE"` | Result code key (`RESULT_OK`/`RESULT_CANCELED`). |
| String | `PACKAGE` | `"jp.co.sharp.android.rb.camera"` | Target package name. |
| int | `RESULT_OK` | `-1` | Detection completed. |
| int | `RESULT_CANCELED` | `0` | Detection cancelled. |

No public methods (constants only).

---

# Package: jp.co.sharp.android.rb.song

## Class: SongUtil

`public class SongUtil extends Object`

歌アプリの起動要求利用時に必要な定義値を提供するクラス. — Constants for requesting the song feature via Broadcast.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `ACTION_REQUEST_SONG` | `"jp.co.sharp.android.rb.song.action.REQUEST_SONG"` | Song-request broadcast action. |
| String | `EXTRA_TYPE` | `"jp.co.sharp.android.rb.song.extra.TYPE"` | Song mode (values below). Unspecified/other → normal. |
| String | `EXTRA_TYPE_NORMAL` | `"normal"` | Random song (excludes Happy Birthday). |
| String | `EXTRA_TYPE_REPEAT` | `"repeat"` | Repeat. |
| String | `EXTRA_TYPE_ASSIGN` | `"assign"` | Play the specified song (`EXTRA_REQUEST_ID`). |
| String | `EXTRA_TYPE_STOP` | `"stop"` | Stop the song. |
| String | `EXTRA_REQUEST_ID` | `"jp.co.sharp.android.rb.song.extra.REQUEST_ID"` | (int) Song ID for `EXTRA_TYPE_ASSIGN`. Nonexistent/unspecified → normal. |
| String | `EXTRA_REPLYTO_ACTION` | `"jp.co.sharp.android.rb.song.extra.REPLYTO_ACTION"` | Result-notification Intent action. Unspecified → no notification. |
| String | `EXTRA_REPLYTO_PKG` | `"jp.co.sharp.android.rb.song.extra.REPLYTO_PKG"` | Result-notification target package. Unspecified → broadcast to all. |
| String | `EXTRA_RESULT_CODE` | `"jp.co.sharp.android.rb.song.extra.RESULT_CODE"` | Result code key (`RESULT_OK`/`RESULT_CANCELED`). |
| String | `EXTRA_RESULT_ID` | `"jp.co.sharp.android.rb.song.extra.RESULT_ID"` | (int) Executed song ID. |
| String | `EXTRA_RESULT_NAME` | `"jp.co.sharp.android.rb.song.extra.RESULT_NAME"` | Executed song name. |
| String | `PACKAGE` | `"jp.co.sharp.android.rb.song"` | Target package name. |
| int | `RESULT_OK` | `-1` | Song completed. |
| int | `RESULT_CANCELED` | `0` | Song cancelled. |

### Methods

| Return | Signature | Description |
|---|---|---|
| `LinkedHashMap<Integer, String>` | `getInfo(Context context)` | Get the list of songs as (song ID → song name). |

---

# Package: jp.co.sharp.android.rb.rbdance

## Class: DanceUtil

`public class DanceUtil extends Object`

ダンスアプリの起動要求利用時に必要な定義値を提供するクラス. — Constants for requesting the dance feature via Broadcast.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `ACTION_REQUEST_DANCE` | `"jp.co.sharp.android.rb.rbdance.action.REQUEST_DANCE"` | Dance-request broadcast action. |
| String | `EXTRA_TYPE` | `"jp.co.sharp.android.rb.rbdance.extra.TYPE"` | Dance mode (values below). Unspecified/other → normal. |
| String | `EXTRA_TYPE_NORMAL` | `"normal"` | Random dance. |
| String | `EXTRA_TYPE_NEW` | `"new"` | Newest dance. |
| String | `EXTRA_TYPE_REPEAT` | `"repeat"` | Repeat the previously danced dance (→ normal if none). |
| String | `EXTRA_TYPE_ASSIGN` | `"assign"` | Play the specified dance (`EXTRA_REQUEST_ID`). |
| String | `EXTRA_TYPE_MEDLEY` | `"medley"` | Play multiple specified dances (`EXTRA_REQUEST_ID_LIST`), random order. |
| String | `EXTRA_REQUEST_ID` | `"jp.co.sharp.android.rb.rbdance.extra.REQUEST_ID"` | (int) Dance ID for `EXTRA_TYPE_ASSIGN`. Nonexistent/unspecified → normal. |
| String | `EXTRA_REQUEST_ID_LIST` | `"jp.co.sharp.android.rb.rbdance.extra.REQUEST_ID_LIST"` | (int[]) Dance IDs for `EXTRA_TYPE_MEDLEY`. Nonexistent ignored; unspecified → normal. |
| String | `EXTRA_EXCLUDE_ID_LIST` | `"jp.co.sharp.android.rb.rbdance.extra.EXCLUDE_ID_LIST"` | (int[]) Dance IDs to exclude for `EXTRA_TYPE_NORMAL`. Excluding all → ignored. |
| String | `EXTRA_SHUFFLE` | `"jp.co.sharp.android.rb.rbdance.extra.SHUFFLE"` | (boolean) Shuffle medley order. Unspecified → list order. |
| String | `EXTRA_SKIP_COMMENT` | `"jp.co.sharp.android.rb.rbdance.extra.SKIP_COMMENT"` | (boolean) Skip RoBoHoN's post-dance comment. Unspecified → comment spoken. |
| String | `EXTRA_REPLYTO_ACTION` | `"jp.co.sharp.android.rb.rbdance.extra.REPLYTO_ACTION"` | Result-notification Intent action. Unspecified → no notification. |
| String | `EXTRA_REPLYTO_PKG` | `"jp.co.sharp.android.rb.rbdance.extra.REPLYTO_PKG"` | Result-notification target package. Unspecified → broadcast to all. |
| String | `EXTRA_RESULT_CODE` | `"jp.co.sharp.android.rb.rbdance.extra.RESULT_CODE"` | Result code key (`RESULT_OK`/`RESULT_CANCELED`). |
| String | `EXTRA_RESULT_ID` | `"jp.co.sharp.android.rb.rbdance.extra.RESULT_ID"` | (int) Executed dance ID. |
| String | `EXTRA_RESULT_ID_LIST` | `"jp.co.sharp.android.rb.rbdance.extra.RESULT_ID_LIST"` | (int[]) Multiple executed dance IDs. |
| String | `EXTRA_RESULT_NAME` | `"jp.co.sharp.android.rb.rbdance.extra.RESULT_NAME"` | Executed dance name. |
| String | `EXTRA_RESULT_NAME_LIST` | `"jp.co.sharp.android.rb.rbdance.extra.RESULT_NAME_LIST"` | (String[]) Multiple executed dance names. |
| String | `PACKAGE` | `"jp.co.sharp.android.rb.rbdance"` | Target package name. |
| int | `RESULT_OK` | `-1` | Dance completed. |
| int | `RESULT_CANCELED` | `0` | Dance cancelled. |

### Methods

| Return | Signature | Description |
|---|---|---|
| `LinkedHashMap<Integer, String>` | `getInfo(Context context)` | Get the list of dances as (dance ID → dance name). |

---

# Package: jp.co.sharp.android.rb.messaging

## Class: MessagingUtil

`public class MessagingUtil extends Object`

メッセージアプリの起動要求利用時に必要な定義値を提供するクラス. — Constants for triggering the message (mail) send feature via Broadcast.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `ACTION_SEND_MESSAGE` | `"jp.co.sharp.android.rb.messaging.action.SEND_MESSAGE"` | Send-message broadcast action. |
| String | `EXTRA_EMAIL` | `"android.intent.extra.EMAIL"` | Recipient email address. Default empty. |
| String | `EXTRA_SUBJECT` | `"android.intent.extra.SUBJECT"` | Subject. Default fixed text. |
| String | `EXTRA_TEXT` | `"android.intent.extra.TEXT"` | Body (max 900 chars). Default empty. |
| String | `EXTRA_ATTACHMENT_PATH` | `"jp.co.sharp.android.rb.messaging.extra.ATTACHMENT_PATH"` | Attachment file path or ContentProvider URI. |
| String | `EXTRA_SKIP_CONFIRM` | `"jp.co.sharp.android.rb.messaging.extra.SKIP_CONFIRM"` | (boolean) Skip user confirmation. Default `false`. Only valid when both recipient and body are set. |
| String | `EXTRA_BACKGROUND` | `"jp.co.sharp.android.rb.messaging.extra.BACKGROUND"` | (boolean) Run in background. Default `false`. |
| String | `EXTRA_REPLYTO_ACTION` | `"jp.co.sharp.android.rb.messaging.extra.REPLYTO_ACTION"` | Result-notification Intent action. Default no notification. |
| String | `EXTRA_REPLYTO_PKG` | `"jp.co.sharp.android.rb.messaging.extra.REPLYTO_PKG"` | Result-notification target package. Default broadcast to all. |
| String | `EXTRA_RESULT_CODE` | `"jp.co.sharp.android.rb.messaging.extra.RESULT_CODE"` | Result code key (`RESULT_OK`/`RESULT_CANCELED`). |
| String | `PACKAGE` | `"jp.co.sharp.android.rb.messaging"` | Target package name. |
| int | `RESULT_OK` | `-1` | Send completed. |
| int | `RESULT_CANCELED` | `0` | Send cancelled. |

No public methods (constants only).

---

# Package: jp.co.sharp.android.rb.projectormanager

## Class: ProjectorManagerServiceUtil

`public class ProjectorManagerServiceUtil extends Object`

プロジェクターマネージャー利用時に必要な定義値を提供するクラス. — Constants for controlling the built-in projector. Started via `startService` using `PACKAGE_NAME` + `CLASS_NAME`; lifecycle progress arrives as the `ACTION_PROJECTOR_*` broadcasts.

### Constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `PACKAGE_NAME` | `"jp.co.sharp.android.rb.projectormanager"` | Projector manager package (for `startService`). |
| String | `CLASS_NAME` | `"jp.co.sharp.android.rb.projectormanager.ProjectorManagerService"` | Projector manager service class (for `startService`). |
| String | `EXTRA_PROJECTOR_DIRECTION` | `"jp.co.sharp.android.rb.projectormanager.extra.PROJECTOR_DIRECTION"` | Projection direction at start (`front`/`under`). Default `under`. |
| String | `EXTRA_PROJECTOR_DIRECTION_VAL_FRONT` | `"front"` | Project forward (standing/going-out posture). |
| String | `EXTRA_PROJECTOR_DIRECTION_VAL_UNDER` | `"under"` | Project downward (projector-down posture). |
| String | `EXTRA_PROJECTOR_OUTPUT` | `"jp.co.sharp.android.rb.projectormanager.extra.PROJECTOR_OUTPUT"` | Screen orientation at start (`normal`/`reverse`). Default `reverse`. |
| String | `EXTRA_PROJECTOR_OUTPUT_VAL_NORMAL` | `"normal"` | Normal orientation (from RoBoHoN's view). |
| String | `EXTRA_PROJECTOR_OUTPUT_VAL_REVERSE` | `"reverse"` | Reverse orientation (from RoBoHoN's view). |
| String | `ACTION_PROJECTOR_PREPARE` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_PREPARE"` | Broadcast: preparing to project (user auth / passphrase setup). |
| String | `ACTION_PROJECTOR_START` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_START"` | Broadcast: projection started. |
| String | `ACTION_PROJECTOR_PAUSE` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_PAUSE"` | Broadcast: projection paused (posture change). |
| String | `ACTION_PROJECTOR_RESUME` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_RESUME"` | Broadcast: projection resumed. |
| String | `ACTION_PROJECTOR_TERMINATE` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_TERMINATE"` | Broadcast: termination process started (followed by END / END_ERROR / END_FATAL_ERROR). |
| String | `ACTION_PROJECTOR_END` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_END"` | Broadcast: projector ended normally. |
| String | `ACTION_PROJECTOR_END_ERROR` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_END_ERROR"` | Broadcast: projector ended with error. |
| String | `ACTION_PROJECTOR_END_FATAL_ERROR` | `"jp.co.sharp.android.rb.projectormanager.action.PROJECTOR_END_FATAL_ERROR"` | Broadcast: projector ended with fatal/unrecoverable error. |

No public methods (constants only).

---

# Package: jp.co.sharp.android.rb.addressbook

## Class: AddressBookCommonUtils

`public class AddressBookCommonUtils extends Object`

電話帳で使用する定義値を提供するクラス. — Definition values (item keys, result codes, URIs, launch/activity codes) used across the address book.

### Key constants — package / class / action

| Type | Name | Value | Meaning |
|---|---|---|---|
| String | `AUTHORITY_NAME` | `"jp.co.sharp.android.rb.addressbook"` | Address book package name. |
| String | `AUTHORITY_CLASS_NAME_ENTRY` | `"jp.co.sharp.android.rb.addressbook.AddressBookActivity"` | Address book entry Activity. |
| String | `AUTHORITY_CLASS_NAME_SEARCH` | `"jp.co.sharp.android.rb.addressbook.AddressBookSearchActivity"` | Address book search Activity. |
| String | `AUTHORITY_CLASS_NAME_DELETE` | `"jp.co.sharp.android.rb.addressbook.AddressBookDeleteActivity"` | Address book delete Activity. |
| String | `ContactId_ACTION` | `"jp.co.sharp.android.rb.extra.ContactId_ACTION"` | Action used by the address book result-notification Intent. |

### Item keys (for getContactID / get / set)

| Name | Value | Meaning |
|---|---|---|
| `KEY_CONTACT_ID` | `"contact_id"` | Contact ID. |
| `KEY_LAST_NAME` | `"lastname"` | Reading (last name), hiragana only. |
| `KEY_FIRST_NAME` | `"firstname"` | Reading (first name), hiragana only. |
| `KEY_NICKNAME` | `"nickname"` | Nickname, hiragana only. |
| `KEY_ANY_NAME` | `"any_name"` | Match against reading (last/first) or nickname. |
| `KEY_PHONE_NUMBER` | `"phone_number"` | Phone number (compared ignoring `()` and `-`). |
| `KEY_MAIL_ADDRESS` | `"mail_address"` | Email address. |
| `KEY_RELATIONS` | `"relations"` | Relationship to owner (e.g. 夫/妻/父親/母親/友達/その他...). |
| `KEY_BIRTHDAY_YEAR` | `"birthday_year"` | Birth year. |
| `KEY_BIRTHDAY_MONTH` | `"birthday_month"` | Birth month. |
| `KEY_BIRTHDAY_DAY` | `"birthday_day"` | Birth day. |
| `KEY_BIRTHDAY_MONTH_DAY` | `"birthday_month_day"` | Set month+day at once (value `"M/d"`). |
| `KEY_BIRTHDAY_ID` | `"birthday_id"` | Birthday ID (read-only). |
| `KEY_PHOTO` | `"photo"` | Face recognition data (read-only). |
| `KEY_ROBO_NAME` | `"robo_name"` | RoBoHoN's name (hiragana only). |
| `KEY_ADDRESSBOOK_ACTIVITY` | `"addressbook_activity_key"` | Item for the address-book registration launch. |
| `KEY_ADDRESSBOOK_ENTRY_TYPE` | `"addressbook_entry_type_key"` | Registration type on launch. |
| `KEY_ADDRESSBOOK_INTENT_CONTACTID` | `"get_contactid_item_key"` | Contact ID (result notification). |
| `KEY_ADDRESSBOOK_INTENT_TYPE` | `"notification_item_key_type"` | Result-notification type. |
| `KEY_ADDRESSBOOK_SEARCH_CONTACTID` | `"addressbook_search_contactid_key"` | Contact ID for DB search. |
| `KEY_ADDRESSBOOK_SEARCH_ITEM` | `"addressbook_search_item_key"` | Search item key. |
| `KEY_ADDRESSBOOK_SEARCH_VALUE` | `"addressbook_search_value_key"` | Search string (reading or nickname). |
| `KEY_ADDRESSBOOK_SEARCH_VISIBLE` | `"addressbook_search_visible"` | Search Activity display state. |
| `KEY_ADDRESSBOOK_SELECTED_CONTACTID` | `"addressbook_selected_contactid_key"` | Selected contact ID from search results. |
| `KEY_ADDRESSBOOK_UPDATE_ITEM` | `"addressbook_update_item"` | Item key(s) for the update item. |

### Value / mode constants

| Type | Name | Value | Meaning |
|---|---|---|---|
| int | `CONTACT_ID_OWNER` | `201` | Owner profile contact_id. |
| int | `CONTACT_ID_ROBO` | `203` | RoBoHoN profile contact_id. |
| String | `VALUE_ISNULL` | `"ISNULL"` | `getContactID` value: find rows where the key item is unset. |
| String | `VALUE_NOTNULL` | `"NOTNULL"` | `getContactID` value: find rows where the key item is set. |
| int | `BOOK_SEARCH_VISIBLE_ONE_REGISTERED` | `1` | Value for `KEY_ADDRESSBOOK_SEARCH_VISIBLE`: show detail screen if ≥1 contact matches. |
| int | `ADDRESSBOOK_ACTIVITY` | `1` | Launch address book list. |
| int | `OWNER_PROFILE_ACTIVITY` | `2` | Launch "About You" (owner profile) screen. |
| int | `ROBO_PROFILE_ACTIVITY` | `3` | Launch RoBoHoN info screen. |
| int | `ADDRESSBOOK_ENTRY_NEW` | `0` | Registration type: new entry. |
| int | `ADDRESSBOOK_ENTRY_UPDATE` | `1` | Registration type: additional/update entry. |
| int | `ADDRESSBOOK_INTENT_NEW` | `0` | Result-notification type: new registration. |
| int | `ADDRESSBOOK_INTENT_UPDATE` | `1` | Result-notification type: update. |
| int | `ADDRESSBOOK_INTENT_DELETE` | `2` | Result-notification type: delete. |

### Result codes

| Name | Value | Meaning |
|---|---|---|
| `RESULT_OK` | `-1` | Success. |
| `RESULT_CANCELED` | `0` | Failure (cancel). |
| `RESULT_OK_CHOICE` | `1` | Success (selected). |
| `RESULT_FAILED_ALLOCTION_CONTACT` | `-1` | Failed to allocate new contact. |
| `RESULT_FAILED_CHANGE_PROHIBITION` | `2` | Tried to change a read-only value. |
| `RESULT_FAILED_NO_CONTENTID` | `3` | Invalid contact ID. |
| `RESULT_FAILED_UNKNOWN` | `4` | Other failure. |
| `RESULT_FAILED_CONTACT_FULL` | `5` | Address book full. |
| `RESULT_FAILED_INVALID_PARAM` | `6` | Invalid input parameter. |
| `RESULT_FAILED_PERMISSION_DENIED` | `7` | Permission denied. |

### Public fields (Content URIs, require `ACCESS_CONTACT` permission)

| Type | Name | Meaning |
|---|---|---|
| Uri | `URI_BOOK_CONTACT` | Access address book data. |
| Uri | `URI_FACE_CONTACT` | Access photo/image data. |
| Uri | `URI_OWNER_CONTACT` | Access owner profile data. |
| Uri | `URI_ROBO_CONTACT` | Access RoBoHoN profile data. |

## Class: AddressBookManager

`public class AddressBookManager extends Object`

電話帳 / オーナープロフィール / ロボホンプロフィール へのアクセスを提供するクラス. — Provides programmatic access to the address book and owner/RoBoHoN profiles. Obtain via `getService(Context)`. Most methods `throw RemoteException`.

### Methods

| Return | Signature | Description |
|---|---|---|
| static `AddressBookManager` | `getService(Context context)` | Create/return the manager instance. Returns null if context is null. |
| `AddressBookData` | `getAddressBookData(int contact_id)` | Get a contact record by contact ID. |
| String | `getAddressBookItemString(int contact_id, String key)` | Get a TEXT-type item for a contact. |
| int | `getAddressBookItemInt(int contact_id, String key)` | Get an INTEGER-type item for a contact. |
| boolean | `getAddressBookItemBoolean(int contact_id, String key)` | Get a BOOLEAN-type item for a contact. |
| byte[] | `getAddressBookPhoto(int contact_id)` | Get a contact's face photo image bytes. |
| int | `getAddressBookNewContact()` | Allocate a new address book record and return its contact_id. |
| int[] | `getContactID(String key, String value)` | Search by item key + String value; returns matching contact IDs (count + IDs). Supports `VALUE_ISNULL`/`VALUE_NOTNULL`; `KEY_BIRTHDAY_MONTH_DAY` uses `"M/d"`; `KEY_ANY_NAME` uses `"reading"` or `"reading,honorific"`. |
| int[] | `getContactID(String key, int value)` | Search by item key + int value; returns matching contact IDs. |
| `OwnerProfileData` | `getOwnerProfileData()` | Get owner profile data. |
| String | `getOwnerProfileDataItemString(String key)` | Get a TEXT-type owner profile item. |
| int | `getOwnerProfileDataItemInt(String key)` | Get an INTEGER-type owner profile item. |
| boolean | `getOwnerProfileDataItemBoolean(String key)` | Get a BOOLEAN-type owner profile item. |
| byte[] | `getOwnerProfileDataPhoto()` | Get the owner's registered face image bytes. |
| `RoboProfileData` | `getRoboProfileData()` | Get RoBoHoN profile data. |
| String | `getRoboProfileDataItem(String key)` | Get a TEXT-type RoBoHoN profile item. |

---

# Package: jp.co.sharp.android.rb.addressbook.AddressBookVariable

Data holder classes (all `implements Parcelable`).

## Class: AddressBookData

`public class AddressBookData ... implements Parcelable`

電話帳データ(1件分)情報クラス. — Single address book entry.

| Return | Signature | Description |
|---|---|---|
| int | `getContact_id()` | Contact ID. |
| String | `getLastname()` | Reading (last name). |
| String | `getFirstname()` | Reading (first name). |
| String | `getNickname()` | Nickname. |
| String | `getPhone_number()` | Phone number. |
| String | `getMail_address()` | Email address. |
| String | `getRelations()` | Relationship to owner. |
| int | `getBirthday_year()` | Birth year. |
| int | `getBirthday_month()` | Birth month. |
| int | `getBirthday_day()` | Birth day. |
| int | `getBirthday_id()` | Birthday ID. |
| byte[] | `getPhoto()` | Photo data. |

## Class: OwnerProfileData

`public class OwnerProfileData ... implements Parcelable`

オーナープロフィール情報クラス. — Owner profile.

| Return | Signature | Description |
|---|---|---|
| String | `getLastname()` | Reading (last name). |
| String | `getFirstname()` | Reading (first name). |
| String | `getNickname()` | Nickname. |
| int | `getBirthday_year()` | Birth year. |
| int | `getBirthday_month()` | Birth month. |
| int | `getBirthday_day()` | Birth day. |
| int | `getBirthday_id()` | Birthday ID. |
| byte[] | `getPhoto()` | Photo data. |

## Class: RoboProfileData

`public class RoboProfileData ... implements Parcelable`

ロボホンプロフィール情報クラス. — RoBoHoN profile.

| Return | Signature | Description |
|---|---|---|
| String | `getRbname()` | RoBoHoN's name. |
| String | `getPhone_number()` | Phone number. |
| String | `getMail()` | Email address. |
