// prompts/*.md をビルド時に TS 定数モジュール（src/prompts/*.gen.ts）へ埋め込むジェネレータ。
//
// なぜコード生成か: このサーバは Node（tsc→dist、バンドラ無し）と Cloudflare Workers
// （wrangler/esbuild）の両方で動く。Workers には実行時のファイルシステムが無く、
// tsc は .md の import を扱えないため、「.md を正としてビルド時に埋め込む」のが
// 両ランタイムで成立する唯一の単純な方式。
//
// 使い方: node scripts/gen-prompts.mjs（build / test スクリプトと wrangler の [build] が自動実行）
// プロンプトの編集は prompts/*.md 側で行うこと。*.gen.ts は直接編集しない。
// md との同期は src/test/prompts.test.ts が検証する（gen 実行漏れの検出）。
import { mkdirSync, readFileSync, readdirSync, unlinkSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const promptsDir = path.join(root, "prompts");
const outDir = path.join(root, "src", "prompts");
mkdirSync(outDir, { recursive: true });

// 旧生成物を先に掃除する（.md のリネーム・削除で孤児化した .gen.ts が
// import され続ける事故を防ぐ。孤児が残ると import エラーで気づける）。
for (const file of readdirSync(outDir)) {
  if (file.endsWith(".gen.ts")) unlinkSync(path.join(outDir, file));
}

/** md の正規化: BOM除去・改行LF化・末尾改行除去。src/test/prompts.test.ts と一致させること。 */
export function normalize(text) {
  return text.replace(/^\uFEFF/, "").replace(/\r\n?/g, "\n").replace(/\n+$/, "");
}

for (const file of readdirSync(promptsDir)) {
  if (!file.endsWith(".md")) continue;
  const name = path.basename(file, ".md");
  if (!/^[A-Za-z_$][\w$]*$/.test(name)) {
    throw new Error(`prompts/${file}: ファイル名は JS 識別子（英数と_、先頭は英字）にすること`);
  }
  const text = normalize(readFileSync(path.join(promptsDir, file), "utf8"));
  const out =
    `// AUTO-GENERATED from prompts/${file} — 直接編集しない。\n` +
    `// 編集は prompts/${file} で行い、node scripts/gen-prompts.mjs で再生成する。\n` +
    `export const ${name}Template = ${JSON.stringify(text)};\n`;
  writeFileSync(path.join(outDir, `${name}.gen.ts`), out);
  console.log(`generated src/prompts/${name}.gen.ts (${text.length} chars)`);
}
