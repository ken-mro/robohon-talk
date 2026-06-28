/**
 * LLM応答テキストを発話片に分割する。
 * RoBoHoNは長文を一息で読むと不自然なので、文末(。！？!?)優先で詰め込み、
 * 1片が maxLen を超えないようにする。1文が長すぎる場合は読点で、無ければ強制分割。
 *
 * 注: HVMLの公式仕様に発話文字数上限は無い（hvml-reference.md）。150字は
 * 自然さ・先行事例に基づく実用値。
 */
export function splitUtterances(text: string, maxLen = 150): string[] {
  const clean = text.replace(/\s+/g, " ").trim();
  if (!clean) return [];

  // 文末記号を保持したまま文に分割
  const sentences = clean.match(/[^。！？!?]*[。！？!?]?/g)?.filter((s) => s.length > 0) ?? [clean];

  const out: string[] = [];
  let buf = "";
  const flush = () => {
    const t = buf.trim();
    if (t) out.push(t);
    buf = "";
  };

  for (const s of sentences) {
    if (s.length > maxLen) {
      // バッファを出してから長文を分割
      flush();
      let rest = s;
      while (rest.length > maxLen) {
        // maxLen以内で最後の読点を探す（comma+1がmaxLenを超えないよう maxLen-1 まで）。
        // 前すぎる場合は強制カット。
        const comma = rest.lastIndexOf("、", maxLen - 1);
        let cut = comma >= Math.floor(maxLen * 0.5) ? comma + 1 : maxLen;
        // サロゲートペア（絵文字・一部漢字）の途中で切らない
        if (cut < rest.length) {
          const code = rest.charCodeAt(cut - 1);
          if (code >= 0xd800 && code <= 0xdbff) cut -= 1;
        }
        out.push(rest.slice(0, cut).trim());
        rest = rest.slice(cut);
      }
      buf = rest;
    } else if ((buf + s).length > maxLen) {
      flush();
      buf = s;
    } else {
      buf += s;
    }
  }
  flush();
  return out;
}
