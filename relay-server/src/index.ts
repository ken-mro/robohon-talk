// ローカル(Node)起動用エントリ。Cloudflare Workers 用は src/worker.ts。
import { app, PORT } from "./app.js";

app.listen(PORT, () => {
  console.log(`relay-server listening on :${PORT}`);
});
