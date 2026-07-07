# 見送りタスク一覧（既知の改善余地）

`fix/terms-compliance` ブランチでの規約対応・プロンプト外部化・ナレッジベース(KB)実装の際、
敵対的レビューで検出したが**今回のスコープ外**として意図的に見送った項目の記録。
実害の評価と、着手する場合の指針をあわせて残す。優先度は目安。

---

## 1. 会話履歴 conversation.jsonl の無制限増加によるUIスレッドパース（中）

- **内容**: `ConversationStore` は会話を1行1メッセージで全件追記し、削減しない。
  `renderHistory()`（onCreate）と `recentApiMessages()`（毎ユーザー発話）が
  全ファイルをUIスレッドで再パースする。数ヶ月運用でファイルが肥大化すると
  パースでUIブロック＝ANRのリスク。
- **今回スコープ外の理由**: KB機能の変更範囲外の既存コード。KBのダイジェスト経路
  （`messagesForDigestSince`）はバックグラウンドスレッド化で対応済みだが、
  上記2経路は既存の設計に属する。
- **着手する場合**: (a) jsonl のローテーション（上限行数で切り詰め、古い分は別ファイルへ退避 or 破棄）、
  (b) `renderHistory`/`recentApiMessages` をバックグラウンドスレッド＋UIへ postで反映、
  のいずれか。表示は直近N件（現状 RENDER_LIMIT=200）に絞っているので、
  読み込み自体をインデックス化 or 末尾読みにするのが本筋。
- **参照**: `robohon-app/.../ConversationStore.java`, `MainActivity.java`（renderHistory / postToRelay）

## 2. extractJsonObject が散文中の裸の `{` で稀に誤抽出（低）

- **内容**: `/digest` のLLM応答からJSONを取り出す `extractJsonObject` は最初の `{` から
  波括弧の深さを数えて最初の完全オブジェクトを切り出す。JSON本体より前の散文に
  裸の `{` があると、そこから解析を始めて parse 失敗しうる。
- **実害**: 小。digest.md はJSONのみ出力を指示。失敗しても `handleDigest` は
  既存KBの整理版を返す安全側動作で、その日の学習が無言でスキップされるだけ。
- **着手する場合**: 候補の `{` 位置を順に試す、または「JSONらしさ（先頭キーが profile/recent）」で
  当たりを選ぶ。あわせて parse skip をメトリクス化して検知可能にすると良い。
- **参照**: `relay-server/src/digest.ts`（extractJsonObject）

## 3. /chat 側で stale な recent が理論上注入されうる（低）

- **内容**: `/digest` は recent を21日で失効（pruneRecent）させるが、`/chat` は端末が送ってきたKBを
  `sanitizeKnowledge` で検証するのみで pruneRecent を通さない。端末がその日ダイジェスト未実行だと、
  古い（例: 2年前の日付の）recent がそのまま「さいきんのこと」として注入される可能性。
- **実害**: 小。ダイジェストが日次で走って prune するため実運用では顕在化しにくい。
  不正カレンダー日（2026-13-45等）は `isValidIsoDate` で `/chat` 経路でも落ちるため、
  残るのは「有効だが古い日付」だけ。
- **着手する場合**: `handleChat` でも `clientTime` から基準日を導いて recent を prune する
  （clientTime は現状「2026年7月8日(火) 10:00」形式の和文なので、端末から YYYY-MM-DD も送るのが簡単）。
- **参照**: `relay-server/src/core.ts`（handleChat）, `digest.ts`（pruneRecent）

## 4. ダイジェスト二重発火の残余（低）

- **内容**: `mDigestRunning` フラグと試行バックオフ(3h)で二重送信は防いでいるが、
  フラグはUIスレッドのin-memoryで、Activity再生成をまたぐ極端なケースまではガードしない。
- **実害**: 小。ガイドライン4.9で onPause は必ず finish する運用のため、通常は起きない。
  最悪ケースでも世代ガードで二重保存は防がれ、余分な /digest 送信1回に留まる。
- **着手する場合**: 送信中フラグを SharedPreferences 等の永続 or プロセス跨ぎで持つ。
  ただし複雑化に見合うかは要検討。
- **参照**: `robohon-app/.../MainActivity.java`（maybeRunDigest）

## 5. マスキングの1文字呼び名・部分一致（設計上のトレードオフ）

- **内容**: 名前送信不許可時のマスク（maskNames / maskContactNames / maskKnowledge）は
  2文字以上の名前のみ対象。1文字の呼び名は全経路で漏れる。逆に名前が一般語の部分文字列だと過剰マスク。
- **実害**: 小〜中（プライバシー方針次第）。1文字マスクは無関係な語を巻き込んで会話が壊れるため、
  意図的に対象外にしている。
- **着手する場合**: 前後の文字種チェック（助詞・記号境界）を入れて1文字名も安全にマスクする、
  または形態素境界を見る。コストと誤爆のバランス次第。
- **参照**: `robohon-app/.../MainActivity.java`（maskTargetNames）

---

## 補足: 未対応が妥当な設計上の制約（対応不要）

- **アプリ削除でKB・会話履歴も消える**: いずれも端末保存（`getFilesDir`）のため。
  会話履歴と同じ挙動で、この用途では許容範囲。クラウド同期は現状の同意設計（外部保存を増やさない）と
  トレードオフになるため、意図的に持たない。
- **LLM自由生成のキャラクター逸脱リスク（規約11条(4)）**: プロンプトで縛るが逸脱ゼロは保証不能。
  自分の端末での私的利用（規約4条・13条の許諾範囲内）では現実的リスクは低い。公開・配布・収益化に
  進む場合はこの点が正面から問われる（`memory/reference-robohon-legal-constraints` 参照）。
