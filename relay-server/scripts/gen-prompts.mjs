// prompts/*.md をビルド時に TS 定数モジュール（src/prompts/*.gen.ts）へ埋め込むジェネレータ。
//
// なぜコード生成か: このサーバは Node（tsc→dist、バンドラ無し）と Cloudflare Workers
// （wrangler/esbuild）の両方で動く。Workers には実行時のファイルシステムが無く、
// tsc は .md の import を扱えないため、「.md を正としてビルド時に埋め込む」のが
// 両ランタイムで成立する唯一の単純な方式。
//
// 使い方: node scripts/gen-prompts.mjs（build / test / cf:dev / deploy スクリプトが自動実行）
// プロンプトの編集は prompts/*.md 側で行うこと。*.gen.ts は直接編集しない。
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const promptsDir = path.join(root, "prompts");
const outDir = path.join(root, "src", "prompts");
mkdirSync(outDir, { recursive: true });

for (const file of readdirSync(promptsDir)) {
  if (!file.endsWith(".md")) continue;
  const name = path.basename(file, ".md");
  // 改行コードを LF に正規化し（Windows の autocrlf 対策）、
  // 「ファイル末尾の改行」規約ぶんの末尾 LF を1つだけ除去して原文どおりの埋め込みにする。
  const text = readFileSync(path.join(promptsDir, file), "utf8")
    .replace(/\r\n/g, "\n")
    .replace(/\n$/, "");
  const out =
    `// AUTO-GENERATED from prompts/${file} — 直接編集しない。\n` +
    `// 編集は prompts/${file} で行い、node scripts/gen-prompts.mjs で再生成する。\n` +
    `export const ${name}Template = ${JSON.stringify(text)};\n`;
  writeFileSync(path.join(outDir, `${name}.gen.ts`), out);
  console.log(`generated src/prompts/${name}.gen.ts (${text.length} chars)`);
}
