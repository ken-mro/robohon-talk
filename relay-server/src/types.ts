// 中継サーバの API契約（docs/architecture.md に準拠）

export type ChatRequest = {
  sessionId: string;
  text: string;
  reset?: boolean;
  /** ロボホンの名前（端末のアドレス帳から取得。無ければ既定「ロボホン」）。 */
  robotName?: string;
  /** 現在の話し相手の呼び名（オーナー or 顔認識で特定した人。取得できた場合のみ）。 */
  ownerName?: string;
  /** 初回ターンで渡す過去履歴（端末保存の直近窓）。サーバ側履歴を復元する。 */
  history?: ChatMessage[];
  /** 電話帳の登録者（家族・友達）。ロボホンが名前で扱えるようにペルソナへ渡す。 */
  contacts?: ContactInfo[];
  /** 端末のローカル現在日時（整形済み文字列）。時刻/日付/曜日の質問に答えるため。 */
  clientTime?: string;
  /** 会話から蓄積したナレッジベース（端末保存）。ペルソナに「おぼえていること」として注入する。 */
  knowledge?: Knowledge;
};

/** 電話帳の登録者1人ぶん（呼び名と続柄）。 */
export type ContactInfo = { name: string; relation?: string };

/**
 * 会話から蓄積したナレッジベース。正データは端末側に保存し、毎リクエスト同梱する（サーバはステートレス）。
 * profile=変わりにくい事実（静的）、recent=最近の出来事（動的、date は YYYY-MM-DD）。
 */
export type Knowledge = {
  profile: string[];
  recent: { date: string; text: string }[];
};

/** ナレッジベースのサイズ上限（プロンプトへ毎ターン入るため小さく保つ）。 */
export const KNOWLEDGE_LIMITS = {
  maxProfileItems: 12,
  maxRecentItems: 8,
  maxItemChars: 60,
} as const;

/** 型不明の入力を Knowledge へ正規化する（上限適用・不正要素は捨てる）。 */
export function sanitizeKnowledge(raw: unknown): Knowledge | undefined {
  const k = raw as Partial<Knowledge> | undefined;
  if (!k || typeof k !== "object") return undefined;
  const profile = (Array.isArray(k.profile) ? k.profile : [])
    .filter((s): s is string => typeof s === "string" && s.trim().length > 0)
    .map((s) => s.trim().slice(0, KNOWLEDGE_LIMITS.maxItemChars))
    .slice(0, KNOWLEDGE_LIMITS.maxProfileItems);
  const recent = (Array.isArray(k.recent) ? k.recent : [])
    .filter(
      (r): r is { date: string; text: string } =>
        !!r &&
        typeof (r as any).date === "string" &&
        /^\d{4}-\d{2}-\d{2}$/.test((r as any).date) &&
        typeof (r as any).text === "string" &&
        (r as any).text.trim().length > 0,
    )
    .map((r) => ({ date: r.date, text: r.text.trim().slice(0, KNOWLEDGE_LIMITS.maxItemChars) }))
    .slice(0, KNOWLEDGE_LIMITS.maxRecentItems);
  if (profile.length === 0 && recent.length === 0) return undefined;
  return { profile, recent };
}

/** アプリ側が解釈する連携指示。アプリ起動 / 日記書き込み / 基本動作（歌・踊り・アクション）。 */
export type Action =
  | { type: "launch_app"; app: string }
  | { type: "write_diary"; text: string }
  | { type: "perform_motion"; kind: string; query?: string };

export type ChatResponse = {
  utterances: string[]; // ~150字に分割した発話片（順次発話する）
  action: Action | null; // 連携指示。無ければ null
  done: boolean; // 会話終了の示唆
};

/** Claude Messages API に渡す会話履歴の1要素。content は文字列 or ブロック配列。 */
export type ChatMessage = { role: "user" | "assistant"; content: unknown };

/** LLM呼び出しの正規化結果 */
export type LlmResult = {
  text: string;
  toolUse: { name: string; input: Record<string, unknown> } | null;
};
