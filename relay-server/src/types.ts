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
  /** 端末に入っている歌・ダンスの一覧（純正コンテンツ名）。LLMが実在タイトルから指定できるように渡す。 */
  catalog?: Catalog;
};

/** 端末に入っている歌・ダンスの名前一覧（perform_motion で実在タイトルを指定させるため）。 */
export type Catalog = { songs?: string[]; dances?: string[] };

/**
 * カタログの安全上限（コンテンツ量に合わせて調整する値ではなく、悪意あるクライアントからの
 * 巨大データを弾く DoS 防御の“天井”）。正規の端末コンテンツ（歌+ダンスで百数十件）が
 * 決して届かない十分高い値にして、コンテンツが増えても更新不要にする。
 */
export const CATALOG_LIMITS = {
  maxItems: 500,
  maxItemChars: 40,
} as const;

/** 型不明の入力を Catalog へ正規化する（曲/ダンス名を無害化・重複除去・上限適用）。 */
export function sanitizeCatalog(raw: unknown): Catalog | undefined {
  const c = raw as Partial<Catalog> | undefined;
  if (!c || typeof c !== "object") return undefined;
  const clean = (arr: unknown): string[] => {
    const seen = new Set<string>();
    return (Array.isArray(arr) ? arr : [])
      .slice(0, CATALOG_LIMITS.maxItems * 4) // 整形前に足切り（DoS対策）
      .filter((s): s is string => typeof s === "string" && s.trim().length > 0)
      .map((s) => Array.from(cleanItem(s)).slice(0, CATALOG_LIMITS.maxItemChars).join(""))
      .filter((s) => s.length > 0 && !seen.has(s) && (seen.add(s), true))
      .slice(0, CATALOG_LIMITS.maxItems);
  };
  const songs = clean(c.songs);
  const dances = clean(c.dances);
  if (songs.length === 0 && dances.length === 0) return undefined;
  const out: Catalog = {};
  if (songs.length > 0) out.songs = songs;
  if (dances.length > 0) out.dances = dances;
  return out;
}

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

/** ナレッジベースのサイズ上限（プロンプトへ毎ターン入るため小さく保つ）。digest.md の文字数と揃える。 */
export const KNOWLEDGE_LIMITS = {
  maxProfileItems: 12,
  maxRecentItems: 8,
  maxItemChars: 60,
} as const;

/** YYYY-MM-DD 形式かつ実在する日付か（2026-13-45 のような不正カレンダー日を弾く）。 */
export function isValidIsoDate(s: unknown): s is string {
  if (typeof s !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const t = Date.parse(s);
  if (Number.isNaN(t)) return false;
  // Date.parse は 2026-02-30 を 03-02 へ丸めるため、往復して一致を確認する。
  return new Date(t).toISOString().slice(0, 10) === s;
}

/**
 * KB項目の1文を無害化する。改行・タブ・制御文字を空白へ畳み、隅付き括弧【】も除去し、
 * コードポイント単位で長さを丸める。これらを残すと、システムプロンプト中に偽の
 * 「【最重要】」ブロックを注入できてしまうため必須（digest入力側の neutralize と対称）。
 */
function cleanItem(s: string): string {
  const collapsed = s
    // eslint-disable-next-line no-control-regex
    .replace(/[\u0000-\u001F\u007F]/g, " ")
    .replace(/[【】]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return Array.from(collapsed).slice(0, KNOWLEDGE_LIMITS.maxItemChars).join("");
}

/** 型不明の入力を Knowledge へ正規化する（先にスライスしてから整形＝巨大配列でのDoSを防ぐ）。 */
export function sanitizeKnowledge(raw: unknown): Knowledge | undefined {
  const k = raw as Partial<Knowledge> | undefined;
  if (!k || typeof k !== "object") return undefined;
  const profile = (Array.isArray(k.profile) ? k.profile : [])
    .slice(0, KNOWLEDGE_LIMITS.maxProfileItems * 4) // 整形前に上限の緩め版で足切り（DoS対策）
    .filter((s): s is string => typeof s === "string" && s.trim().length > 0)
    .map(cleanItem)
    .filter((s) => s.length > 0)
    .slice(0, KNOWLEDGE_LIMITS.maxProfileItems);
  const recent = (Array.isArray(k.recent) ? k.recent : [])
    .slice(0, KNOWLEDGE_LIMITS.maxRecentItems * 4)
    .filter(
      (r): r is { date: string; text: string } =>
        !!r && isValidIsoDate((r as any).date) && typeof (r as any).text === "string" && (r as any).text.trim().length > 0,
    )
    .map((r) => ({ date: r.date, text: cleanItem(r.text) }))
    .filter((r) => r.text.length > 0)
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
