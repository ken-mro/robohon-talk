package com.robohon.template;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

import jp.co.sharp.android.rb.action.ActionUtil;
import jp.co.sharp.android.rb.rbdance.DanceUtil;
import jp.co.sharp.android.rb.song.SongUtil;

/**
 * 基本動作（歌う/踊る/アクション=歩く等）を sendBroadcast で実行する。
 * これらは別アプリの startActivity と違い前面を奪わない＝この会話アプリは終了しない。
 * 実行完了は結果ブロードキャストで受け取り、{@link Listener#onMotionDone(boolean)} で通知する。
 * 端末が当該フレームワーク非対応でも落ちないよう、全て Throwable を握る。
 */
public final class MotionController {
    private static final String TAG = "MotionController";

    private static final String PKG = "com.robohon.template";
    private static final String RESULT_SONG = PKG + ".action.RESULT_SONG";
    private static final String RESULT_DANCE = PKG + ".action.RESULT_DANCE";
    private static final String RESULT_ACTION = PKG + ".action.RESULT_ACTION";

    public interface Listener {
        /** 動作完了通知。ok=true は正常終了、false は中断/失敗（USB接続中など）。 */
        void onMotionDone(boolean ok);
    }

    private final Context mApp;
    private final Listener mListener;
    private BroadcastReceiver mReceiver;

    // id -> 名前（getInfo の結果）。名前一致で query から id を引く。
    private LinkedHashMap<Integer, String> mSongs = new LinkedHashMap<>();
    private LinkedHashMap<Integer, String> mDances = new LinkedHashMap<>();
    private LinkedHashMap<Integer, String> mActions = new LinkedHashMap<>();

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
        try {
            if ("sing".equals(kind)) {
                int id = findId(mSongs, query);
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
                int id = findId(mDances, query);
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
                int id = findId(mActions, query);
                if (id <= 0) {
                    Log.w(TAG, "no action matched query=" + query);
                    return false; // 該当アクション無し → 実行できない
                }
                Intent i = new Intent(ActionUtil.ACTION_REQUEST_ACTION);
                i.putExtra(ActionUtil.EXTRA_REPLYTO_ACTION, RESULT_ACTION);
                i.putExtra(ActionUtil.EXTRA_REPLYTO_PKG, mApp.getPackageName());
                i.putExtra(ActionUtil.EXTRA_REQUEST_ID, id);
                mApp.sendBroadcast(i);
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "execute failed kind=" + kind + ": " + t);
        }
        return false;
    }

    /** 名前一致で id を引く。query 空なら -1（＝おまかせ）。双方向 contains で緩く一致。 */
    private static int findId(LinkedHashMap<Integer, String> map, String query) {
        if (query == null) return -1;
        String q = query.trim();
        if (q.isEmpty() || map == null || map.isEmpty()) return -1;
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
                    try {
                        String act = intent.getAction();
                        if (RESULT_SONG.equals(act)) {
                            ok = intent.getIntExtra(SongUtil.EXTRA_RESULT_CODE, SongUtil.RESULT_CANCELED)
                                    == SongUtil.RESULT_OK;
                        } else if (RESULT_DANCE.equals(act)) {
                            ok = intent.getIntExtra(DanceUtil.EXTRA_RESULT_CODE, DanceUtil.RESULT_CANCELED)
                                    == DanceUtil.RESULT_OK;
                        } else if (RESULT_ACTION.equals(act)) {
                            ok = intent.getIntExtra(ActionUtil.EXTRA_RESULT_CODE, ActionUtil.RESULT_CANCELED)
                                    == ActionUtil.RESULT_OK;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "result parse failed: " + t);
                    }
                    if (mListener != null) mListener.onMotionDone(ok);
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(RESULT_SONG);
            f.addAction(RESULT_DANCE);
            f.addAction(RESULT_ACTION);
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
