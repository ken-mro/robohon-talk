// プロンプト外部化（prompts/*.md → *.gen.ts 埋め込み）の同期検証。
// gen を通さずに .md だけ編集・コミットした場合にここで検出する。
import assert from "node:assert";
import { readFileSync } from "node:fs";
import path from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { digestTemplate } from "../prompts/digest.gen.js";
import { personaTemplate } from "../prompts/persona.gen.js";

// scripts/gen-prompts.mjs の normalize と同一の正規化（変更時は両方を揃えること）。
function normalize(text: string): string {
  return text.replace(/^\uFEFF/, "").replace(/\r\n?/g, "\n").replace(/\n+$/, "");
}

const promptsDir = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "prompts");
const md = (name: string): string => normalize(readFileSync(path.join(promptsDir, name), "utf8"));
const placeholders = (template: string): string[] => [
  ...new Set([...template.matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1])),
];

test("persona.gen.ts は prompts/persona.md と同期している（gen 実行漏れの検出）", () => {
  assert.strictEqual(personaTemplate, md("persona.md"));
});

test("persona.md のプレースホルダは {{robotName}} のみ（未知の {{...}} はコードが置換できず素通りする）", () => {
  assert.deepStrictEqual(placeholders(personaTemplate), ["robotName"]);
});

test("digest.gen.ts は prompts/digest.md と同期している（gen 実行漏れの検出）", () => {
  assert.strictEqual(digestTemplate, md("digest.md"));
});

test("digest.md にプレースホルダは無い（動的データはユーザーメッセージ側で渡す）", () => {
  assert.deepStrictEqual(placeholders(digestTemplate), []);
});
