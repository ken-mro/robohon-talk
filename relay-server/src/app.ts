// ローカル開発用の Express ラッパ（中核ロジックは core.ts）。
// ※ Cloudflare Workers では Express を import できない（body-parser→iconv-lite が
//   バンドルで壊れる）。Workers は src/worker.ts の素の fetch ハンドラを使う。
import express from "express";
import type { ChatRequest } from "./types.js";
import { handleChat, isBadRequest, isMock } from "./core.js";
import type { DigestRequest } from "./digest.js";
import { handleDigest, isBadDigestRequest } from "./digest.js";

export const PORT = Number(process.env.PORT ?? 8787);
/** 受信ボディの上限（巨大配列でのメモリ/CPU消費を防ぐ）。1MB。 */
const MAX_BODY_BYTES = 1_000_000;

export const app = express();

// JSONボディを自前で読む（express.json は使わない）。サイズ上限を超えたら 413 で打ち切る。
app.use((req, res, next) => {
  if (req.method !== "POST") return next();
  let data = "";
  let bytes = 0;
  let aborted = false;
  req.setEncoding("utf8");
  req.on("data", (chunk: string) => {
    if (aborted) return;
    bytes += Buffer.byteLength(chunk, "utf8");
    if (bytes > MAX_BODY_BYTES) {
      aborted = true;
      res.status(413).json({ error: "body too large" });
      req.destroy();
      return;
    }
    data += chunk;
  });
  req.on("end", () => {
    if (aborted) return;
    try {
      req.body = data ? JSON.parse(data) : {};
    } catch {
      req.body = {};
    }
    next();
  });
  req.on("error", () => {
    if (aborted) return;
    req.body = {};
    next();
  });
});

// 認証（合言葉）: この Express は**ローカル開発専用**（本番は worker.ts=fail-closed）。
// ローカルは MOCK/キー無しで手軽に回す用途があるため、RELAY_TOKEN 未設定時は認証しない（fail-open）。
// 設定した場合は一致必須(401)。worker.ts と違い未設定=通す点に注意——Express を LAN/トンネルで
// 露出する場合は必ず RELAY_TOKEN を設定すること（高単価な /digest の費用悪用を防ぐため）。
app.use((req, res, next) => {
  if (req.method !== "POST") return next();
  const expected = process.env.RELAY_TOKEN;
  if (expected && req.header("x-relay-token") !== expected) {
    return res.status(401).json({ error: "unauthorized" });
  }
  next();
});

app.get("/health", (_req, res) => {
  res.json({ ok: true, mock: isMock() });
});

app.post("/chat", async (req, res) => {
  if (isBadRequest(req.body)) {
    return res.status(400).json({ error: "sessionId と text は必須です" });
  }
  res.json(await handleChat(req.body as ChatRequest));
});

app.post("/digest", async (req, res) => {
  if (isBadDigestRequest(req.body)) {
    return res.status(400).json({ error: "sessionId・clientDate(YYYY-MM-DD)・messages は必須です" });
  }
  res.json(await handleDigest(req.body as DigestRequest));
});
