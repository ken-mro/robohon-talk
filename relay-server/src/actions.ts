import { LAUNCH_APPS } from "./claude.js";
import type { Action, LlmResult } from "./types.js";

/** Claude の tool_use を、アプリ側が解釈する action に変換する。未知ツール/未知アプリは null。 */
export function toAction(toolUse: LlmResult["toolUse"]): Action | null {
  if (!toolUse) return null;
  if (toolUse.name === "launch_app") {
    const app = String(toolUse.input.app ?? "");
    // enum外のアプリ名（モデルのhallucination）は弾く＝アプリ側へ無効起動を送らない。
    if (app && (LAUNCH_APPS as readonly string[]).includes(app)) return { type: "launch_app", app };
  }
  if (toolUse.name === "write_diary") {
    const text = String(toolUse.input.text ?? "").trim();
    if (text) return { type: "write_diary", text };
  }
  return null;
}

/** 終了示唆の簡易判定（モック/実応答どちらでも）。 */
export function isDone(text: string): boolean {
  return /またね|さようなら|バイバイ|ばいばい|終了するね/.test(text);
}
