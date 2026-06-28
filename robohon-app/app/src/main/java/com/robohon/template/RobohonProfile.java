package com.robohon.template;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.co.sharp.android.rb.addressbook.AddressBookCommonUtils;
import jp.co.sharp.android.rb.addressbook.AddressBookManager;
import jp.co.sharp.android.rb.addressbook.AddressBookVariable.AddressBookData;
import jp.co.sharp.android.rb.addressbook.AddressBookVariable.OwnerProfileData;
import jp.co.sharp.android.rb.addressbook.AddressBookVariable.RoboProfileData;

/**
 * ロボホン名 / オーナー名 / 電話帳の登録者の取得（アドレス帳API）。
 * ACCESS_CONTACT権限が無い・APIが使えない場合でも落ちないよう、例外は握って既定値を返す。
 */
public final class RobohonProfile {
    private static final String TAG = "RobohonProfile";

    /** 既定のロボホン名（取得不可時）。 */
    public static final String DEFAULT_ROBOT_NAME = "ロボホン";
    /** 起動ワード用の既定かな。 */
    public static final String DEFAULT_ROBOT_KANA = "ろぼほん";

    private RobohonProfile() {}

    /** 電話帳の登録者1人ぶん（呼び名と続柄）。 */
    public static final class Contact {
        public final int id;
        public final String name;
        public final String relation; // 無ければ null

        Contact(int id, String name, String relation) {
            this.id = id;
            this.name = name;
            this.relation = relation;
        }
    }

    /** ロボホンの名前。取得できなければ "ロボホン"。 */
    public static String getRobotName(Context ctx) {
        try {
            AddressBookManager m = AddressBookManager.getService(ctx);
            if (m != null) {
                RoboProfileData robo = m.getRoboProfileData();
                if (robo != null && !TextUtils.isEmpty(robo.getRbname())) {
                    return robo.getRbname();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getRobotName failed: " + t);
        }
        return DEFAULT_ROBOT_NAME;
    }

    /** オーナーの呼び名（ニックネーム→姓名）。無ければ null（呼び名未設定）。 */
    public static String getOwnerName(Context ctx) {
        try {
            AddressBookManager m = AddressBookManager.getService(ctx);
            if (m != null) {
                OwnerProfileData owner = m.getOwnerProfileData();
                if (owner != null) {
                    String name = displayName(owner.getNickname(), owner.getLastname(), owner.getFirstname());
                    if (!name.isEmpty()) return name;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getOwnerName failed: " + t);
        }
        return null;
    }

    /**
     * 電話帳の登録者（家族・友達）一覧。オーナー(201)/ロボホン(203)は除外。
     * 名前(よみ/ニックネーム)が設定されている連絡先のみ。取得不可なら空リスト。
     */
    public static List<Contact> getAllContacts(Context ctx) {
        List<Contact> out = new ArrayList<>();
        try {
            AddressBookManager m = AddressBookManager.getService(ctx);
            if (m == null) return out;
            // KEY_ANY_NAME は NOTNULL 検索に使えないため、名前系キーを個別に NOTNULL 検索して和集合を取る。
            Set<Integer> ids = new LinkedHashSet<>();
            collectIds(ids, m, AddressBookCommonUtils.KEY_NICKNAME);
            collectIds(ids, m, AddressBookCommonUtils.KEY_LAST_NAME);
            collectIds(ids, m, AddressBookCommonUtils.KEY_FIRST_NAME);
            for (int id : ids) {
                if (id == AddressBookCommonUtils.CONTACT_ID_OWNER
                        || id == AddressBookCommonUtils.CONTACT_ID_ROBO) {
                    continue;
                }
                try {
                    AddressBookData d = m.getAddressBookData(id);
                    if (d == null) continue;
                    String name = displayName(d.getNickname(), d.getLastname(), d.getFirstname());
                    if (name.isEmpty()) continue;
                    out.add(new Contact(id, name, emptyToNull(d.getRelations())));
                } catch (Throwable t) {
                    // 1件失敗してもスキップして続行
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getAllContacts failed: " + t);
        }
        return out;
    }

    /** contact_id から呼び名を返す（顔認識結果のマッピング用）。オーナーはオーナー呼び名。無ければ null。 */
    public static String getNameByContactId(Context ctx, int id) {
        try {
            if (id == AddressBookCommonUtils.CONTACT_ID_OWNER) return getOwnerName(ctx);
            AddressBookManager m = AddressBookManager.getService(ctx);
            if (m != null) {
                AddressBookData d = m.getAddressBookData(id);
                if (d != null) {
                    String name = displayName(d.getNickname(), d.getLastname(), d.getFirstname());
                    if (!name.isEmpty()) return name;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getNameByContactId failed: " + t);
        }
        return null;
    }

    /** getContactID(key, NOTNULL) の結果（[0]=件数, 以降=ID）を ids へ追加。 */
    private static void collectIds(Set<Integer> ids, AddressBookManager m, String key) {
        try {
            int[] res = m.getContactID(key, AddressBookCommonUtils.VALUE_NOTNULL);
            if (res == null || res.length <= 1) return;
            int count = res[0];
            for (int i = 1; i < res.length && i <= count; i++) ids.add(res[i]);
        } catch (Throwable t) {
            Log.w(TAG, "getContactID(" + key + ") failed: " + t);
        }
    }

    /** 呼び名の優先順位：ニックネーム → 姓+さん → 名+さん。すべて空なら ""。 */
    private static String displayName(String nickname, String lastname, String firstname) {
        if (!TextUtils.isEmpty(nickname)) return nickname;
        if (!TextUtils.isEmpty(lastname)) return lastname + "さん";
        if (!TextUtils.isEmpty(firstname)) return firstname + "さん";
        return "";
    }

    private static String emptyToNull(String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }
}
