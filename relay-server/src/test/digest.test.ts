// /digest のロジック検証（LLM呼び出しはモックパスで回避）。
import assert from "node:assert";
import { test } from "node:test";
import { extractJsonObject, handleDigest, pruneRecent } from "../digest.js";
import { sanitizeKnowledge } from "../types.js";

test("sanitizeKnowledge: 上限適用・不正要素の除去", () => {
  const raw = {
    profile: ["  サッカーを習っている  ", "", 123, "あ".repeat(100)],
    recent: [
      { date: "2026-07-01", text: "プールに行った" },
      { date: "7月1日", text: "日付形式が不正" },
      { date: "2026-07-02", text: "" },
    ],
  };
  const k = sanitizeKnowledge(raw)!;
  assert.deepStrictEqual(k.profile[0], "サッカーを習っている");
  assert.strictEqual(k.profile.length, 2); // 空・非文字列は除去、長すぎは切り詰め
  assert.strictEqual(k.profile[1].length, 60);
  assert.deepStrictEqual(k.recent, [{ date: "2026-07-01", text: "プールに行った" }]);
});

test("sanitizeKnowledge: 空・型不明は undefined", () => {
  assert.strictEqual(sanitizeKnowledge(undefined), undefined);
  assert.strictEqual(sanitizeKnowledge("x"), undefined);
  assert.strictEqual(sanitizeKnowledge({ profile: [], recent: [] }), undefined);
});

test("pruneRecent: 21日超過去と未来日付を落とす", () => {
  const k = {
    profile: ["p"],
    recent: [
      { date: "2026-07-01", text: "keep(2日前)" },
      { date: "2026-06-12", text: "keep(ちょうど21日前)" },
      { date: "2026-06-11", text: "drop(22日前)" },
      { date: "2026-08-01", text: "drop(未来)" },
    ],
  };
  const out = pruneRecent(k, "2026-07-03");
  assert.deepStrictEqual(
    out.recent.map((r) => r.text),
    ["keep(2日前)", "keep(ちょうど21日前)"],
  );
  assert.deepStrictEqual(out.profile, ["p"]);
});

test("extractJsonObject: コードフェンスや前置きが混ざっても抽出できる", () => {
  const text = '説明です。\n```json\n{"profile": ["a"], "recent": []}\n```';
  assert.deepStrictEqual(extractJsonObject(text), { profile: ["a"], recent: [] });
  assert.strictEqual(extractJsonObject("JSONなし"), undefined);
  assert.strictEqual(extractJsonObject("{壊れたjson"), undefined);
});

test("handleDigest: モック時（キー無し）は既存KBの整理版を返し、LLMを呼ばない", async () => {
  process.env.MOCK = "1";
  try {
    const res = await handleDigest({
      sessionId: "t",
      clientDate: "2026-07-03",
      knowledge: {
        profile: ["サッカーを習っている"],
        recent: [
          { date: "2026-07-02", text: "keep" },
          { date: "2026-01-01", text: "drop(期限切れ)" },
        ],
      },
      messages: [{ role: "user", content: "こんにちは", date: "2026-07-03" }],
    });
    assert.deepStrictEqual(res.knowledge.profile, ["サッカーを習っている"]);
    assert.deepStrictEqual(
      res.knowledge.recent.map((r) => r.text),
      ["keep"],
    );
  } finally {
    delete process.env.MOCK;
  }
});

test("handleDigest: 新規会話ゼロならLLMを呼ばず既存を返す", async () => {
  // MOCK未設定でもメッセージ0件ならAPIに到達しない（キー無し環境でも安全に通る）
  const res = await handleDigest({
    sessionId: "t",
    clientDate: "2026-07-03",
    knowledge: { profile: ["p"], recent: [] },
    messages: [],
  });
  assert.deepStrictEqual(res.knowledge, { profile: ["p"], recent: [] });
});
