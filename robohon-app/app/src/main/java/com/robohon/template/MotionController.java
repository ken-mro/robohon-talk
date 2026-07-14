package com.robohon.template;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.co.sharp.android.rb.action.ActionUtil;
import jp.co.sharp.android.rb.camera.ShootMediaUtil;
import jp.co.sharp.android.rb.rbdance.DanceUtil;
import jp.co.sharp.android.rb.song.SongUtil;

/**
 * 基本動作（歌う/踊る/アクション=歩く等）を sendBroadcast で実行する。
 * これらは別アプリの startActivity と違い前面を奪わない＝この会話アプリは終了しない。
 * 実行完了は結果ブロードキャストで受け取り、{@link Listener#onMotionDone(boolean, String, String)} で通知する。
 * 端末が当該フレームワーク非対応でも落ちないよう、全て Throwable を握る。
 */
public final class MotionController {
    private static final String TAG = "MotionController";

    private static final String PKG = "com.robohon.template";
    private static final String RESULT_SONG = PKG + ".action.RESULT_SONG";
    private static final String RESULT_DANCE = PKG + ".action.RESULT_DANCE";
    private static final String RESULT_ACTION = PKG + ".action.RESULT_ACTION";
    private static final String RESULT_PHOTO = PKG + ".action.RESULT_PHOTO";

    public interface Listener {
        /**
         * 動作完了通知。ok=true は正常終了、false は中断/失敗（USB接続中など）。
         *
         * @param kind 実行した種別（sing/dance/action/photo）。不明なら null。
         * @param name 実際に演じた曲/ダンス/動作の名前（結果IDを一覧で解決。おまかせでも実名）。
         *             解決できなければ要求時の指定語、いずれも無ければ null。
         */
        void onMotionDone(boolean ok, String kind, String name);
    }

    private final Context mApp;
    private final Listener mListener;
    private BroadcastReceiver mReceiver;

    // id -> 名前（getInfo の結果）。名前一致で query から id を引く。
    // 背景スレッドで代入・メインスレッドで参照するため volatile（可視性確保）。
    private volatile LinkedHashMap<Integer, String> mSongs = new LinkedHashMap<>();
    private volatile LinkedHashMap<Integer, String> mDances = new LinkedHashMap<>();
    private volatile LinkedHashMap<Integer, String> mActions = new LinkedHashMap<>();

    // 直近の実行で「指定」した名前（ASSIGN時のみ非null）。結果IDを引けないときのフォールバック用。
    // おまかせ(NORMAL)時は null＝未再生の指定語を誤って記録しない。
    private volatile String mPendingAssignedName;

    public MotionController(Context ctx, Listener listener) {
        mApp = ctx.getApplicationContext();
        mListener = listener;
        registerReceiver();
        loadInfoAsync();
    }

    /** 一覧をバックグラウンドで取得（端末提供API。失敗時は空のまま）。 */
    private void loadInfoAsync() {
        new Thread(() -> {
            try {
                mSongs = safeInfo(SongUtil.getInfo(mApp));
            } catch (Throwable t) {
                Log.w(TAG, "song getInfo failed: " + t);
            }
            try {
                mDances = safeInfo(DanceUtil.getInfo(mApp));
            } catch (Throwable t) {
                Log.w(TAG, "dance getInfo failed: " + t);
            }
            try {
                mActions = safeInfo(ActionUtil.getInfo(mApp));
            } catch (Throwable t) {
                Log.w(TAG, "action getInfo failed: " + t);
            }
            Log.v(TAG, "motion lists: songs=" + mSongs.size() + " dances=" + mDances.size()
                    + " actions=" + mActions.size());
            // TODO: 実機での全アクション名の確認用（adb logcat -s MotionController:I で参照）。確認が済んだら削除する。
            Log.i(TAG, "actions=" + mActions.values());
        }, "motion-getinfo").start();
    }

    private static LinkedHashMap<Integer, String> safeInfo(LinkedHashMap<Integer, String> m) {
        return m != null ? m : new LinkedHashMap<>();
    }

    /**
     * 動作を実行する。実行をリクエストできたら true（結果は onMotionDone で通知）。
     * 該当が無い/非対応で実行できなければ false（呼び出し側は即座に会話へ戻すこと）。
     */
    public boolean execute(String kind, String query) {
        mPendingAssignedName = null; // 既定はおまかせ扱い（指定が一致したときだけ下で設定）
        try {
            if ("sing".equals(kind)) {
                LinkedHashMap<Integer, String> songs = mSongs;
                int id = findId(songs, query);
                mPendingAssignedName = (id > 0) ? songs.get(id) : null;
                Intent i = new Intent(SongUtil.ACTION_REQUEST_SONG);
                i.putExtra(SongUtil.EXTRA_REPLYTO_ACTION, RESULT_SONG);
                i.putExtra(SongUtil.EXTRA_REPLYTO_PKG, mApp.getPackageName());
                if (id > 0) {
                    i.putExtra(SongUtil.EXTRA_TYPE, SongUtil.EXTRA_TYPE_ASSIGN);
                    i.putExtra(SongUtil.EXTRA_REQUEST_ID, id);
                } else {
                    i.putExtra(SongUtil.EXTRA_TYPE, SongUtil.EXTRA_TYPE_NORMAL);
                }
                mApp.sendBroadcast(i);
                return true;
            } else if ("dance".equals(kind)) {
                LinkedHashMap<Integer, String> dances = mDances;
                int id = findId(dances, query);
                mPendingAssignedName = (id > 0) ? dances.get(id) : null;
                Intent i = new Intent(DanceUtil.ACTION_REQUEST_DANCE);
                i.putExtra(DanceUtil.EXTRA_REPLYTO_ACTION, RESULT_DANCE);
                i.putExtra(DanceUtil.EXTRA_REPLYTO_PKG, mApp.getPackageName());
                if (id > 0) {
                    i.putExtra(DanceUtil.EXTRA_TYPE, DanceUtil.EXTRA_TYPE_ASSIGN);
                    i.putExtra(DanceUtil.EXTRA_REQUEST_ID, id);
                } else {
                    i.putExtra(DanceUtil.EXTRA_TYPE, DanceUtil.EXTRA_TYPE_NORMAL);
                }
                mApp.sendBroadcast(i);
                return true;
            } else if ("action".equals(kind)) {
                LinkedHashMap<Integer, String> actions = mActions;
                int id = findId(actions, query);
                if (id <= 0) {
                    Log.w(TAG, "no action matched query=" + query);
                    return false; // 該当アクション無し → 実行できない
                }
                mPendingAssignedName = actions.get(id); // action は常に指定id
                Intent i = new Intent(ActionUtil.ACTION_REQUEST_ACTION);
                i.putExtra(ActionUtil.EXTRA_REPLYTO_ACTION, RESULT_ACTION);
                i.putExtra(ActionUtil.EXTRA_REPLYTO_PKG, mApp.getPackageName());
                i.putExtra(ActionUtil.EXTRA_REQUEST_ID, id);
                mApp.sendBroadcast(i);
                return true;
            } else if ("photo".equals(kind)) {
                Intent i = new Intent(ShootMediaUtil.ACTION_SHOOT_IMAGE);
                i.setPackage(ShootMediaUtil.PACKAGE);
                i.putExtra(ShootMediaUtil.EXTRA_FACE_DETECTION, false);
                i.putExtra(ShootMediaUtil.EXTRA_REPLYTO_ACTION, RESULT_PHOTO);
                i.putExtra(ShootMediaUtil.EXTRA_REPLYTO_PKG, mApp.getPackageName());
                mApp.sendBroadcast(i);
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "execute failed kind=" + kind + ": " + t);
        }
        return false;
    }

    /** 端末に入っている歌の名前一覧（サーバ経由でLLMへ渡し、指定の的中率を上げる）。 */
    public List<String> getSongNames() {
        return names(mSongs);
    }

    /** 端末に入っているダンスの名前一覧。 */
    public List<String> getDanceNames() {
        return names(mDances);
    }

    /** 端末でできるアクション（立ち上がる・歩く等）の名前一覧。LLMが実在の名前で指定できるように渡す。 */
    public List<String> getActionNames() {
        return names(mActions);
    }

    private static List<String> names(LinkedHashMap<Integer, String> map) {
        List<String> out = new ArrayList<>();
        if (map == null) return out;
        for (String v : map.values()) {
            if (v != null && !v.trim().isEmpty()) out.add(v.trim());
        }
        return out;
    }

    /**
     * 名前一致で id を引く。query 空なら -1（＝おまかせ）。
     * LLM には一覧の正式名を“そのまま”指定させるため、まず完全一致を全走査してから、
     * 言い回し違いの保険として双方向 contains の緩い一致に落とす（先勝ちの部分一致だけだと、
     * ある名前が別名の部分文字列のとき正式名指定でも別エントリに化けるため）。
     */
    private static int findId(LinkedHashMap<Integer, String> map, String query) {
        if (query == null) return -1;
        String q = query.trim();
        if (q.isEmpty() || map == null || map.isEmpty()) return -1;
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            String name = e.getValue();
            if (name != null && name.trim().equals(q)) return e.getKey();
        }
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            String name = e.getValue();
            if (name == null) continue;
            if (name.contains(q) || q.contains(name)) return e.getKey();
        }
        return -1;
    }

    private void registerReceiver() {
        try {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean ok = false;
                    String kind = null;
                    String name = null;
                    try {
                        String act = intent.getAction();
                        if (RESULT_SONG.equals(act)) {
                            kind = "sing";
                            ok = intent.getIntExtra(SongUtil.EXTRA_RESULT_CODE, SongUtil.RESULT_CANCELED)
                                    == SongUtil.RESULT_OK;
                            name = resolveName(mSongs, intent.getIntExtra(SongUtil.EXTRA_RESULT_ID, -1));
                        } else if (RESULT_DANCE.equals(act)) {
                            kind = "dance";
                            ok = intent.getIntExtra(DanceUtil.EXTRA_RESULT_CODE, DanceUtil.RESULT_CANCELED)
                                    == DanceUtil.RESULT_OK;
                            name = resolveName(mDances, intent.getIntExtra(DanceUtil.EXTRA_RESULT_ID, -1));
                        } else if (RESULT_ACTION.equals(act)) {
                            kind = "action";
                            ok = intent.getIntExtra(ActionUtil.EXTRA_RESULT_CODE, ActionUtil.RESULT_CANCELED)
                                    == ActionUtil.RESULT_OK;
                            name = resolveName(mActions, intent.getIntExtra(ActionUtil.EXTRA_RESULT_ID, -1));
                        } else if (RESULT_PHOTO.equals(act)) {
                            kind = "photo";
                            ok = intent.getIntExtra(ShootMediaUtil.EXTRA_RESULT_CODE, ShootMediaUtil.RESULT_CANCELED)
                                    == ShootMediaUtil.RESULT_OK;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "result parse failed: " + t);
                    }
                    if (mListener != null) mListener.onMotionDone(ok, kind, name);
                }

                /**
                 * 実際に演じた名前を返す。結果ID→一覧で解決（おまかせでも実名）。引けないときは、
                 * ASSIGN指定時のみ指定名にフォールバック。おまかせで未解決なら null（誤記録しない）。
                 */
                private String resolveName(LinkedHashMap<Integer, String> map, int resultId) {
                    if (map != null && resultId > 0) {
                        String n = map.get(resultId);
                        if (n != null && !n.trim().isEmpty()) return n.trim();
                    }
                    return mPendingAssignedName; // ASSIGN時のみ非null。NORMALで未解決なら null
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(RESULT_SONG);
            f.addAction(RESULT_DANCE);
            f.addAction(RESULT_ACTION);
            f.addAction(RESULT_PHOTO);
            mApp.registerReceiver(mReceiver, f);
        } catch (Throwable t) {
            Log.w(TAG, "registerReceiver failed: " + t);
        }
    }

    public void release() {
        try {
            if (mReceiver != null) mApp.unregisterReceiver(mReceiver);
        } catch (Throwable ignore) {
        }
        mReceiver = null;
    }
}
