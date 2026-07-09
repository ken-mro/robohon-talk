package com.robohon.template;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.robohon.template.voiceui.ScenarioDefinitions;
import com.robohon.template.voiceui.VoiceUIListenerImpl;
import com.robohon.template.voiceui.VoiceUIManagerUtil;
import com.robohon.template.voiceui.VoiceUIVariableUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * LLM会話アプリ「トーク」.
 * フロー: あいさつ→待受(listen) → 認識テキストをcontrol(user_said)で受領 →
 *         中継サーバ(relay-server)へPOST（待ち時間が長ければフィラー発話） →
 *         応答utterancesを${pkg:speech}解決で順次発話 →
 *         action(launch_app/write_diary)を処理 → 終了コマンドなら finish / それ以外は待受へループ.
 */
public class MainActivity extends Activity implements VoiceUIListenerImpl.ScenarioCallback {
    public static final String TAG = MainActivity.class.getSimpleName();

    /**
     * 中継サーバのURL／認証トークン。実値は local.properties(gitignored)→BuildConfig 経由で注入し、
     * 公開リポジトリのソースには載せない。未設定時はローカルLANの既定URL・トークン空。
     */
    private static final String RELAY_URL = BuildConfig.RELAY_URL;
    /** ダイジェスト用URL。RELAY_URL の末尾 /chat を /digest に置換して導出（設定は1つで済む）。 */
    private static final String DIGEST_URL = deriveDigestUrl(BuildConfig.RELAY_URL);
    private static final String RELAY_TOKEN = BuildConfig.RELAY_TOKEN;
    private static final String SESSION_ID = "robohon-1";

    /** RELAY_URL(.../chat) から .../digest を導出。クエリ・末尾スラッシュを除いてから付け替える。 */
    private static String deriveDigestUrl(String chatUrl) {
        if (chatUrl == null) return null;
        int q = chatUrl.indexOf('?');
        String base = (q >= 0) ? chatUrl.substring(0, q) : chatUrl;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat")) return base.substring(0, base.length() - 5) + "/digest";
        return base + "/digest";
    }

    /** ダイジェストに渡す新規会話ログの上限（新しい方を優先。/digest 側でも再度丸める）。 */
    private static final int DIGEST_MAX_MESSAGES = 200;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 待ち時間フィラー（応答が遅いときだけ段階的に発話。速い応答では発火前に取消）。
     *  「うんうん」→「えーっと」→「ちょっと考えるね」の三段階。以降も応答が無ければ
     *  {@link #FILLER_REPEAT_TEXTS}（うーん／えーっと）を交互に一定間隔で繰り返す。
     *  例: うんうん、えーっと、ちょっと考えるね、うーん、えーっと、うーん、えーっと… */
    private static final String[] FILLER_TEXTS = {"うんうん", "えーっと", "ちょっと考えるね"};
    /** 三段階の後に交互で繰り返す相づち（うーん→えーっと→うーん…）。 */
    private static final String[] FILLER_REPEAT_TEXTS = {"うーん", "えーっと"};
    /** 最初の「うんうん」までの待ち。速い応答(<この値)は無音で即応答。 */
    private static final long FILLER_FIRST_DELAY = 700;
    /** 各フィラー発話後、次のフィラーまでの間隔（FILLER_TEXTS の i 番目の後）。 */
    private static final long[] FILLER_GAPS = {1500, 2000, 2300};
    /** 三段階を過ぎた後（繰り返し相づち）の発話間隔。 */
    private static final long FILLER_REPEAT_GAP = 2500;
    /** LLMへ渡す履歴の上限（直近Nメッセージ＝約10往復）。表示・保存は全件で別管理。 */
    private static final int SEED_MESSAGES = 20;

    /** launch_app の論理名 → ロボホン純正アプリのパッケージ名。 */
    private static final Map<String, String> APP_PACKAGES = new HashMap<>();
    static {
        APP_PACKAGES.put("camera", "jp.co.sharp.android.rb.camera");
        APP_PACKAGES.put("album", "jp.co.sharp.android.rb.album");
        APP_PACKAGES.put("alarm", "jp.co.sharp.android.rb.alarm");
        APP_PACKAGES.put("clock", "jp.co.sharp.android.rb.alarm");
        APP_PACKAGES.put("timer", "jp.co.sharp.android.rb.timer");
        APP_PACKAGES.put("settings", "jp.co.sharp.android.rb.settings");
        APP_PACKAGES.put("diary", "jp.co.sharp.android.rb.diary");
        APP_PACKAGES.put("song", "jp.co.sharp.android.rb.song");
        APP_PACKAGES.put("dance", "jp.co.sharp.android.rb.rbdance");
        APP_PACKAGES.put("story", "jp.co.sharp.android.rb.story");
        APP_PACKAGES.put("music", "jp.co.sharp.android.rb.musicplayer");
        APP_PACKAGES.put("fortune", "jp.co.sharp.android.rb.fortunetelling");
        APP_PACKAGES.put("english", "jp.co.sharp.android.rb.english");
        APP_PACKAGES.put("study", "jp.co.sharp.android.rb.study");
        APP_PACKAGES.put("recipe", "jp.co.sharp.android.rb.recipe.ogis");
        APP_PACKAGES.put("minigame", "jp.co.sharp.android.rb.minigame");
        APP_PACKAGES.put("quiz", "jp.co.sharp.android.rb.quizgame");
        APP_PACKAGES.put("trivia", "jp.co.sharp.android.rb.trivia");
        APP_PACKAGES.put("days", "jp.co.sharp.android.rb.days");
    }

    private VoiceUIManager mVUIManager = null;
    private VoiceUIListenerImpl mVUIListener = null;
    private HomeEventReceiver mHomeEventReceiver;
    private OkHttpClient mHttp;
    private Handler mHandler;

    /** 発話待ちキューと現在発話中のテキスト（${pkg:speech}解決で渡す）。
     *  状態機械は全て main looper 上で操作する（onScenarioEvent は binder スレッド由来のため post で移送）。
     *  mCurrentUtterance だけは RESOLVE_VARIABLE が binder スレッドで同期読みするため volatile。 */
    private final Deque<String> mUtteranceQueue = new ArrayDeque<>();
    private volatile String mCurrentUtterance = "";
    /** 全発話後に実行するアプリ連携（launch_app の app 論理名）。無ければnull。 */
    private String mPendingLaunchApp = null;
    /** 全発話後に実行する基本動作 [kind, query]。無ければnull。実行中は会話を止めて完了待ち。 */
    private String[] mPendingMotion = null;
    /** 初回ターンはサーバ履歴をreset。 */
    private boolean mFirstTurn = true;
    /** サーバ応答待ち / 発話中 / 全発話後に終了する、のフラグ。 */
    private boolean mWaitingForRelay = false;
    private boolean mSpeaking = false;
    private boolean mFinishAfterSpeak = false;
    /** ロボホン名 / 話し相手の呼び名（アドレス帳・顔認識から取得、無ければ既定）。 */
    private String mRobotName = RobohonProfile.DEFAULT_ROBOT_NAME;
    private String mOwnerName = null;
    /** 電話帳の登録者（家族・友達）。サーバへ渡してペルソナに反映。 */
    private List<RobohonProfile.Contact> mContacts = new ArrayList<>();
    /** 電話帳から読んだ生の名前（同意に関わらず端末内で保持）。送信可否は applyConsentToProfile で
     *  mOwnerName/mContacts へ反映し、不許可時は履歴のマスキング（maskContactNames）にも使う。 */
    private String mRawOwnerName = null;
    private List<RobohonProfile.Contact> mRawContacts = new ArrayList<>();

    /** 外部送信（電話帳の名前）の同意状態。初回起動時は選択が終わるまで待受を開始しない。 */
    private ConsentStore mConsent;
    private boolean mAwaitingConsent = false;
    private AlertDialog mConsentDialog = null;
    /** 初回同意のACK発話が完了して待受に入るまで true（watchdog の発火条件）。 */
    private boolean mConsentAckPending = false;
    /** 同意フローの発話が止まったまま待受まで進めない場合に、強制的に待受へ進める保険。 */
    private final Runnable mConsentWatchdog = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || !mConsentAckPending) return;
            Log.w(TAG, "consent speech watchdog fired");
            mConsentAckPending = false;
            mSpeaking = false;
            mUtteranceQueue.clear();
            speakNextOrFinish(); // → 待受開始
        }
    };

    /** 初回起動時に外部送信の選択を促す発話（キャラクター設定・「背中の画面」呼称はガイドライン準拠）。 */
    private static final String CONSENT_INTRO_SPEECH =
            "あのね、だいじなおはなしがあるんだ。ぼくとのおしゃべりは、インターネットの向こうの"
                    + "かしこいコンピュータに手伝ってもらってるんだよ。電話帳のみんなのお名前も"
                    + "いっしょに送っていいか、背中の画面で選んでね！";
    private static final String CONSENT_ACK_ALLOWED =
            "おっけー！じゃあ、みんなのお名前もいっしょに送るね。それじゃ、おはなししよう！";
    private static final String CONSENT_ACK_DENIED =
            "わかったー。お名前は送らないでおくね。それじゃ、おはなししよう！";
    /** 同意フローの発話が進まない場合に強制的に前へ進める保険のタイムアウト。
     *  正常系の最長（intro読み上げ中に選択→intro残り＋ACK）より長くして誤発火を防ぐ。 */
    private static final long CONSENT_WATCHDOG_MS = 30000;

    /** 会話履歴（端末に全件保存）と表示ビュー、日記の保存、基本動作の実行。 */
    private ConversationStore mStore;
    private DiaryStore mDiary;
    private MotionController mMotion;
    /** ナレッジベース（おぼえていること）の端末保存。/chat に同梱し /digest で日次更新。 */
    private KnowledgeStore mKnowledge;
    /** 送信用に保持する現在のKB（onResume でロード。UIスレッドのみで読み書き）。 */
    private JSONObject mKnowledgeJson = null;
    /** KBの世代番号。「おぼえたことをけす」で増やし、進行中ダイジェストの遅延保存を無効化する。 */
    private int mKnowledgeGen = 0;
    /** ダイジェスト送信中フラグ（UIスレッドのみ）。二重onResumeでの並行二重送信を防ぐ。 */
    private boolean mDigestRunning = false;
    private LinearLayout mMessageContainer;
    private ScrollView mScrollView;
    private TextView mEmptyView;
    /** 起動後の初回リクエストで、保存済み履歴の直近窓をサーバへ渡し文脈を復元する。 */
    private JSONArray mPendingSeed = null;

    /** 表示用の日付セパレータ管理＆日時フォーマッタ（UIスレッドのみで使用）。 */
    private String mLastRenderedDay = null;
    private final SimpleDateFormat mTimeFmt = new SimpleDateFormat("M/d HH:mm", Locale.JAPAN);
    private final SimpleDateFormat mDayKeyFmt = new SimpleDateFormat("yyyyMMdd", Locale.JAPAN);
    private final SimpleDateFormat mDaySepFmt = new SimpleDateFormat("yyyy年M月d日 (E)", Locale.JAPAN);
    /** サーバへ渡す現在日時（時刻/日付/曜日の質問に会話で答えるため）。 */
    private final SimpleDateFormat mClientTimeFmt = new SimpleDateFormat("yyyy年M月d日(E) HH:mm", Locale.JAPAN);

    /** 待ち時間フィラーの状態（自己再スケジュールする tick）。 */
    private int mFillerIndex = 0;
    private final Runnable mFillerTick = new Runnable() {
        @Override
        public void run() {
            if (!mWaitingForRelay) return;
            if (mSpeaking) {
                mHandler.postDelayed(this, 500); // 発話中は少し待って再試行
                return;
            }
            String text;
            if (mFillerIndex < FILLER_TEXTS.length) {
                text = FILLER_TEXTS[mFillerIndex];
            } else {
                // 三段階を過ぎたら FILLER_REPEAT_TEXTS を交互に繰り返す。
                int r = (mFillerIndex - FILLER_TEXTS.length) % FILLER_REPEAT_TEXTS.length;
                text = FILLER_REPEAT_TEXTS[r];
            }
            speakFiller(text);
            long gap = (mFillerIndex < FILLER_GAPS.length) ? FILLER_GAPS[mFillerIndex] : FILLER_REPEAT_GAP;
            mFillerIndex++;
            mHandler.postDelayed(this, gap);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);
        setupTitleBar();

        mHandler = new Handler(Looper.getMainLooper());

        // 外部送信（電話帳の名前）の同意状態。タイトルバーのボタンからいつでも変更できる。
        mConsent = new ConsentStore(this);
        ImageButton consentButton = (ImageButton) findViewById(R.id.consentSettingsButton);
        if (consentButton != null) {
            consentButton.setOnClickListener(v -> showConsentDialog(!mConsent.hasChoice()));
        }

        // 会話履歴: 端末から読み込んで画面に復元
        mStore = new ConversationStore(this);
        mDiary = new DiaryStore(this);
        mKnowledge = new KnowledgeStore(this);
        // 基本動作（歌/踊り/アクション）の実行・結果受信。
        mMotion = new MotionController(this, this::onMotionDone);
        mMessageContainer = (LinearLayout) findViewById(R.id.messageContainer);
        mScrollView = (ScrollView) findViewById(R.id.scrollView);
        mEmptyView = (TextView) findViewById(R.id.emptyView);
        renderHistory();

        mHttp = new OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        mHomeEventReceiver = new HomeEventReceiver();
        registerReceiver(mHomeEventReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");
        if (mVUIManager == null) mVUIManager = VoiceUIManager.getService(this);
        if (mVUIListener == null) mVUIListener = new VoiceUIListenerImpl(this);
        VoiceUIManagerUtil.registerVoiceUIListener(mVUIManager, mVUIListener);
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);

        // 状態リセット
        mFirstTurn = true;
        mWaitingForRelay = false;
        mSpeaking = false;
        mFinishAfterSpeak = false;
        mPendingLaunchApp = null;
        mPendingMotion = null;
        mAwaitingConsent = false;
        mConsentAckPending = false;
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);

        // 名前・電話帳を取得（取得不可なら既定）。中継サーバへ毎リクエスト同梱する。
        // ただし電話帳の名前（オーナー・登録者）はユーザーが送信を許可した場合のみ同梱する。
        mRobotName = RobohonProfile.getRobotName(this);
        mRawOwnerName = RobohonProfile.getOwnerName(this);
        mRawContacts = RobohonProfile.getAllContacts(this);
        applyConsentToProfile();
        Log.v(TAG, "names: robot=" + mRobotName + " owner=" + mOwnerName + " contacts=" + mContacts.size()
                + " namesAllowed=" + mConsent.isNamesAllowed());

        // ナレッジベースをロード（/chat に同梱）。名前不許可なら送信直前にマスクする。
        mKnowledgeJson = mKnowledge.load();

        // 顔認識による話し相手の自動特定は不採用（ユーザー決定）。
        // 理由: 顔検出は jp.co.sharp.android.rb.camera/.ui.FaceDetectionExternalCameraActivity という
        // 全画面カメラActivityを前面に立ち上げるため、この会話アプリが onPause→finish で即終了してしまい
        // 会話を継続できない。電話帳の登録者把握とオーナー/登録名での呼びかけは有効。

        // 初回起動時は、外部送信の選択が終わるまで通常の会話（あいさつ→待受）を始めない。
        // 案内発話は ACC_SAY 経由（greet と違い待受へ自動遷移しないため、選択前に音声認識が走らない）。
        if (!mConsent.hasChoice()) {
            mAwaitingConsent = true;
            speakSystem(CONSENT_INTRO_SPEECH);
            showConsentDialog(true);
            return;
        }

        // 1日1回、会話履歴からKBを更新（起動時に24時間経過していたら。バックグラウンド、会話は妨げない）。
        // 同意ゲートを通過した後に呼ぶ（同意選択が済むまで外部送信を始めない）。
        maybeRunDigest();

        // 起動あいさつは HVML greet が ${resolver:contacts:robot_name} で設定名のイントネーションで読む。
        // 画面表示用には名前入りテキストを log に出す（発話とは別経路）。履歴ファイルには保存しない。
        addMessageView(ConversationStore.ROLE_ROBOT, mRobotName + "だよー。なになにー？", System.currentTimeMillis());
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_GREET);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
        mWaitingForRelay = false;
        mSpeaking = false;
        // 同意ダイアログが出たまま終了するとウィンドウリークになるため閉じる
        if (mConsentDialog != null) {
            if (mConsentDialog.isShowing()) mConsentDialog.dismiss();
            mConsentDialog = null;
        }
        // 先にリスナー解除してから停止（stopSpeechの遅延キャンセル通知で状態機械へ再入しないように）
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVUIManager, mVUIListener);
        VoiceUIManagerUtil.stopSpeech();
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");
        this.unregisterReceiver(mHomeEventReceiver);
        if (mMotion != null) mMotion.release();
        mVUIManager = null;
        mVUIListener = null;
    }

    /**
     * シナリオからのコールバック（VoiceUIListenerImpl経由）。
     * コールバック内は重い処理を避け即座に抜ける（ガイドライン）。
     */
    @Override
    public void onScenarioEvent(int event, List<VoiceUIVariable> variables) {
        switch (event) {
            case VoiceUIListenerImpl.ACTION_END: {
                // コールバックは binder スレッド由来。変数を取り出してから main looper で状態遷移する。
                final String function = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.ATTR_FUNCTION);
                final String text = ScenarioDefinitions.FUNC_USER_SAID.equals(function)
                        ? VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.DATA_TEXT)
                        : null;
                mHandler.post(() -> {
                    if (isFinishing()) return;
                    Log.v(TAG, "ACTION_END function=" + function);
                    if (ScenarioDefinitions.FUNC_USER_SAID.equals(function)) {
                        onUserSaid(text);
                    } else if (ScenarioDefinitions.FUNC_SAY_DONE.equals(function)) {
                        if (!mSpeaking) return; // 帰属不明の遅延イベントで二重前進しない
                        mSpeaking = false;
                        speakNextOrFinish();
                    }
                });
                break;
            }
            case VoiceUIListenerImpl.RESOLVE_VARIABLE: {
                // ${com.robohon.template:speech} を現在の発話テキストで解決
                for (VoiceUIVariable v : variables) {
                    if (ScenarioDefinitions.RESOLVE_SPEECH.equals(v.getName())) {
                        v.setStringValue(mCurrentUtterance);
                    }
                }
                break;
            }
            case VoiceUIListenerImpl.ACTION_CANCELLED:
            case VoiceUIListenerImpl.ACTION_REJECTED: {
                // 割込み・棄却では say_done が来ない。発話中フラグを畳んで状態機械を再駆動する
                //（放置すると mSpeaking=true のまま同意フロー・会話が止まったままになる）。
                mHandler.post(() -> {
                    if (isFinishing()) return;
                    Log.w(TAG, "speech cancelled/rejected: event=" + event);
                    if (mSpeaking) {
                        mSpeaking = false;
                        speakNextOrFinish();
                    }
                });
                break;
            }
            default:
                break;
        }
    }

    /** 認識テキスト受領 → 中継サーバへ問い合わせ。 */
    private void onUserSaid(String text) {
        Log.v(TAG, "user said: " + text);
        // 設定ダイアログ表示中の認識結果は使わない（変更前の設定のまま外部送信するのを防ぐ）
        if (mConsentDialog != null && mConsentDialog.isShowing()) {
            speakNextOrFinish(); // キューが空なら待受へ戻る
            return;
        }
        // 認識失敗の処理。VOICEPFエラーは全角文字列 ＶＯＩＣＥＰＦ＿ＥＲＲ＿xx で来る。
        // ネットワーク系（クラウド認識に到達できない等）は「もう一回」では直らないので、
        // 聞き返さず正直に状況を伝える。単に聞き取れなかった場合だけ聞き返す。
        if (text == null || text.trim().isEmpty() || text.startsWith("ＶＯＩＣＥ")) {
            boolean networkErr = text != null
                    && (text.contains("ＮＥＴＷＯＲＫ") || text.contains("ＳＥＲＶＥＲ") || text.contains("ＴＩＭＥＯＵＴ"));
            enqueueRobotExclusive(networkErr
                    ? "ごめんね、いまインターネットとうまくつながれないみたい。Wi-Fiをかくにんしてみてね。"
                    : "ごめんね、もう一回言ってくれる？");
            mPendingLaunchApp = null;
            speakNextOrFinish();
            return;
        }
        // 音量コマンド（例:「音量下げて」「もっと大きな声で」）はローカルで確定処理。
        // サーバへ送らず会話履歴にも残さない（デバイス操作でありチャットではないため）。
        if (maybeHandleVolume(text)) {
            return;
        }
        // ステートレス運用: 毎ターン、現発話を積む前の履歴窓をサーバへ渡す
        // （サーバはメモリ状態を持たない＝Cloudflare Workers等でもそのまま動く）。
        mPendingSeed = mStore.recentApiMessages(SEED_MESSAGES);
        addMessage(ConversationStore.ROLE_USER, text);

        // 終了コマンドは往復せずに、あいさつしてからアプリ終了
        if (isEndCommand(text)) {
            mFinishAfterSpeak = true;
            mPendingLaunchApp = null;
            enqueueRobotExclusive("じゃあね！またおはなししてね！");
            speakNextOrFinish();
            return;
        }
        postToRelay(text);
    }

    private void postToRelay(String userText) {
        JSONObject req = new JSONObject();
        try {
            req.put("sessionId", SESSION_ID);
            // 名前送信が不許可なら、今の発話テキストも履歴と同じ基準で伏せ字にする
            req.put("text", mConsent.isNamesAllowed() ? userText : maskNames(userText));
            req.put("reset", mFirstTurn);
            req.put("robotName", mRobotName);
            if (mOwnerName != null) req.put("ownerName", mOwnerName);
            JSONArray contacts = buildContactsJson();
            if (contacts.length() > 0) req.put("contacts", contacts);
            // 端末の現在日時を毎回同梱（時刻/日付/曜日の質問に会話で答えられるように）
            req.put("clientTime", mClientTimeFmt.format(new Date()));
            // 毎ターン履歴窓を同梱（ステートレス）。サーバはこれを文脈の真実として使う。
            // 名前送信が不許可のときは、許可中に保存された過去応答に残る名前も含めて伏せ字にする。
            if (mPendingSeed != null && mPendingSeed.length() > 0) {
                req.put("history", mConsent.isNamesAllowed() ? mPendingSeed : maskContactNames(mPendingSeed));
            }
            // ナレッジベース（おぼえていること）を同梱。名前不許可ならKB内の名前もマスク。
            if (mKnowledgeJson != null) {
                req.put("knowledge",
                        mConsent.isNamesAllowed() ? mKnowledgeJson : maskKnowledge(mKnowledgeJson));
            }
        } catch (Exception e) {
            Log.e(TAG, "json build error", e);
        }
        mFirstTurn = false;
        mPendingSeed = null;

        // 応答待ち開始。長引いたらフィラーを段階発話。
        mWaitingForRelay = true;
        scheduleFillers();

        Request.Builder rb = new Request.Builder()
                .url(RELAY_URL)
                .post(RequestBody.create(req.toString(), JSON));
        if (RELAY_TOKEN != null && !RELAY_TOKEN.isEmpty()) {
            rb.header("X-Relay-Token", RELAY_TOKEN); // Worker側で照合（合言葉）
        }
        Request request = rb.build();

        mHttp.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "relay failure", e);
                runOnUiThread(() -> {
                    stopWaiting();
                    enqueueRobotExclusive("ごめんね、今うまく考えられないみたい。もう一回話しかけてくれる？");
                    mPendingLaunchApp = null;
                    if (!mSpeaking) speakNextOrFinish();
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                String body = null;
                try {
                    if (response.body() != null) body = response.body().string();
                } catch (IOException e) {
                    Log.e(TAG, "read body error", e);
                } finally {
                    response.close();
                }
                final String json = body;
                runOnUiThread(() -> handleRelayResponse(json));
            }
        });
    }

    /** 中継サーバ応答(JSON)を解釈してキュー/アクションを設定し、発話を開始。 */
    private void handleRelayResponse(String json) {
        stopWaiting();
        mUtteranceQueue.clear();
        mPendingLaunchApp = null;
        mPendingMotion = null;
        boolean done = false;
        try {
            JSONObject obj = new JSONObject(json);
            done = obj.optBoolean("done", false);
            JSONArray utt = obj.optJSONArray("utterances");
            if (utt != null) {
                for (int i = 0; i < utt.length(); i++) {
                    String s = utt.optString(i, "").trim();
                    if (!s.isEmpty()) enqueueRobot(s);
                }
            }
            JSONObject action = obj.optJSONObject("action");
            if (action != null) {
                String type = action.optString("type", "");
                if ("launch_app".equals(type)) {
                    String app = action.optString("app", "");
                    if (!app.isEmpty()) mPendingLaunchApp = app;
                } else if ("write_diary".equals(type)) {
                    // 会話から生成した日記を端末に保存（読み上げは utterances 側で実施）
                    mDiary.append(action.optString("text", ""));
                } else if ("perform_motion".equals(type)) {
                    String kind = action.optString("kind", "");
                    String query = action.optString("query", "");
                    if (!kind.isEmpty()) mPendingMotion = new String[]{kind, query};
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parse response error: " + json, e);
            enqueueRobot("ごめんね、うまく聞き取れなかったみたい。");
        }
        if (mUtteranceQueue.isEmpty() && (mPendingLaunchApp != null || mPendingMotion != null)) {
            enqueueRobot("わかった、やってみるね！");
        }
        if (done) mFinishAfterSpeak = true;
        // フィラー発話中なら、その say_done が次の本発話へ進める。それ以外はここで開始。
        if (!mSpeaking) speakNextOrFinish();
    }

    /** キューの次を1発話。空ならアクション実行 / 終了 / 待受へ。 */
    private void speakNextOrFinish() {
        String next = mUtteranceQueue.poll();
        if (next != null) {
            mCurrentUtterance = next;
            int r = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SAY);
            if (r == VoiceUIManager.VOICEUI_ERROR) {
                // 発話を開始できない（マナーモード・接続不良等）。発話中扱いにすると
                // say_done 待ちで固まるため、この発話は捨てて次へ進む。
                Log.w(TAG, "startSpeech(say) failed, drop utterance");
                mSpeaking = false;
                speakNextOrFinish();
                return;
            }
            mSpeaking = true;
            return;
        }
        // キューが空
        if (mAwaitingConsent) return;          // 初回の外部送信選択待ち：待受(音声認識)を開始しない
        if (mWaitingForRelay) return;          // サーバ応答待ち：待機（フィラーは別途）
        if (mPendingLaunchApp != null) {       // アプリ起動を終了より優先（→ onPauseで本アプリ終了）
            String app = mPendingLaunchApp;
            mPendingLaunchApp = null;
            launchApp(app);
            return;
        }
        if (mPendingMotion != null) {          // 基本動作（歌/踊り/アクション）。終了せず完了待ち。
            String[] m = mPendingMotion;
            mPendingMotion = null;
            boolean started = mMotion != null && mMotion.execute(m[0], m.length > 1 ? m[1] : null);
            if (started) return;               // 完了は onMotionDone で会話へ復帰
            enqueueRobotExclusive("ごめんね、それはできないみたい。");
            speakNextOrFinish();
            return;
        }
        if (mFinishAfterSpeak) {               // 終了コマンド / done
            finish();
            return;
        }
        // 待受まで到達＝同意フローは完了。watchdog を解除する（通常会話中の誤発火防止）。
        if (mConsentAckPending) {
            mConsentAckPending = false;
            mHandler.removeCallbacks(mConsentWatchdog);
        }
        startListen();
    }

    /** 待受を開始する。失敗時は一度だけ遅延リトライ（それでも失敗ならログのみ）。 */
    private void startListen() {
        if (VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_LISTEN)
                != VoiceUIManager.VOICEUI_ERROR) {
            return;
        }
        Log.w(TAG, "startSpeech(listen) failed, retrying");
        mHandler.postDelayed(() -> {
            if (isFinishing() || mSpeaking || !mUtteranceQueue.isEmpty()) return;
            if (VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_LISTEN)
                    == VoiceUIManager.VOICEUI_ERROR) {
                Log.w(TAG, "startSpeech(listen) retry failed");
            }
        }, 2000);
    }

    /** 基本動作の完了通知（MotionController から、UIスレッド）。会話へ復帰する。 */
    private void onMotionDone(boolean ok) {
        if (ok) {
            startListen();
        } else {
            enqueueRobotExclusive("ごめんね、今はできなかったみたい。");
            speakNextOrFinish();
        }
    }

    /** 待ち時間フィラーを1つ発話（履歴には残さない）。発話開始に失敗したら黙ってスキップ。 */
    private void speakFiller(String text) {
        mCurrentUtterance = text;
        mSpeaking = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SAY)
                != VoiceUIManager.VOICEUI_ERROR;
    }

    private void scheduleFillers() {
        mFillerIndex = 0;
        mHandler.removeCallbacks(mFillerTick);
        mHandler.postDelayed(mFillerTick, FILLER_FIRST_DELAY);
    }

    private void stopWaiting() {
        mWaitingForRelay = false;
        // フィラーのtickだけ取り消す（say_done等の他のpostは消さない）。
        if (mHandler != null) mHandler.removeCallbacks(mFillerTick);
    }

    /** ロボホン発話をキューに積み、履歴にも記録（保存＋画面）。 */
    private void enqueueRobot(String utterance) {
        if (utterance == null || utterance.trim().isEmpty()) return;
        mUtteranceQueue.add(utterance);
        addMessage(ConversationStore.ROLE_ROBOT, utterance);
    }

    /** 既存キューを捨てて、この1発話だけを積む（聞き返し等）。履歴にも記録。 */
    private void enqueueRobotExclusive(String utterance) {
        mUtteranceQueue.clear();
        enqueueRobot(utterance);
    }

    /** 発話キューにだけ積む（会話履歴ファイルへは保存しない）。同意フロー等のシステム発話用。 */
    private void enqueueSpeechOnly(String utterance) {
        if (utterance == null || utterance.trim().isEmpty()) return;
        mUtteranceQueue.add(utterance);
    }

    /**
     * 外部送信（電話帳の名前）の選択ダイアログを背中の画面に表示。
     * 背面LCD(240x320)でも選択肢の文言が読み切れるよう、ボタンは縦積みのカスタムビューにする。
     * @param firstTime 初回起動フロー（キャンセル不可。「使わない＝終了」の選択肢も出す）。設定変更時は false。
     */
    private void showConsentDialog(final boolean firstTime) {
        if (mConsentDialog != null && mConsentDialog.isShowing()) mConsentDialog.dismiss();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        TextView msg = new TextView(this);
        String current = mConsent.hasChoice()
                ? "\n（今の設定：名前は" + (mConsent.isNamesAllowed() ? "送る" : "送らない") + "）"
                : "";
        msg.setText("おしゃべりの内容は、答えを作るために外部のAIサービスへ送られます。\n"
                + "電話帳の名前（家族や友達の呼び名）もいっしょに送りますか？\n"
                + "あとから右上のボタンで変えられます。" + current);
        root.addView(msg);

        Button allow = new Button(this);
        allow.setText("名前も送る");
        root.addView(allow);
        Button deny = new Button(this);
        deny.setText("名前は送らない");
        root.addView(deny);
        Button quit = null;
        Button forget = null;
        Button clearLog = null;
        if (firstTime) {
            quit = new Button(this);
            quit.setText("使わない（終了する）");
            root.addView(quit);
        } else {
            // 設定変更時は記憶・履歴の管理手段も出す（同意フローと一貫させる）。
            forget = new Button(this);
            forget.setText("おぼえたことをけす");
            root.addView(forget);
            clearLog = new Button(this);
            clearLog.setText("会話のきろくをけす");
            root.addView(clearLog);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("外部送信の設定")
                .setView(scroll)
                .setCancelable(!firstTime)
                .create();
        allow.setOnClickListener(v -> { dlg.dismiss(); onConsentChosen(true, firstTime); });
        deny.setOnClickListener(v -> { dlg.dismiss(); onConsentChosen(false, firstTime); });
        if (quit != null) {
            quit.setOnClickListener(v -> { dlg.dismiss(); finish(); });
        }
        if (forget != null) {
            forget.setOnClickListener(v -> { dlg.dismiss(); confirmForgetKnowledge(); });
        }
        if (clearLog != null) {
            clearLog.setOnClickListener(v -> { dlg.dismiss(); confirmClearConversation(); });
        }
        mConsentDialog = dlg;
        dlg.show();
    }

    /** 「会話のきろくをけす」の確認ダイアログ → 会話履歴クリア。 */
    private void confirmClearConversation() {
        if (mConsentDialog != null && mConsentDialog.isShowing()) mConsentDialog.dismiss();
        mConsentDialog = new AlertDialog.Builder(this)
                .setTitle("会話のきろくをけす")
                .setMessage("画面に表示されている会話のきろくをぜんぶ消します。よろしいですか？\n"
                        + "（ロボホンが「おぼえたこと」は消えません）")
                .setPositiveButton("けす", (d, w) -> clearConversation())
                .setNegativeButton("やめる", null)
                .show();
    }

    /** 会話履歴（保存ファイル＋画面表示）を消す。KB（おぼえたこと）は別管理なので触らない。 */
    private void clearConversation() {
        if (mStore != null) mStore.clear();
        if (mMessageContainer != null) mMessageContainer.removeAllViews();
        mLastRenderedDay = null;
        if (mEmptyView != null) mEmptyView.setVisibility(View.VISIBLE);
        // 次ターンはサーバへ渡す履歴窓も空になり、サーバ側メモリもリセットさせる。
        mPendingSeed = null;
        mFirstTurn = true;
        Log.v(TAG, "conversation history cleared by user");
    }

    /** 「おぼえたことをけす」の確認ダイアログ → KBクリア。 */
    private void confirmForgetKnowledge() {
        if (mConsentDialog != null && mConsentDialog.isShowing()) mConsentDialog.dismiss();
        mConsentDialog = new AlertDialog.Builder(this)
                .setTitle("おぼえたことをけす")
                .setMessage("ロボホンが会話からおぼえたこと（名前・好きなもの・さいきんの出来事など）を"
                        + "ぜんぶ消します。よろしいですか？\n（会話の履歴は消えません）")
                .setPositiveButton("けす", (d, w) -> {
                    if (mKnowledge != null) mKnowledge.clear(System.currentTimeMillis());
                    mKnowledgeJson = null;
                    mKnowledgeGen++; // 進行中ダイジェストの遅延保存で復活させない
                    Log.v(TAG, "knowledge cleared by user");
                })
                .setNegativeButton("やめる", null)
                .show();
    }

    /** 選択結果を保存して送信データへ即反映。初回フローではお礼を言って通常の会話を開始する。 */
    private void onConsentChosen(boolean allowed, boolean firstTime) {
        mConsent.setNamesAllowed(allowed);
        applyConsentToProfile();
        Log.v(TAG, "consent chosen: namesAllowed=" + allowed + " firstTime=" + firstTime);
        if (isFinishing()) return; // 頭ボタン等の終了処理と競合したら、選択の保存だけで終える
        if (firstTime && mAwaitingConsent) {
            mAwaitingConsent = false;
            mConsentAckPending = true; // 待受に入った時点で解除（speakNextOrFinish）
            speakSystem(allowed ? CONSENT_ACK_ALLOWED : CONSENT_ACK_DENIED); // お礼→キューが空になったら待受へ
            // 発話経路が死んでいた場合の保険：時間内に待受まで進まなければ強制的に進める
            mHandler.postDelayed(mConsentWatchdog, CONSENT_WATCHDOG_MS);
        }
    }

    /** システム発話（同意フロー等）：画面に表示して発話するが、会話履歴ファイルへは保存しない。 */
    private void speakSystem(String text) {
        addMessageView(ConversationStore.ROLE_ROBOT, text, System.currentTimeMillis());
        enqueueSpeechOnly(text);
        if (!mSpeaking) speakNextOrFinish();
    }

    /** 同意状態を送信データへ反映（不許可なら名前情報を落とす）。ロボホン名はペルソナ生成に必要なため送る。 */
    private void applyConsentToProfile() {
        if (mConsent != null && mConsent.isNamesAllowed()) {
            mOwnerName = mRawOwnerName;
            mContacts = mRawContacts;
        } else {
            mOwnerName = null;
            mContacts = new ArrayList<>();
        }
    }

    /** マスク対象の既知の名前（長い順）。1文字の呼び名は無関係な語を巻き込むため対象外。 */
    private List<String> maskTargetNames() {
        List<String> names = new ArrayList<>();
        if (mRawOwnerName != null && mRawOwnerName.length() >= 2) names.add(mRawOwnerName);
        for (RobohonProfile.Contact c : mRawContacts) {
            if (c.name != null && c.name.length() >= 2) names.add(c.name);
        }
        Collections.sort(names, (a, b) -> b.length() - a.length()); // 長い名前から（部分一致の崩れ防止）
        return names;
    }

    /** テキスト中の電話帳の名前を伏せ字にする（送信不許可時用）。 */
    private String maskNames(String text) {
        if (text == null) return null;
        for (String n : maskTargetNames()) text = text.replace(n, "おともだち");
        return text;
    }

    /**
     * 履歴中の電話帳の名前を伏せ字にする（送信不許可時用）。
     * 許可中に生成・保存された過去のロボ応答に名前が残っているため、フィールドを落とすだけでは
     * 履歴経由で名前が外部へ送られ続ける。変換に失敗した場合は履歴を送らない（安全側）。
     */
    private JSONArray maskContactNames(JSONArray history) {
        List<String> names = maskTargetNames();
        if (names.isEmpty()) return history;
        try {
            JSONArray out = new JSONArray();
            for (int i = 0; i < history.length(); i++) {
                JSONObject m = history.getJSONObject(i);
                String content = m.optString("content", "");
                for (String n : names) content = content.replace(n, "おともだち");
                JSONObject copy = new JSONObject();
                copy.put("role", m.optString("role"));
                copy.put("content", content);
                out.put(copy);
            }
            return out;
        } catch (Exception e) {
            Log.w(TAG, "maskContactNames failed", e);
            return new JSONArray();
        }
    }

    /** KB(profile[]/recent[].text)中の電話帳の名前を伏せ字にする。失敗時は空KBを送る（安全側）。 */
    private JSONObject maskKnowledge(JSONObject knowledge) {
        List<String> names = maskTargetNames();
        if (names.isEmpty() || knowledge == null) return knowledge;
        try {
            JSONObject out = new JSONObject();
            JSONArray profile = knowledge.optJSONArray("profile");
            JSONArray outProfile = new JSONArray();
            if (profile != null) {
                for (int i = 0; i < profile.length(); i++) {
                    outProfile.put(maskAll(profile.optString(i, ""), names));
                }
            }
            out.put("profile", outProfile);
            JSONArray recent = knowledge.optJSONArray("recent");
            JSONArray outRecent = new JSONArray();
            if (recent != null) {
                for (int i = 0; i < recent.length(); i++) {
                    JSONObject r = recent.optJSONObject(i);
                    if (r == null) continue;
                    JSONObject copy = new JSONObject();
                    copy.put("date", r.optString("date"));
                    copy.put("text", maskAll(r.optString("text", ""), names));
                    outRecent.put(copy);
                }
            }
            out.put("recent", outRecent);
            return out;
        } catch (Exception e) {
            Log.w(TAG, "maskKnowledge failed", e);
            try {
                JSONObject empty = new JSONObject();
                empty.put("profile", new JSONArray());
                empty.put("recent", new JSONArray());
                return empty;
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    private static String maskAll(String text, List<String> names) {
        for (String n : names) text = text.replace(n, "おともだち");
        return text;
    }

    /**
     * 1日1回、会話履歴からナレッジベースを更新する（差分更新）。
     * 起動時に前回から24時間経過していれば、既存KB＋前回以降の新規会話ログを /digest へ送り、
     * 返ってきた更新版KBを端末に保存する。バックグラウンドで実行し、会話フローは妨げない。
     */
    private void maybeRunDigest() {
        if (DIGEST_URL == null || mKnowledge == null) return;
        // 同意未選択、または名前送信が不許可のときはダイジェストしない。
        // ダイジェストはKBに実名などの個人的事実を蓄積する処理なので、外部に名前を送れない状況では
        // 動かさない（マスク版を書き戻して端末の正データを壊す事故＝実名の恒久消失も防げる）。
        // また同意選択が済むまで外部送信自体を始めない（同意ゲートの一貫性）。
        if (!mConsent.hasChoice() || !mConsent.isNamesAllowed()) return;
        if (mDigestRunning) return; // 二重onResume等での並行二重送信を防ぐ
        final long now = System.currentTimeMillis();
        if (!mKnowledge.isDigestDue(now)) return;

        final long since = mKnowledge.lastDigestAt();
        // 送信データに使う既存KBと世代番号をUIスレッドで確定（以後の clear と競合しても古い保存を捨てる）。
        final JSONObject existing = (mKnowledgeJson != null) ? mKnowledgeJson : mKnowledge.load();
        final int gen = mKnowledgeGen;
        mDigestRunning = true; // UIスレッドで即座に立てる（Threadのパース遅延に窓を作らない）

        // 履歴の全件パース（肥大しうる）はUIスレッドを避けてバックグラウンドで行う（ANR防止）。
        new Thread(() -> {
            JSONArray messages = mStore.messagesForDigestSince(since, DIGEST_MAX_MESSAGES);
            if (messages.length() == 0) {
                // 新規会話ゼロ：送らず間隔も進めない（次回に持ち越し）。フラグは戻す。
                runOnUiThread(() -> mDigestRunning = false);
                return;
            }
            mKnowledge.markAttempt(now); // 送るのでバックオフ起点を記録（成否に関わらず）

            JSONObject req = new JSONObject();
            try {
                req.put("sessionId", SESSION_ID);
                req.put("clientDate", new SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(new Date(now)));
                // 名前許可時のみここに到達するのでマスク不要（実データをそのまま送る）。
                req.put("knowledge", existing);
                req.put("messages", messages);
            } catch (Exception e) {
                Log.e(TAG, "digest json build error", e);
                runOnUiThread(() -> mDigestRunning = false);
                return;
            }

            Request.Builder rb = new Request.Builder()
                    .url(DIGEST_URL)
                    .post(RequestBody.create(req.toString(), JSON));
            if (RELAY_TOKEN != null && !RELAY_TOKEN.isEmpty()) {
                rb.header("X-Relay-Token", RELAY_TOKEN);
            }
            mHttp.newCall(rb.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "digest failed (keep existing knowledge): " + e);
                    runOnUiThread(() -> mDigestRunning = false);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String body = null;
                    try {
                        if (response.isSuccessful() && response.body() != null) body = response.body().string();
                    } catch (IOException e) {
                        Log.w(TAG, "digest read error", e);
                    } finally {
                        response.close();
                    }
                    final String json = body;
                    runOnUiThread(() -> {
                        mDigestRunning = false;
                        onDigestResponse(json, now, gen);
                    });
                }
            });
        }, "digest").start();
    }

    /** ダイジェスト応答を保存し、次回間隔の起点を更新（UIスレッド）。 */
    private void onDigestResponse(String json, long digestedAt, int gen) {
        if (isFinishing() || json == null) return;
        // 送信後に「おぼえたことをけす」が実行されていたら、古いKBを保存して消去を無かったことにしない。
        if (gen != mKnowledgeGen) {
            Log.v(TAG, "digest response discarded (knowledge was cleared)");
            return;
        }
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject knowledge = obj.optJSONObject("knowledge");
            if (knowledge != null) {
                mKnowledge.save(knowledge);
                mKnowledgeJson = knowledge; // 次ターンの /chat から新KBを使う
                mKnowledge.markDigested(digestedAt);
                JSONArray p = knowledge.optJSONArray("profile");
                JSONArray r = knowledge.optJSONArray("recent");
                Log.v(TAG, "digest saved: profile=" + (p != null ? p.length() : 0)
                        + " recent=" + (r != null ? r.length() : 0));
            }
        } catch (Exception e) {
            Log.w(TAG, "digest parse error (keep existing)", e);
        }
    }

    /** 終了コマンド判定。 */
    private static boolean isEndCommand(String text) {
        return text != null
                && text.matches(".*(終了|しゅうりょう|おしまい|ばいばい|バイバイ|さようなら|またね|とじて|閉じて).*");
    }

    /** 音量を上げる指示（「音量上げて」「もっと大きな声で」「声大きくして」等）。 */
    private static final Pattern VOL_UP = Pattern.compile(
            "(音量|ボリューム|ボリウム|おんりょう).{0,6}(上げ|あげ|大きく|おおきく|アップ)"
                    + "|(声|こえ)(?!が).{0,4}(大きく|おおきく)"        // 「声を大きく」等（「声が大きくて」は除外）
                    + "|もっと.{0,4}(大きな|大きい|おおきい)(声|こえ)"  // 「もっと大きな声で」
                    + "|音量アップ");
    /** 音量を下げる指示（「音量下げて」「もっと小さな声で」「声小さくして」等）。 */
    private static final Pattern VOL_DOWN = Pattern.compile(
            "(音量|ボリューム|ボリウム|おんりょう).{0,6}(下げ|さげ|小さく|ちいさく|ダウン)"
                    + "|(声|こえ)(?!が).{0,4}(小さく|ちいさく)"        // 「声を小さく」等（「声が小さくて」は除外）
                    + "|もっと.{0,4}(小さな|小さい|ちいさい)(声|こえ)"  // 「もっと小さな声で」
                    + "|音量ダウン");
    /** 否定・現状維持（「下げないで」「そのままで」等）。音量コマンド判定を打ち消す。 */
    private static final Pattern VOL_NEG = Pattern.compile(
            "(ないで|しないで|なくてい|しなくてい|そのままで|変えないで|かえないで)");

    /**
     * ロボホンの音声(TTS)が鳴るストリーム。実機(SR-S05BJ/Android8.1)で再生中トラックの
     * stream type を確認した結果 STREAM_TTS(=9) だった（STREAM_MUSIC ではない）。
     * AOSP/SHARPの隠しストリームのため定数が公開SDKに無く、整数値で指定する。
     */
    private static final int STREAM_TTS = 9;
    /** 1回の上げ下げ幅。RoBoHoNの「音量レベル」1段ぶん（表4-3: レベル間隔≒2）に相当。 */
    private static final int VOL_STEP = 2;

    /**
     * 音量コマンドをローカル処理する。処理したら true（サーバへ送らない）。
     * <p>ロボホンの声は STREAM_TTS で鳴る。歌などのメディアと一体感を出すため STREAM_MUSIC も追随させる。
     * 0(無音)にはしない（下げても最小1で止める）。
     */
    private boolean maybeHandleVolume(String text) {
        if (text == null) return false;
        if (VOL_NEG.matcher(text).find()) return false; // 「下げないで」「そのままで」等は通常会話へ
        boolean up = VOL_UP.matcher(text).find();
        boolean down = VOL_DOWN.matcher(text).find();
        if (up == down) return false; // 非該当、または上げ下げ両方該当（曖昧）は通常会話へ回す

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        // 応答待ち・フィラーの取消（このターンはサーバへ行かないため念のため畳む）。
        stopWaiting();
        int max = am.getStreamMaxVolume(STREAM_TTS);
        int cur = am.getStreamVolume(STREAM_TTS);
        // 下げる場合は1未満にしない（既に0/1なら上げ直さない）。上げる場合は最大までクランプ。
        int next = up ? Math.min(max, cur + VOL_STEP) : (cur <= 1 ? cur : Math.max(1, cur - VOL_STEP));
        boolean changed = next != cur;
        if (changed) {
            try {
                am.setStreamVolume(STREAM_TTS, next, 0); // UIオーバーレイは出さない(0)
                // 歌などメディア音量も同じ値に追随（範囲はTTSと同じ0..15。念のためクランプ）。
                int musicMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, Math.min(musicMax, next)), 0);
            } catch (SecurityException e) {
                // マナーモード等で変更不可のことがある。会話は止めず正直に伝える。
                Log.w(TAG, "setStreamVolume failed: " + e);
                speakSystemLine("ごめんね、今は音の大きさを変えられないみたい。");
                return true;
            }
        }
        String reply;
        if (up) {
            reply = changed
                    ? "はーい、声を大きくしたよ！"
                    : "もうこれ以上は大きくできないよ。今がいちばん大きい声なんだ。";
        } else {
            reply = changed
                    ? "はーい、声を小さくしたよ。"
                    : "これより小さくすると聞こえなくなっちゃうから、このくらいにしておくね。";
        }
        speakSystemLine(reply);
        return true;
    }

    /** システム的な一言を、キューを空にして即発話（会話履歴には残さない）。発話後は待受へ戻る。 */
    private void speakSystemLine(String line) {
        mUtteranceQueue.clear();
        enqueueSpeechOnly(line);
        speakNextOrFinish();
    }

    /** 電話帳の登録者を {name, relation} のJSON配列へ。 */
    private JSONArray buildContactsJson() {
        JSONArray arr = new JSONArray();
        if (mContacts == null) return arr;
        try {
            for (RobohonProfile.Contact c : mContacts) {
                JSONObject o = new JSONObject();
                o.put("name", c.name);
                if (c.relation != null) o.put("relation", c.relation);
                arr.put(o);
            }
        } catch (Exception ignore) {
        }
        return arr;
    }

    /** 履歴に1メッセージ追加（端末へ保存＋画面へ反映）。 */
    private void addMessage(String role, String text) {
        long now = System.currentTimeMillis();
        mStore.append(role, text);
        addMessageView(role, text, now);
    }

    /** メッセージの吹き出し（話者ラベル＋時刻＋角丸バブル）を追加して最下部へスクロール（UIスレッド）。 */
    private void addMessageView(String role, String text, long t) {
        runOnUiThread(() -> {
            if (mMessageContainer == null) return;
            // 日付が変わったら日付セパレータを挿入（チャットアプリ風）
            String dayKey = mDayKeyFmt.format(new Date(t));
            if (!dayKey.equals(mLastRenderedDay)) {
                addDateSeparator(t);
                mLastRenderedDay = dayKey;
            }
            boolean isUser = ConversationStore.ROLE_USER.equals(role);
            int bubbleBg = isUser ? 0xFF1E88E5 : 0xFFECEFF1;   // 青 / 淡灰
            int textColor = isUser ? 0xFFFFFFFF : 0xFF1A1A1A;  // 白 / 濃色
            String speaker = isUser ? "あなた" : mRobotName;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(dp(8), dp(6), dp(8), dp(6));
            rowLp.gravity = isUser ? Gravity.END : Gravity.START;
            row.setLayoutParams(rowLp);

            TextView label = new TextView(this);
            label.setText(speaker + "  " + mTimeFmt.format(new Date(t)));
            label.setTextSize(12);
            label.setTextColor(0xFF888888);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.gravity = isUser ? Gravity.END : Gravity.START;
            labelLp.setMargins(dp(4), 0, dp(4), dp(2));
            label.setLayoutParams(labelLp);

            TextView bubble = new TextView(this);
            bubble.setText(text);
            bubble.setTextSize(22);
            bubble.setTextColor(textColor);
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
            // 幅は内容に合わせて横に伸ばす（明示しないと縦並びLinearLayoutの既定=MATCH_PARENTで
            // 行幅(=ラベル幅)まで縮み、1文字ずつ折り返して縦書きのように見えてしまう）。
            LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bubble.setLayoutParams(bubbleLp);
            // 長文は画面幅の約8割で折り返す（横書きを保ちつつ読みやすく）。
            bubble.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(bubbleBg);
            bg.setCornerRadius(dp(16));
            bubble.setBackground(bg);

            row.addView(label);
            row.addView(bubble);
            mMessageContainer.addView(row);
            if (mEmptyView != null) mEmptyView.setVisibility(View.GONE);
            scrollToBottom();
        });
    }

    /** 日付セパレータ（中央寄せの「yyyy年M月d日 (E)」）を追加。UIスレッドから呼ぶ。 */
    private void addDateSeparator(long t) {
        TextView sep = new TextView(this);
        sep.setText(mDaySepFmt.format(new Date(t)));
        sep.setTextSize(12);
        sep.setTextColor(0xFF888888);
        sep.setPadding(dp(8), dp(10), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, dp(6), 0, dp(2));
        sep.setLayoutParams(lp);
        mMessageContainer.addView(sep);
    }

    /** 保存済み会話を画面へ復元（直近 RENDER_LIMIT 件のみ。起動時の重さ/OOMを回避）。 */
    private static final int RENDER_LIMIT = 200;
    private void renderHistory() {
        List<ConversationStore.Message> all = mStore.loadAll();
        int from = Math.max(0, all.size() - RENDER_LIMIT);
        for (int i = from; i < all.size(); i++) {
            addMessageView(all.get(i).role, all.get(i).text, all.get(i).t);
        }
        if (mEmptyView != null) mEmptyView.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void scrollToBottom() {
        if (mScrollView != null) mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    /** launch_app の論理名 → ロボホン純正アプリをランチャーIntentで起動。起動不可なら断って待受へ。 */
    private void launchApp(String app) {
        String pkg = (app == null) ? null : APP_PACKAGES.get(app);
        Intent intent = (pkg != null) ? getPackageManager().getLaunchIntentForPackage(pkg) : null;
        if (intent == null) {
            Log.w(TAG, "cannot launch app=" + app + " pkg=" + pkg);
            enqueueRobot("ごめんね、それはまだ開けないみたい。");
            speakNextOrFinish();
            return;
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent); // 別アプリ起動 → onPauseでこのアプリは終了
        } catch (Throwable e) {
            Log.e(TAG, "launch failed: " + app, e);
            enqueueRobot("ごめんね、うまく開けなかったみたい。");
            speakNextOrFinish();
        }
    }

    private class HomeEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Home button pressed");
            finish();
        }
    }

    private void setupTitleBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
    }
}
