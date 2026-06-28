package com.robohon.template;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private static final String RELAY_TOKEN = BuildConfig.RELAY_TOKEN;
    private static final String SESSION_ID = "robohon-1";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 待ち時間フィラー（応答が遅いときだけ段階的に発話。速い応答では発火前に取消）。 */
    private static final String[] FILLER_TEXTS = {"えーっと", "ふむふむ", "ちょっと調べてみるね"};
    // 認識完了後の沈黙をすぐ埋める。1つ目は早め(0.7s)、以降は応答が長引いたとき段階的に。
    // 速い応答(<0.7s)はフィラー発火前に取り消されるので無音のまま即応答する。
    private static final long[] FILLER_DELAYS = {700, 2500, 4500};
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

    /** 会話履歴（端末に全件保存）と表示ビュー、日記の保存。 */
    private ConversationStore mStore;
    private DiaryStore mDiary;
    private LinearLayout mMessageContainer;
    private ScrollView mScrollView;
    private TextView mEmptyView;
    /** 起動後の初回リクエストで、保存済み履歴の直近窓をサーバへ渡し文脈を復元する。 */
    private JSONArray mPendingSeed = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);
        setupTitleBar();

        mHandler = new Handler(Looper.getMainLooper());

        // 会話履歴: 端末から読み込んで画面に復元
        mStore = new ConversationStore(this);
        mDiary = new DiaryStore(this);
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
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);

        // 名前・電話帳を取得（取得不可なら既定）。中継サーバへ毎リクエスト同梱する。
        mRobotName = RobohonProfile.getRobotName(this);
        mOwnerName = RobohonProfile.getOwnerName(this);
        mContacts = RobohonProfile.getAllContacts(this);
        Log.v(TAG, "names: robot=" + mRobotName + " owner=" + mOwnerName + " contacts=" + mContacts.size());

        // 顔認識による話し相手の自動特定は不採用（ユーザー決定）。
        // 理由: 顔検出は jp.co.sharp.android.rb.camera/.ui.FaceDetectionExternalCameraActivity という
        // 全画面カメラActivityを前面に立ち上げるため、この会話アプリが onPause→finish で即終了してしまい
        // 会話を継続できない。電話帳の登録者把握とオーナー/登録名での呼びかけは有効。

        // 起動あいさつ（名前入り）を ${pkg:speech} で渡し、greet→listen で待受開始。
        // あいさつは画面に出すだけで履歴ファイルには保存しない（毎起動の保存で履歴/seedが
        // あいさつだらけになり文脈が埋まるのを防ぐ）。
        mCurrentUtterance = "はーい、" + mRobotName + "だよー。なになにー？";
        addMessageView(ConversationStore.ROLE_ROBOT, mCurrentUtterance);
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_GREET);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
        mWaitingForRelay = false;
        mSpeaking = false;
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
            default:
                break;
        }
    }

    /** 認識テキスト受領 → 中継サーバへ問い合わせ。 */
    private void onUserSaid(String text) {
        Log.v(TAG, "user said: " + text);
        // 認識失敗（空 or 全角VOICEPFエラー文字列）は聞き返して待受へ
        if (text == null || text.trim().isEmpty() || text.startsWith("ＶＯＩＣＥ")) {
            enqueueRobotExclusive("ごめんね、もう一回言ってくれる？");
            mPendingLaunchApp = null;
            speakNextOrFinish();
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
            enqueueRobotExclusive("ばいばい！またねー！");
            speakNextOrFinish();
            return;
        }
        postToRelay(text);
    }

    private void postToRelay(String userText) {
        JSONObject req = new JSONObject();
        try {
            req.put("sessionId", SESSION_ID);
            req.put("text", userText);
            req.put("reset", mFirstTurn);
            req.put("robotName", mRobotName);
            if (mOwnerName != null) req.put("ownerName", mOwnerName);
            JSONArray contacts = buildContactsJson();
            if (contacts.length() > 0) req.put("contacts", contacts);
            // 毎ターン履歴窓を同梱（ステートレス）。サーバはこれを文脈の真実として使う。
            if (mPendingSeed != null && mPendingSeed.length() > 0) {
                req.put("history", mPendingSeed);
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
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parse response error: " + json, e);
            enqueueRobot("ごめんね、うまく聞き取れなかったみたい。");
        }
        if (mUtteranceQueue.isEmpty() && mPendingLaunchApp != null) {
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
            mSpeaking = true;
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SAY);
            return;
        }
        // キューが空
        if (mWaitingForRelay) return;          // サーバ応答待ち：待機（フィラーは別途）
        if (mPendingLaunchApp != null) {       // アプリ起動を終了より優先（→ onPauseで本アプリ終了）
            String app = mPendingLaunchApp;
            mPendingLaunchApp = null;
            launchApp(app);
            return;
        }
        if (mFinishAfterSpeak) {               // 終了コマンド / done
            finish();
            return;
        }
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_LISTEN);
    }

    /** 待ち時間フィラーを1つ発話（履歴には残さない）。 */
    private void speakFiller(String text) {
        mCurrentUtterance = text;
        mSpeaking = true;
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SAY);
    }

    private void scheduleFillers() {
        for (int i = 0; i < FILLER_TEXTS.length; i++) {
            final String filler = FILLER_TEXTS[i];
            mHandler.postDelayed(() -> {
                if (mWaitingForRelay && !mSpeaking) speakFiller(filler);
            }, FILLER_DELAYS[i]);
        }
    }

    private void stopWaiting() {
        mWaitingForRelay = false;
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
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

    /** 終了コマンド判定。 */
    private static boolean isEndCommand(String text) {
        return text != null
                && text.matches(".*(終了|しゅうりょう|おしまい|ばいばい|バイバイ|さようなら|またね|とじて|閉じて).*");
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
        mStore.append(role, text);
        addMessageView(role, text);
    }

    /** メッセージの吹き出し（話者ラベル＋角丸バブル）を追加して最下部へスクロール（UIスレッド）。 */
    private void addMessageView(String role, String text) {
        runOnUiThread(() -> {
            if (mMessageContainer == null) return;
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
            label.setText(speaker);
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

    /** 保存済み会話を画面へ復元（直近 RENDER_LIMIT 件のみ。起動時の重さ/OOMを回避）。 */
    private static final int RENDER_LIMIT = 200;
    private void renderHistory() {
        List<ConversationStore.Message> all = mStore.loadAll();
        int from = Math.max(0, all.size() - RENDER_LIMIT);
        for (int i = from; i < all.size(); i++) addMessageView(all.get(i).role, all.get(i).text);
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
