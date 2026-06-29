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
};

/** 電話帳の登録者1人ぶん（呼び名と続柄）。 */
export type ContactInfo = { name: string; relation?: string };

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
