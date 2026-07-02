package com.robohon.template.voiceui;

/**
 * シナリオファイルで使用する定数の定義クラス.<br>
 * <p/>
 * <p>
 * scene、memory_p(長期記憶の変数名)、resolve variable(アプリ変数解決の変数名)、accostのwordはPackage名を含むこと<br>
 * </p>
 */
public class ScenarioDefinitions {

    //static クラスとして使用する.
    private ScenarioDefinitions() {
    }

    /****************** 共通の定義 *******************/
    /**
     * sceneタグを指定する文字列
     */
    public static final String TAG_SCENE = "scene";
    /**
     * accostタグを指定する文字列
     */
    public static final String TAG_ACCOST = "accost";
    /**
     * memory_pを指定するタグ
     */
    public static final String TAG_MEMORY_P = "memory_p:";
    /**
     * target属性を指定する文字列
     */
    public static final String ATTR_TARGET = "target";
    /**
     * function属性を指定する文字列
     */
    public static final String ATTR_FUNCTION = "function";

    /****************** アプリ固有の定義 *******************/
    /**
     * Package名.
     */
    protected static final String PACKAGE = "com.robohon.template";
    /**
     * controlタグで指定するターゲット名.
     */
    public static final String TARGET = PACKAGE;
    /**
     * scene名: アプリ共通のシーン
     */
    public static final String SCENE_COMMON = PACKAGE + ".scene_common";

    /****************** LLM会話(chat.hvml)用の定義 *******************/
    /** accost: 起動あいさつ→待受 */
    public static final String ACC_GREET = PACKAGE + ".chat.greet";
    /** accost: 応答を1発話する */
    public static final String ACC_SAY = PACKAGE + ".chat.say";
    /** accost: ユーザ発話を待ち受ける */
    public static final String ACC_LISTEN = PACKAGE + ".chat.listen";

    /** function: 認識テキストをアプリへ通知（capture topicのcontrol） */
    public static final String FUNC_USER_SAID = "user_said";
    /** function: 1発話の完了通知（say topicのcontrol） */
    public static final String FUNC_SAY_DONE = "say_done";

    /** capture controlのdataキー（認識テキスト = ${Lvcsr:Basic}） */
    public static final String DATA_TEXT = "text";

    /** アプリ変数解決名：応答発話テキスト（${com.robohon.template:speech}） */
    public static final String RESOLVE_SPEECH = PACKAGE + ":speech";
}
