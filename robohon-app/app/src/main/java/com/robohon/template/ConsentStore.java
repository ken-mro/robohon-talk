package com.robohon.template;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 外部送信（電話帳の名前情報）に関するユーザー選択の保存。
 * 会話テキストの外部送信はアプリの動作上必須のため対象外とし、
 * 任意項目である「電話帳の名前（オーナー・登録者の呼び名）」の送信可否のみを扱う。
 * 未選択（初回起動）と「送らない」を区別するため、選択済みフラグを別キーで持つ。
 */
public final class ConsentStore {
    private static final String PREFS = "consent";
    private static final String KEY_CHOICE_MADE = "names_choice_made";
    private static final String KEY_NAMES_ALLOWED = "names_allowed";

    private final SharedPreferences mPrefs;

    public ConsentStore(Context ctx) {
        mPrefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** ユーザーが一度でも選択したか（false=初回起動でまだ選んでいない）。 */
    public boolean hasChoice() {
        return mPrefs.getBoolean(KEY_CHOICE_MADE, false);
    }

    /** 電話帳の名前を外部（中継サーバ経由のAIサービス）へ送ってよいか。未選択時は false（送らない）。 */
    public boolean isNamesAllowed() {
        return mPrefs.getBoolean(KEY_NAMES_ALLOWED, false);
    }

    /** 選択を保存（選択済みフラグも同時に立てる）。 */
    public void setNamesAllowed(boolean allowed) {
        mPrefs.edit()
                .putBoolean(KEY_CHOICE_MADE, true)
                .putBoolean(KEY_NAMES_ALLOWED, allowed)
                .apply();
    }
}
