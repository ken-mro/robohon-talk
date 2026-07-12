// 中継サーバの中核ロジック（HTTPフレームワーク非依存）。
// ローカルは Express(app.ts) から、Cloudflare Workers は素の fetch ハンドラ(worker.ts) から呼ぶ。
import type { ChatRequest, ChatResponse, ChatMessage } from "./types.js";
import { sanitizeCatalog, sanitizeKnowledge } from "./types.js";
import { splitUtterances } from "./split.js";
import { callClaude } from "./claude.js";
import { callClaudeMock } from "./mockClaude.js";
import { toAction, isDone } from "./actions.js";

// MOCK判定は遅延評価（Workersではキーがモジュール読込後に process.env へ載るため）。
export const isMock = (): boolean => process.env.MOCK === "1" || !process.env.ANTHROPIC_API_KEY;

// sessionId -> 会話履歴（ローカル(Express)用のメモリキャッシュ）。
// Workers ではリクエストごとに isolate が異なりうるため、アプリが毎ターン history を送る
// （クライアント権威＝ステートレス）。このMapは history 未送信時のフォールバック。
const histories = new Map<string, ChatMessage[]>();
// LLMへ渡す履歴の上限。端末側は全件保存・表示するが、コスト抑制のため送るのは直近のみ。
const MAX_TURNS = 10; // 直近N往復（=2N メッセージ）に丸める

const asText = (c: unknown): string => (typeof c === "string" ? c : String(c ?? ""));

/**
 * Messages API 用に履歴を正規化する。
 * - 連続する同一ロールを1件に結合（端末履歴は1発話=1要素で assistant が連続しうる）。
 * - 先頭の非 user を落とす（API は user 始まり必須）。
 * - 直近 MAX_TURNS*2 件に丸める。
 */
function clampHistory(msgs: ChatMessage[]): ChatMessage[] {
  const merged: ChatMessage[] = [];
  for (const m of msgs) {
    const last = merged[merged.length - 1];
    if (last && last.role === m.role) {
      last.content = `${asText(last.content)}\n${asText(m.content)}`;
    } else {
      merged.push({ role: m.role, content: asText(m.content) });
    }
  }
  let out = merged.slice(-MAX_TURNS * 2);
  while (out.length > 0 && out[0].role !== "user") out = out.slice(1);
  return out;
}

/** 受信した history を ChatMessage[] として検証（role/content が妥当な要素のみ）。 */
function sanitizeSeed(seed: unknown): ChatMessage[] {
  if (!Array.isArray(seed)) return [];
  const out: ChatMessage[] = [];
  for (const m of seed) {
    const role = (m as any)?.role;
    const content = (m as any)?.content;
    if ((role === "user" || role === "assistant") && typeof content === "string" && content.trim()) {
      out.push({ role, content });
    }
  }
  return out;
}

/** /chat のボディ検証。必須項目が無ければ true（=400相当）。 */
export function isBadRequest(body: unknown): boolean {
  const b = body as ChatRequest | undefined;
  return !b?.sessionId || typeof b.text !== "string";
}

/** 1往復の会話処理。例外時はフォールバック発話を返す（常に ChatResponse を返す）。 */
export async function handleChat(body: ChatRequest): Promise<ChatResponse> {
  if (body.reset) histories.delete(body.sessionId);
  // クライアント権威: history が来ていればそれを文脈の真実とする（ステートレス）。
  // 無ければメモリMapにフォールバック（ローカルExpress運用での後方互換）。
  let history = body.history ? sanitizeSeed(body.history) : histories.get(body.sessionId) ?? [];
  history.push({ role: "user", content: body.text });

  try {
    // LLMへ渡すのは直近窓のみ（先頭 user 始まり・ロール結合を保証）
    const window = clampHistory(history);
    const llm = isMock()
      ? await callClaudeMock(window)
      : await callClaude(window, {
          ownerName: body.ownerName,
          robotName: body.robotName,
          contacts: body.contacts,
          clientTime: body.clientTime,
          knowledge: sanitizeKnowledge(body.knowledge),
          catalog: sanitizeCatalog(body.catalog),
        });

    history.push({ role: "assistant", content: llm.text });
    histories.set(body.sessionId, history.slice(-MAX_TURNS * 2));

    const action = toAction(llm.toolUse);
    let utterances = splitUtterances(llm.text);
    // 日記作成は本文を読み上げる。前置きに既に本文が含まれていれば二重読みを避ける。
    if (action?.type === "write_diary" && !llm.text.includes(action.text)) {
      utterances = [...utterances, ...splitUtterances(action.text)];
    }
    if (utterances.length === 0 && action) {
      utterances = action.type === "write_diary" ? ["日記、書いたよ！"] : ["わかった、やってみるね！"];
    }

    const actionDetail = !action
      ? "none"
      : action.type === "launch_app"
        ? `launch_app:${action.app}`
        : action.type === "perform_motion"
          ? `perform_motion:${action.kind}${action.query ? "/" + action.query : ""}`
          : "write_diary";
    console.log(
      `[chat] session=${body.sessionId} in="${body.text}" -> utt=${utterances.length} action=${actionDetail}`,
    );
    return { utterances, action, done: isDone(llm.text) };
  } catch (err) {
    console.error("chat error:", err);
    return {
      utterances: ["ごめんね、今うまく考えられないみたい。もう一回話しかけてくれる？"],
      action: null,
      done: false,
    };
  }
}
