package com.robohon.template;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 外部送信（電話帳の名前情報）に関するユーザー選択の保存。
 * 会話テキストの外部送信はアプリの動作上必須のため対象外とし、
 * 任意項目である「電話帳の名前（オーナー・登録者の呼び名）」の送信可否のみを扱う。
 * 選択した際の説明文の版と日時を保存し、説明文を実質的に変更した場合は
 * CONSENT_TEXT_VERSION のインクリメントで全ユーザーに再同意を求められる。
 */
public final class ConsentStore {
    private static final String PREFS = "consent";
    private static final String KEY_CHOICE_VERSION = "names_choice_version";
    private static final String KEY_CHOICE_AT = "names_choice_at";
    private static final String KEY_NAMES_ALLOWED = "names_allowed";

    /** 同意説明文の版。ユーザーへ示す説明・選択肢を実質的に変えたらインクリメントする。 */
    public static final int CONSENT_TEXT_VERSION = 1;

    private final SharedPreferences mPrefs;

    public ConsentStore(Context ctx) {
        mPrefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 現行版の説明文で選択済みか（false=初回起動、または説明文改定後まだ選び直していない）。 */
    public boolean hasChoice() {
        return mPrefs.getInt(KEY_CHOICE_VERSION, 0) >= CONSENT_TEXT_VERSION;
    }

    /** 電話帳の名前を外部（中継サーバ経由のAIサービス）へ送ってよいか。未選択時は false（送らない）。 */
    public boolean isNamesAllowed() {
        return mPrefs.getBoolean(KEY_NAMES_ALLOWED, false);
    }

    /** 選択を保存（説明文の版・選択日時も記録）。 */
    public void setNamesAllowed(boolean allowed) {
        mPrefs.edit()
                .putInt(KEY_CHOICE_VERSION, CONSENT_TEXT_VERSION)
                .putLong(KEY_CHOICE_AT, System.currentTimeMillis())
                .putBoolean(KEY_NAMES_ALLOWED, allowed)
                .apply();
    }
}
