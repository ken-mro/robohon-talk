// ナレッジベース（おぼえていること）の日次ダイジェスト生成。
// 端末が「既存KB＋前回生成以降の新規会話」を送り、Sonnet が差分更新した新KBを返す（差分更新方式）。
// 失敗時は既存KBを整理（期限切れ削除・上限適用）して返し、記憶を失わない（安全側）。
import Anthropic from "@anthropic-ai/sdk";
import { digestTemplate } from "./prompts/digest.gen.js";
import { isMock } from "./core.js";
import type { Knowledge } from "./types.js";
import { sanitizeKnowledge } from "./types.js";

// 抽出・統合の忠実さ重視で会話用（Haiku）より上位のモデルを使う。日次1回なのでコストは誤差。
const DIGEST_MODEL = "claude-sonnet-4-6";
const MAX_TOKENS = 1500;
/** recent の保持期間（日）。プロンプト側のルール5と一致させる。 */
export const RECENT_TTL_DAYS = 21;
/** 1回のダイジェストに入れる会話ログの上限（新しい方を優先）。 */
const MAX_INPUT_MESSAGES = 200;
const MAX_MESSAGE_CHARS = 400;

export type DigestMessage = { role: "user" | "assistant"; content: string; date?: string };

export type DigestRequest = {
  sessionId: string;
  /** 基準日 YYYY-MM-DD（端末ローカル）。recent の期限切れ判定に使う。 */
  clientDate: string;
  /** 既存のナレッジベース（無ければ初回）。 */
  knowledge?: Knowledge;
  /** 前回ダイジェスト以降の新規会話ログ。 */
  messages: DigestMessage[];
};

export type DigestResponse = { knowledge: Knowledge };

/** /digest のボディ検証。必須項目が無ければ true（=400相当）。 */
export function isBadDigestRequest(body: unknown): boolean {
  const b = body as DigestRequest | undefined;
  return (
    !b?.sessionId ||
    typeof b.clientDate !== "string" ||
    !/^\d{4}-\d{2}-\d{2}$/.test(b.clientDate) ||
    !Array.isArray(b.messages)
  );
}

/** recent から期限切れ（基準日より21日超過去）と未来日付（不正）の項目を落とす。 */
export function pruneRecent(k: Knowledge, clientDate: string): Knowledge {
  const base = Date.parse(clientDate);
  if (Number.isNaN(base)) return k;
  const cutoff = base - RECENT_TTL_DAYS * 86_400_000;
  const upper = base + 86_400_000; // タイムゾーン誤差の許容ぶん
  return {
    profile: k.profile,
    recent: k.recent.filter((r) => {
      const t = Date.parse(r.date);
      return !Number.isNaN(t) && t >= cutoff && t <= upper;
    }),
  };
}

/** LLM応答からJSONオブジェクトを取り出す（コードフェンスや前置きが混ざっても耐える）。 */
export function extractJsonObject(text: string): unknown | undefined {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return undefined;
  try {
    return JSON.parse(text.slice(start, end + 1));
  } catch {
    return undefined;
  }
}

function sanitizeMessages(raw: DigestMessage[]): DigestMessage[] {
  const out: DigestMessage[] = [];
  for (const m of raw) {
    if ((m?.role === "user" || m?.role === "assistant") && typeof m.content === "string") {
      const content = m.content.trim().slice(0, MAX_MESSAGE_CHARS);
      if (!content) continue;
      const date =
        typeof m.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(m.date) ? m.date : undefined;
      out.push({ role: m.role, content, date });
    }
  }
  return out.slice(-MAX_INPUT_MESSAGES);
}

function buildUserPrompt(clientDate: string, existing: Knowledge, messages: DigestMessage[]): string {
  const log = messages
    .map((m) => `${m.date ? `[${m.date}] ` : ""}${m.role === "user" ? "USER" : "ROBOT"}: ${m.content}`)
    .join("\n");
  return (
    `基準日: ${clientDate}\n\n` +
    `【既存の記憶】\n${JSON.stringify(existing)}\n\n` +
    `【新しい会話ログ】\n${log || "（なし）"}`
  );
}

let client: Anthropic | null = null;
function getClient(): Anthropic {
  if (!client) client = new Anthropic();
  return client;
}

const EMPTY: Knowledge = { profile: [], recent: [] };

/** ダイジェスト生成の本体。常に DigestResponse を返す（例外時は既存KBの整理版）。 */
export async function handleDigest(body: DigestRequest): Promise<DigestResponse> {
  const existing = pruneRecent(sanitizeKnowledge(body.knowledge) ?? EMPTY, body.clientDate);
  const messages = sanitizeMessages(body.messages);

  // 新規会話が無い・モック運用（キー無し）のときはLLMを呼ばず、整理済みの既存KBを返す。
  if (messages.length === 0 || isMock()) {
    return { knowledge: existing };
  }

  try {
    // structured outputs は SDK のバージョン都合で使わず、プロンプトでJSONを指示して
    // 抽出（extractJsonObject）→検証（sanitizeKnowledge）の二段で受ける。
    const res = await getClient().messages.create({
      model: DIGEST_MODEL,
      max_tokens: MAX_TOKENS,
      system: digestTemplate,
      messages: [{ role: "user", content: buildUserPrompt(body.clientDate, existing, messages) }],
    });
    const text = res.content
      .filter((b): b is Anthropic.TextBlock => b.type === "text")
      .map((b) => b.text)
      .join("");
    const parsed = sanitizeKnowledge(extractJsonObject(text));
    if (!parsed) {
      console.error(`[digest] parse failed, keeping existing knowledge: ${text.slice(0, 200)}`);
      return { knowledge: existing };
    }
    const knowledge = pruneRecent(parsed, body.clientDate);
    console.log(
      `[digest] session=${body.sessionId} msgs=${messages.length} -> profile=${knowledge.profile.length} recent=${knowledge.recent.length}`,
    );
    return { knowledge };
  } catch (err) {
    console.error("digest error:", err);
    return { knowledge: existing };
  }
}
