package com.robohon.template;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * ナレッジベース（おぼえていること）の端末保存。
 * <p>
 * KBは会話から抽出した事実の要約で、{@code {"profile":[...], "recent":[{"date","text"}...]}} のJSON。
 * サーバには保存せず端末が正データを持ち、/chat には毎回同梱、/digest で日次更新して上書きする。
 * 前回ダイジェスト実行時刻も持ち、24時間の間隔判定に使う。
 */
public final class KnowledgeStore {
    private static final String TAG = "KnowledgeStore";
    private static final String FILE_NAME = "knowledge.json";
    private static final String PREFS = "knowledge";
    private static final String KEY_LAST_DIGEST_AT = "last_digest_at";
    private static final String KEY_LAST_ATTEMPT_AT = "last_attempt_at";

    /** ダイジェストの最小間隔（24時間）。 */
    public static final long DIGEST_INTERVAL_MS = 24L * 60 * 60 * 1000;
    /** 送信を試みてから次に試みるまでの最小間隔（失敗が続いても毎起動で全履歴を再送しない）。 */
    public static final long ATTEMPT_BACKOFF_MS = 3L * 60 * 60 * 1000;

    private final File mFile;
    private final SharedPreferences mPrefs;

    public KnowledgeStore(Context ctx) {
        mFile = new File(ctx.getFilesDir(), FILE_NAME);
        mPrefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 保存済みKBを返す。無ければ空KB（profile/recent が空配列）。壊れていても空KBを返す。 */
    public synchronized JSONObject load() {
        if (mFile.isFile()) {
            try (FileInputStream in = new FileInputStream(mFile)) {
                byte[] buf = new byte[(int) mFile.length()];
                int off = 0, n;
                while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
                return new JSONObject(new String(buf, 0, off, StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.w(TAG, "load failed: " + e);
            }
        }
        return emptyKnowledge();
    }

    /** KBを上書き保存する。null は無視。 */
    public synchronized void save(JSONObject knowledge) {
        if (knowledge == null) return;
        try (FileOutputStream out = new FileOutputStream(mFile)) {
            out.write(knowledge.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "save failed: " + e);
        }
    }

    /**
     * KBを消す（「おぼえたことをけす」）。
     * ダイジェスト起点(lastDigestAt)は0に戻さず現在時刻にする。0にすると次回起動で全履歴から
     * KBがまるごと再構築され「忘れて」が定着しないため、以後の新しい会話だけを対象にする。
     */
    public synchronized void clear(long now) {
        if (mFile.exists() && !mFile.delete()) {
            // 消せない場合は空KBで上書きして中身を消す
            save(emptyKnowledge());
        }
        mPrefs.edit()
                .putLong(KEY_LAST_DIGEST_AT, now)
                .remove(KEY_LAST_ATTEMPT_AT)
                .apply();
    }

    /** 前回ダイジェスト実行時刻（epoch millis）。未実行なら0。 */
    public synchronized long lastDigestAt() {
        return mPrefs.getLong(KEY_LAST_DIGEST_AT, 0L);
    }

    /**
     * ダイジェストが必要か。前回成功から24時間以上、かつ前回“試行”から3時間以上経っていること。
     * 試行間隔の条件で、サーバ障害などで成功しない間に毎起動で全履歴を再送するのを防ぐ。
     */
    public synchronized boolean isDigestDue(long now) {
        return now - lastDigestAt() >= DIGEST_INTERVAL_MS
                && now - mPrefs.getLong(KEY_LAST_ATTEMPT_AT, 0L) >= ATTEMPT_BACKOFF_MS;
    }

    /** 送信を試みた時刻を記録する（成否に関わらず。バックオフの起点）。 */
    public synchronized void markAttempt(long at) {
        mPrefs.edit().putLong(KEY_LAST_ATTEMPT_AT, at).apply();
    }

    /** ダイジェスト成功時刻を記録する。 */
    public synchronized void markDigested(long at) {
        mPrefs.edit().putLong(KEY_LAST_DIGEST_AT, at).apply();
    }

    private static JSONObject emptyKnowledge() {
        try {
            JSONObject o = new JSONObject();
            o.put("profile", new org.json.JSONArray());
            o.put("recent", new org.json.JSONArray());
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
