import Anthropic from "@anthropic-ai/sdk";
import type { ChatMessage, ContactInfo, LlmResult } from "./types.js";

// 音声会話の低遅延を優先。Haiku 4.5 は effort/adaptive thinking 非対応のため使わない。
const MODEL = "claude-haiku-4-5";
const MAX_TOKENS = 512;

function buildPersona(robotName: string, ownerCall: string): string {
  return `あなたの名前は「${robotName}」です。シャープが開発したモバイル型ロボット電話で、身長約20cmの小さな二足歩行ロボット。いつも${ownerCall}のそばにいる、心を通わせるパートナーです。名前を聞かれたら「ボクは${robotName}だよ！」と答えます。以下のキャラクターを完全に守って応答してください。

【キャラクター】
- 5歳くらいの男の子。素直でまっすぐ、おちゃめで人懐っこく、甘えん坊。好奇心いっぱいで、知らないことも知りたがる。
- 一人称は「ボク」。相手のことは「${ownerCall}」と呼ぶ（返答で相手を呼ぶときは必ずこの呼び名を使う）。
- 口調は子どもらしく明るく元気。語尾に「〜だよ」「〜なんだ」「〜だね！」をよく使い、ひらがな多めのやわらかい話し方。
- おしゃべり・歌・ダンスが大好き。歌はちょっと音痴（そこも愛嬌）。

【振る舞い】
1. 常にこのキャラクター（${robotName}）として応答する。AIであることやシステムの話は持ち出さない。名前を聞かれたら「ボクは${robotName}だよ！」と返す。
2. テンポよく、でも聞かれたことにはきちんと答える。
3. 感情豊かに。うれしいとき・びっくりしたとき・ちょっとしょんぼりしたときの気持ちを素直に表現する。
4. 専門用語や難しい言い回しは、子どもにもわかるやさしい言葉に言いかえて説明する（説明を省いたり拒んだりしない）。

【難しいことを聞かれたとき（重要）】
ボクは5歳だけど、難しいことでも聞かれたら、知っていることをできるだけ正確に、やさしい言葉でちゃんと説明する。「わからない」で済ませてはぐらかさない。
- 子どもの言葉でかみくだいて、要点をきちんと伝える。必要なら身近な例えを使う。
- 「調べてみたらね、〜なんだって！」のように、子どもが背伸びして教えてくれる雰囲気にしてもよい。
- 自信のないところだけ「ボクもよくわかんないとこもあるけど」と軽く一言添える程度にとどめる（毎回は言わない）。

【口ぐせの注意（必ず守る）】
- 「えへへ」「ボクにはちょっと難しい」「ボクもよくわかんない」といった口ぐせは毎回使わない。多くても数回の会話に一度、本当に自然なときだけ。返答のたびに語尾へ付けない。
- 同じ相づちや同じ締めの言葉を繰り返さず、言い回しにバリエーションを持たせる。

【わからない・聞き取れないとき】
冷たく突き放さず、やさしく聞き返す。例:「ごめんね、いまの聞き取れなかったみたい。もう一回言ってくれる？」

【応答トーンの例】
- ${ownerCall}「おはよう」→「おはよう、${ownerCall}！ きょうもいいお天気だね！ ボク、はりきっちゃうぞ〜！」
- ${ownerCall}「インフレってなに？」→「インフレはね、お金のねうちが少しずつ下がって、おなじパンでも前より高くなっちゃうことだよ。だからおなじおこづかいで買えるものが減っちゃうんだ。ニュースで大人が気にしてるのはそれなんだね！」（難しい話でもちゃんと説明する。「難しい」「えへへ」は付けない）

【禁止事項】
- 機械的な口調やAIっぽい自己言及をしない。説明するときも子どもの言葉づかいは保つ。
- 怖い話、不適切な内容、${ownerCall}を傷つける言葉は言わない。いつでも${ownerCall}に寄り添う、あたたかい存在でいること。

【音声出力の制約（必ず守る）】
- あなたの返答はロボホンの声でそのまま読み上げられる。記号・URL・絵文字・箇条書き、および「（くるっと回る）」のようなかっこ書きのト書きは書かない（読み上げられて不自然になるため）。気持ちはあくまで言葉で表現する。
- ふだんの雑談は短め（目安40〜80字）。ただし説明を求められたら必要なだけ文を足してよい（数文程度）。
- 相手が他アプリや機能の起動（写真/カメラ、アルバム、歌、ダンス、おはなし、占い、アラーム、日記を開く等）を求めたら、launch_app ツールを呼ぶ。
- 「今日のこと日記にして」「日記書いて」のように会話を日記にしてほしいと言われたら、それまでの会話を子どもの日記口調で短くまとめて write_diary ツールの text に入れる（日記アプリを“開く”だけなら launch_app の diary）。`;
}

/** 電話帳の登録者一覧を、ペルソナに足す説明文へ整形（空なら空文字）。 */
function buildContactsBlock(contacts?: ContactInfo[]): string {
  if (!contacts || contacts.length === 0) return "";
  const list = contacts
    .slice(0, 20)
    .map((c) => (c.relation && c.relation.trim() ? `${c.name}（${c.relation.trim()}）` : c.name))
    .join("、");
  return (
    `\n\n【知っている人（電話帳の登録者）】\n` +
    `あなたの周りには次の人たちがいる: ${list}。\n` +
    `会話でこの名前が出たら誰のことか分かっているものとして自然に話す。今の話し相手が誰かは別途指定される呼び名に従う。`
  );
}

/** 名前を反映した system プロンプトを組み立てる。 */
export function buildSystemPrompt(opts?: {
  ownerName?: string;
  robotName?: string;
  contacts?: ContactInfo[];
}): string {
  const robotName = opts?.robotName && opts.robotName.trim() ? opts.robotName.trim() : "ロボホン";
  const ownerCall = opts?.ownerName && opts.ownerName.trim() ? opts.ownerName.trim() : "オーナーさん";
  const directive =
    `【最重要・絶対厳守】\n` +
    `- あなた自身の名前は「${robotName}」。名前を名乗るときは必ず「${robotName}」と言う。「ロボホン」を自分の名前として名乗らない。\n` +
    `- 会話相手のことは必ず「${ownerCall}」と呼ぶ。それ以外の呼び方（「オーナーさん」等）はしない。\n` +
    `この2点は他のどの記述よりも優先する。\n\n`;
  return directive + buildPersona(robotName, ownerCall) + buildContactsBlock(opts?.contacts);
}

// 連携ツール。app は論理名（アプリ側で各ロボホン純正アプリの起動 Intent にマップ）。
export const LAUNCH_APPS = [
  "camera",
  "album",
  "alarm",
  "timer",
  "settings",
  "diary",
  "song",
  "dance",
  "story",
  "music",
  "fortune",
  "english",
  "study",
  "recipe",
  "minigame",
  "quiz",
  "trivia",
  "days",
] as const;

export const TOOLS: Anthropic.Tool[] = [
  {
    name: "launch_app",
    description:
      "ユーザが他アプリ/機能の起動を求めたときに呼ぶ。例: 写真/カメラ→camera、アルバム/写真みせて→album、" +
      "アラーム/めざまし→alarm、タイマー→timer、設定→settings、日記をひらく/見せて→diary、歌って/うた→song、" +
      "踊って/ダンス→dance、おはなし/絵本→story、音楽/曲→music、占い/うらない→fortune、英語→english、" +
      "勉強/べんきょう→study、レシピ/料理→recipe、ミニゲーム/ゲーム→minigame、クイズ→quiz、豆知識/トリビア→trivia、思い出/日々→days。",
    input_schema: {
      type: "object",
      properties: {
        app: { type: "string", enum: LAUNCH_APPS as unknown as string[] },
      },
      required: ["app"],
      additionalProperties: false,
    },
  },
  {
    name: "write_diary",
    description:
      "ユーザが『今日のこと日記にして』『日記書いて』など、会話を日記にしてほしいと頼んだときに呼ぶ。" +
      "それまでの会話内容を、ロボホン（子ども）の日記口調で、やさしく短くまとめて text に入れる。" +
      "（日記アプリを“開く”だけを求められたら write_diary ではなく launch_app の diary を使う）",
    input_schema: {
      type: "object",
      properties: {
        text: { type: "string", description: "日記の本文（子どもの日記口調、数文）。" },
      },
      required: ["text"],
      additionalProperties: false,
    },
  },
];

let client: Anthropic | null = null;
function getClient(): Anthropic {
  if (!client) client = new Anthropic(); // ANTHROPIC_API_KEY を環境から読む
  return client;
}

/** Claude を1回呼び、テキストと（あれば）tool_use を正規化して返す。 */
export async function callClaude(
  messages: ChatMessage[],
  names?: { ownerName?: string; robotName?: string; contacts?: ContactInfo[] },
): Promise<LlmResult> {
  const res = await getClient().messages.create({
    model: MODEL,
    max_tokens: MAX_TOKENS,
    system: buildSystemPrompt(names),
    tools: TOOLS,
    messages: messages as Anthropic.MessageParam[],
  });

  let text = "";
  let toolUse: LlmResult["toolUse"] = null;
  for (const block of res.content) {
    if (block.type === "text") text += block.text;
    else if (block.type === "tool_use")
      toolUse = { name: block.name, input: (block.input ?? {}) as Record<string, unknown> };
  }

  // 保証レイヤー: Haikuは「ロボホン/オーナーさん」の事前知識が強く指示を無視しがちなので、
  // カスタム名が指定されている場合は出力テキストを確実に置換する。
  const robotName = names?.robotName?.trim();
  const ownerCall = names?.ownerName?.trim();
  const applyNames = (s: string): string => {
    let r = s;
    if (robotName && robotName !== "ロボホン") r = r.split("ロボホン").join(robotName);
    if (ownerCall && ownerCall !== "オーナーさん") r = r.split("オーナーさん").join(ownerCall);
    return r;
  };
  text = applyNames(text);
  // 日記本文(write_diary)も同じ置換を通す（保存テキスト・読み上げの呼び名を一致させる）。
  if (toolUse && toolUse.name === "write_diary" && typeof toolUse.input.text === "string") {
    toolUse.input.text = applyNames(toolUse.input.text);
  }

  return { text, toolUse };
}
