package com.robohon.template;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日記の端末保存（JSON Lines）。1行 = 1エントリ: {"t":epochMillis,"date":"yyyy-MM-dd","text":"..."}。
 * 会話から生成した日記本文を追記する（中身が必ず端末に残る）。
 */
public final class DiaryStore {
    private static final String TAG = "DiaryStore";
    private static final String FILE_NAME = "diary.jsonl";

    private final File mFile;

    public DiaryStore(Context ctx) {
        mFile = new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** 日記1件を追記（保存失敗しても会話は継続させる）。 */
    public synchronized void append(String text) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        try {
            long now = System.currentTimeMillis();
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(new Date(now));
            JSONObject o = new JSONObject();
            o.put("t", now);
            o.put("date", date);
            o.put("text", text);
            try (FileOutputStream out = new FileOutputStream(mFile, /*append=*/true)) {
                out.write((o.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "append failed: " + e);
        }
    }
}
