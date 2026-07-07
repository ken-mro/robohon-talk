# relay-server（RoBoHoN × Claude 中継サーバ・Phase B PoC）

端末（robohon-app）からの音声認識テキストを Claude（Haiku 4.5）へ中継し、**~150字に分割した発話**と**アプリ連携action**を返す Node.js/TypeScript サーバ。設計は [../docs/architecture.md](../docs/architecture.md)。

- パッケージマネージャ: **pnpm**
- APIキーは `.env` の `ANTHROPIC_API_KEY`（端末・リポジトリには置かない）。未設定なら**モックモード**で起動（キー不要でループ確認可）。

## セットアップ
```powershell
cd relay-server
# 社内プロキシ(TLS傍受)環境では証明書PEMを指定（Windowsルートから生成済みの例）
$env:NODE_EXTRA_CA_CERTS = "$env:USERPROFILE\.config\windows-root-ca.pem"
pnpm install
pnpm test          # 分割ロジックの単体テスト（5件）
pnpm build
```

## 起動
```powershell
# モックモード（キー不要）
$env:MOCK="1"; pnpm start
# 実キーで起動（.env を作成: cp .env.example .env して ANTHROPIC_API_KEY を設定）
pnpm run start:env
```
既定ポート 8787（`.env` の `PORT` で変更可）。

## API
- `GET /health` → `{ ok, mock }`
- `POST /chat`
  - req: `{ "sessionId": "string", "text": "認識テキスト", "reset": false }`
  - res: `{ "utterances": ["~150字×N"], "action": {"type":"launch_app","app":"camera"} | null, "done": false }`

例:
```powershell
curl -Method Post http://127.0.0.1:8787/chat -ContentType application/json `
  -Body '{"sessionId":"t1","text":"写真を撮って","reset":true}'
# → {"utterances":["わかった、写真を撮るね！"],"action":{"type":"launch_app","app":"camera"},"done":false}
```

## 構成
- `src/index.ts` … Express。`/chat` で履歴管理→LLM呼び出し→分割→action変換。
- `src/claude.ts` … Anthropic SDK（model `claude-haiku-4-5`、thinkingなし）、systemプロンプトの組み立て、`launch_app` ツール定義。
- `prompts/persona.md` … **ペルソナ本文の正データ**（`{{robotName}}` プレースホルダ）。プロンプトの編集はこのファイルで行う。
- `scripts/gen-prompts.mjs` … `prompts/*.md` → `src/prompts/*.gen.ts` の埋め込み定数を生成（build/test/cf:dev/deploy が自動実行。手動は `pnpm gen`）。Workers に実行時 fs が無いためビルド時埋め込み方式。`*.gen.ts` は直接編集しない。
- `src/mockClaude.ts` … キー無し時のモック応答（「写真」→camera等）。
- `src/split.ts` … 文末優先の~150字分割（読点・強制分割フォールバック）。
- `src/actions.ts` … tool_use→action 変換、終了判定。
- `src/test/split.test.ts` … 分割の単体テスト（`node:test`）。

## 注意
- PoC中はプロセス内メモリで履歴保持（再起動で消える）。`reset:true` でセッション履歴クリア。
- 履歴は直近20往復に丸めてコンテキスト/コスト抑制。
- 実用化（Phase C）ではHTTPS・認証・ログ/コスト監視・永続履歴を追加検討。
