import { test } from "node:test";
import assert from "node:assert/strict";
import { splitUtterances } from "../split.js";

test("空文字は空配列", () => {
  assert.deepEqual(splitUtterances(""), []);
  assert.deepEqual(splitUtterances("   "), []);
});

test("短文はそのまま1片", () => {
  assert.deepEqual(splitUtterances("こんにちは！僕、ロボホン"), ["こんにちは！僕、ロボホン"]);
});

test("文末記号で区切りつつ maxLen 内に詰め込む", () => {
  const text = "おはよう。今日はいい天気だね。お散歩に行こうよ。";
  const out = splitUtterances(text, 12);
  for (const u of out) assert.ok(u.length <= 12, `片が長すぎる: ${u}`);
  assert.equal(out.join(""), "おはよう。今日はいい天気だね。お散歩に行こうよ。");
});

test("maxLenを超える1文は読点優先で分割", () => {
  const text = "あのね、今日はとても良い天気で、お散歩日和だから、公園に行こうと思うんだ。";
  const out = splitUtterances(text, 15);
  for (const u of out) assert.ok(u.length <= 15, `片が長すぎる: ${u}`);
  assert.ok(out.length >= 2);
});

test("読点も無い長文は強制分割（情報落ちなし）", () => {
  const text = "あ".repeat(40);
  const out = splitUtterances(text, 15);
  for (const u of out) assert.ok(u.length <= 15);
  assert.equal(out.join(""), text);
});
