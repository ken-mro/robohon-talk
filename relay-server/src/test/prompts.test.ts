// プロンプト外部化（prompts/*.md → *.gen.ts 埋め込み）の同期検証。
// gen を通さずに .md だけ編集・コミットした場合にここで検出する。
import assert from "node:assert";
import { readFileSync } from "node:fs";
import path from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { personaTemplate } from "../prompts/persona.gen.js";

// scripts/gen-prompts.mjs の normalize と同一の正規化（変更時は両方を揃えること）。
function normalize(text: string): string {
  return text.replace(/^\uFEFF/, "").replace(/\r\n?/g, "\n").replace(/\n+$/, "");
}

const mdPath = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  "..", "..", "prompts", "persona.md",
);

test("persona.gen.ts は prompts/persona.md と同期している（gen 実行漏れの検出）", () => {
  assert.strictEqual(personaTemplate, normalize(readFileSync(mdPath, "utf8")));
});

test("persona.md のプレースホルダは {{robotName}} のみ（未知の {{...}} はコードが置換できず素通りする）", () => {
  const names = [...personaTemplate.matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1]);
  assert.deepStrictEqual([...new Set(names)], ["robotName"]);
});
