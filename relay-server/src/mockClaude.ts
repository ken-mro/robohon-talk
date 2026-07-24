import type { ChatMessage, LlmResult } from "./types.js";

/**
 * APIキー未設定時のモック。キー無しで会話ループと150字分割を確認できる。
 * 簡易ルール: 「写真」を含めば camera 起動 tool_use、それ以外はオウム返し風の短い応答。
 */
export async function callClaudeMock(messages: ChatMessage[]): Promise<LlmResult> {
  const lastUser = [...messages].reverse().find((m) => m.role === "user");
  const text = typeof lastUser?.content === "string" ? lastUser.content : "";

  if (/写真|撮って|カメラ/.test(text)) {
    return { text: "わかった、写真を撮るね！", toolUse: { name: "launch_app", input: { app: "camera" } } };
  }
  if (/アルバム|見せて/.test(text)) {
    return { text: "アルバムを開くね！", toolUse: { name: "launch_app", input: { app: "album" } } };
  }
  // 「◯◯に誕生日の歌」→ 名前入りバースデー。名前が取れなければ聞き返す（純正の「誰に?」相当）。
  if (/誕生日|たんじょうび|バースデー/.test(text)) {
    const m = text.match(/([^\s、。]{1,20}?)(?:に|へ)(?=.*(?:誕生日|たんじょうび|バースデー))/);
    if (m) return { text: "うたうね！", toolUse: { name: "sing_birthday", input: { name: m[1] } } };
    return { text: "だれにうたう？", toolUse: null };
  }
  if (/おわり|終了|バイバイ|さようなら/.test(text)) {
    return { text: "またね！話せて楽しかったよ。", toolUse: null };
  }
  return {
    text: `${text ? `「${text}」だね。` : ""}うん、僕はまだ準備中だけど、ちゃんと聞こえてるよ！これからもっとお話しできるようになるね。`,
    toolUse: null,
  };
}
