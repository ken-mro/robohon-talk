package com.robohon.template;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 会話履歴の端末保存（JSON Lines）.
 * <p>
 * 1行 = 1メッセージ: {"t":epochMillis,"role":"user"|"robot","text":"..."}。
 * <ul>
 *   <li>保存・表示する履歴は<b>全件</b>（テキストを追記するだけでトークンは消費しない）。</li>
 *   <li>LLMへ送る履歴は {@link #recentApiMessages(int)} で<b>直近の窓だけ</b>に丸める
 *       （API先頭は必ず user 始まりになるよう、先頭の assistant は落とす）。</li>
 * </ul>
 */
public final class ConversationStore {
    private static final String TAG = "ConversationStore";
    private static final String FILE_NAME = "conversation.jsonl";

    public static final String ROLE_USER = "user";
    public static final String ROLE_ROBOT = "robot";

    /** 1メッセージ. */
    public static final class Message {
        public final long t;
        public final String role; // ROLE_USER / ROLE_ROBOT
        public final String text;

        Message(long t, String role, String text) {
            this.t = t;
            this.role = role;
            this.text = text;
        }
    }

    private final File mFile;

    public ConversationStore(Context ctx) {
        mFile = new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** 1メッセージを追記する（保存は全件。失敗しても会話は継続させる）。 */
    public synchronized void append(String role, String text) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("role", role);
            o.put("text", text);
            try (FileOutputStream out = new FileOutputStream(mFile, /*append=*/true)) {
                out.write((o.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "append failed: " + e);
        }
    }

    /** 保存済みの全メッセージを古い順に返す（表示用）。 */
    public synchronized List<Message> loadAll() {
        List<Message> list = new ArrayList<>();
        if (!mFile.isFile()) return list;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(mFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JSONObject o = new JSONObject(line);
                    list.add(new Message(
                            o.optLong("t", 0L),
                            o.optString("role", ROLE_ROBOT),
                            o.optString("text", "")));
                } catch (Exception ignore) {
                    // 壊れた行はスキップ
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "loadAll failed: " + e);
        }
        return list;
    }

    /**
     * 直近 maxMessages 件を Claude Messages API 形式へ変換して返す（サーバへの seed 用）。
     * robot→assistant, user→user。先頭が assistant にならないよう調整する。
     */
    public synchronized JSONArray recentApiMessages(int maxMessages) {
        List<Message> all = loadAll();
        int from = Math.max(0, all.size() - maxMessages);
        List<Message> window = all.subList(from, all.size());
        // API は user 始まり必須。窓の先頭が robot(assistant) なら落とす。
        int start = 0;
        while (start < window.size() && ROLE_ROBOT.equals(window.get(start).role)) start++;
        JSONArray arr = new JSONArray();
        for (int i = start; i < window.size(); i++) {
            Message m = window.get(i);
            try {
                JSONObject o = new JSONObject();
                o.put("role", ROLE_USER.equals(m.role) ? "user" : "assistant");
                o.put("content", m.text);
                arr.put(o);
            } catch (Exception ignore) {
            }
        }
        return arr;
    }
}
