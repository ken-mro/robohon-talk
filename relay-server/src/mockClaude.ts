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
  if (/おわり|終了|バイバイ|さようなら/.test(text)) {
    return { text: "またね！話せて楽しかったよ。", toolUse: null };
  }
  return {
    text: `${text ? `「${text}」だね。` : ""}うん、僕はまだ準備中だけど、ちゃんと聞こえてるよ！これからもっとお話しできるようになるね。`,
    toolUse: null,
  };
}
