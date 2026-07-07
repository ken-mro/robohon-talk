// Cloudflare Workers 用エントリ（素の fetch ハンドラ）。
// Express は import しない（body-parser→iconv-lite が Workers バンドルで壊れるため）。
// 中核ロジックは core.ts を共有。ローカル(Node)は src/index.ts(Express)。
import type { ChatRequest } from "./types.js";
import { handleChat, isBadRequest, isMock } from "./core.js";
import type { DigestRequest } from "./digest.js";
import { handleDigest, isBadDigestRequest } from "./digest.js";

type Env = { ANTHROPIC_API_KEY?: string; MOCK?: string; RELAY_TOKEN?: string };

const json = (data: unknown, status = 200): Response =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // Workers のシークレット/変数は env 経由。Anthropic SDK は process.env を読むため橋渡し。
    if (env.ANTHROPIC_API_KEY) process.env.ANTHROPIC_API_KEY = env.ANTHROPIC_API_KEY;
    if (env.MOCK) process.env.MOCK = env.MOCK;

    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, mock: isMock() });
    }

    if (request.method === "POST" && (url.pathname === "/chat" || url.pathname === "/digest")) {
      // 認証（合言葉）: RELAY_TOKEN を設定すると一致必須。公開URLの無断利用＝費用悪用を防ぐ。
      // 未設定なら fail-closed（503）でリクエストを通さない。必ず `wrangler secret put RELAY_TOKEN` を。
      const expected = env.RELAY_TOKEN;
      if (!expected) return json({ error: "relay not configured" }, 503);
      if (request.headers.get("x-relay-token") !== expected) {
        return json({ error: "unauthorized" }, 401);
      }
      let body: unknown;
      try {
        body = await request.json();
      } catch {
        return json({ error: "invalid json" }, 400);
      }
      if (url.pathname === "/digest") {
        if (isBadDigestRequest(body)) {
          return json({ error: "sessionId・clientDate(YYYY-MM-DD)・messages は必須です" }, 400);
        }
        return json(await handleDigest(body as DigestRequest));
      }
      if (isBadRequest(body)) {
        return json({ error: "sessionId と text は必須です" }, 400);
      }
      return json(await handleChat(body as ChatRequest));
    }

    return new Response("not found", { status: 404 });
  },
};
