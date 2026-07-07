// ローカル開発用の Express ラッパ（中核ロジックは core.ts）。
// ※ Cloudflare Workers では Express を import できない（body-parser→iconv-lite が
//   バンドルで壊れる）。Workers は src/worker.ts の素の fetch ハンドラを使う。
import express from "express";
import type { ChatRequest } from "./types.js";
import { handleChat, isBadRequest, isMock } from "./core.js";
import type { DigestRequest } from "./digest.js";
import { handleDigest, isBadDigestRequest } from "./digest.js";

export const PORT = Number(process.env.PORT ?? 8787);

export const app = express();

// JSONボディを自前で読む（express.json は使わない）。
app.use((req, _res, next) => {
  if (req.method !== "POST") return next();
  let data = "";
  req.setEncoding("utf8");
  req.on("data", (chunk: string) => {
    data += chunk;
  });
  req.on("end", () => {
    try {
      req.body = data ? JSON.parse(data) : {};
    } catch {
      req.body = {};
    }
    next();
  });
  req.on("error", () => {
    req.body = {};
    next();
  });
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
