// web search 対応の退行検知。
// - TOOLS にサーバーサイド web_search が入っていること（Haiku 4.5 対応の 20250305 版）
// - システムプロンプトに「いつ検索するか」の指針が含まれること（persona.md 側の編集漏れ検出）
import assert from "node:assert";
import { test } from "node:test";
import { buildSystemPrompt, TOOLS } from "../claude.js";

test("TOOLS にサーバーサイド web_search（20250305版・max_uses付き）が含まれる", () => {
  const ws = TOOLS.find((t) => t.name === "web_search");
  assert.ok(ws, "web_search ツールが見つからない");
  assert.strictEqual((ws as { type?: string }).type, "web_search_20250305");
  const maxUses = (ws as { max_uses?: number }).max_uses;
  assert.ok(typeof maxUses === "number" && maxUses >= 1 && maxUses <= 5, `max_uses が不正: ${maxUses}`);
});

test("システムプロンプトに web_search の使いどころの指針が入っている", () => {
  const prompt = buildSystemPrompt({ robotName: "ロボホン" });
  assert.ok(prompt.includes("web_search"), "web_search への言及が無い");
  assert.ok(prompt.includes("今のことはインターネットで調べる"), "検索指針セクションが無い");
  // 音声読み上げ用: URL 等を読み上げない指針
  assert.ok(/URL・サイト名・出典/.test(prompt), "URL読み上げ禁止の指針が無い");
});
