# RoBoHoN HVML 2.0 リファレンス (HVML Reference)

> 原典: **RoBoHoN HVML 2.0 リファレンス** Version 2.0.0 / Last update 2019/8/7 / SHARP CORPORATION
> このドキュメントは公式 PDF (`RoBoHoN_HVML_Reference.pdf`, 全 65 ページ) を構造化して再編したものです。Claude が独自 LLM 会話アプリ向けの HVML を記述する際の知識ベースとして使用します。
> 日本語を主とし、英語の補足を併記しています。技術的詳細は省略せず保持しています。

---

## 概要 (Overview)

**HVML (Hyper Voice Markup Language)** は XML 1.0 をベースとしたマークアップ言語で、電子機器(ロボホン)と人間との「対話シナリオ」を表現するために設計されています。音声対話シナリオを **「ユーザ発話 (user utterance)」** と **「対応するアクション (action)」** の組み合わせで表現します。

特徴:
- 雑談、ニュース読み上げ、天気予報のお知らせなどを実現できる。
- 機器を発話 (TTS) させるだけでなく、**モーション(動作)** や **アプリへのイベント通知** も記述可能。
- ロボホンとの対話を実現するには、HVML が記述されたファイルを登録し、その HVML を実行するという手順を踏む。

本資料の対象は **ビルド番号 03.01.00 以降** のロボホン。第1世代 (SR01MW/SR02MW) と第2世代 (SR03M/SR04M/SR05M) の両方を扱います。

### 用語 (Terminology)

| 用語 | 意味 |
|---|---|
| ロボホン | ロボホン(3G・LTE)、ロボホン(Wi-Fi)、ロボホンライトの総称 |
| 第1世代ロボホン | SR01MW / SR02MW |
| 第2世代ロボホン | SR03M / SR04M / SR05M |
| HVML | Hyper Voice Markup Language の略語 |
| TTS | Text-to-Speech。音声合成によりテキストを読み上げる機能 |
| 対話 (dialogue) | ユーザの発話 ⇒ ロボホンの応答のやり取り。HVML の実行開始からすべての topic 実行が終了するまでを指す |

---

## 目次 (Table of Contents)

1. [HVML の基本形式](#1-hvml-の基本形式)
2. [HVML の実行フロー](#2-hvml-の実行フロー)
3. [タグ階層図](#3-タグ階層図)
4. [タグリファレンス (全要素)](#4-タグリファレンス-全要素)
   - [hvml](#hvml-タグ) / [head](#head-タグ) / [body](#body-タグ)
   - [producer](#producer-タグ) / [version](#version-タグ) / [tool_version](#tool_version-タグ) / [description](#description-タグ) / [scene](#scene-タグ)
   - [situation](#situation-タグ) / [accost](#accost-タグ)
   - [topic](#topic-タグ) / [rule](#rule-タグ) / [condition](#condition-タグ) / [case](#case-タグ)
   - [action](#action-タグ) / [a](#a-タグ) / [next](#next-タグ)
   - [speech](#speech-タグ) / [emotion](#emotion-タグ) / [wait](#wait-タグ)
   - [behavior](#behavior-タグ) / [モーションID一覧](#モーションid-一覧)
   - [control](#control-タグ) / [data](#data-タグ)
   - [memory](#memory-タグ)
5. [演算子 (Operators)](#5-演算子-operators)
6. [変数 (Variables)](#6-変数-variables)
   - [音声認識変数 (Lvcsr ほか)](#61-音声認識--ユーザ発話変数)
   - [時制変数](#62-時制取得用変数)
   - [型変換・判定変数](#63-時制判定用変数)
   - [個人情報・環境変数](#65-個人情報を扱う変数)
   - [memory 変数 / 追加変数](#67-memory-変数)
   - [変数型 (日時型・リスト型)](#69-変数型)
   - [禁止文字](#610-禁止文字)
7. [環境検知イベント (Environment Events)](#7-環境検知イベント-environment-events)
8. [付録: 音声認識のしくみ要点](#8-付録-音声認識のしくみ要点)

---

## 1. HVML の基本形式

一つの HVML ファイルは `head` と `body` で構成されます。

- **head**: HVML ファイル自体に付随する情報、および HVML が選択されるために必要な情報を記述。
- **body**: 実行する内容を記述。body 内は **一つ以上の `topic`** で構成される。`topic` は実行内容の区切り。

### 雑談 HVML シナリオサンプル (verbatim)

ユーザが「こんにちは」と話しかけると、ロボホンが「こんにちは。今日もいい天気ですね。」と TTS で応答する最小例。

```xml
<hvml version="2.0">
<head>
  <version value="1.0"/>
    <producer>jp.co.sharp.producer</producer>
    <scene value="jp.co.sharp.producer.sample"/>
    <situation topic_id="0001" trigger="user-word">${Lvcsr:Basic} eq こんにちは</situation>
</head>
<body>
    <topic id="0001" listen="false">
      <action index="1">
        <speech>こんにちは。今日もいい天気ですね。</speech>
      </action>
  </topic>
    <topic id="0002" listen="false">
      …
    </topic>
</body>
</hvml>
```

topic 実行後にユーザ発話を受け付けて次の topic に遷移させることができ、別の HVML ファイルの topic にも遷移可能です。

---

## 2. HVML の実行フロー

### 実行用サンプル (verbatim)

```xml
<hvml version="2.0">
<head>
  <version value="1.0"/>
    <producer>jp.co.sharp.producer</producer>
    <scene value="jp.co.sharp.producer.sample"/>
    <situation topic_id="0001" trigger="user-word" >${Lvcsr:Basic} eq こんにちは</situation>
</head>
<body>
    <topic id="0001" listen="true">
      <action index="1">
        <speech>こんにちは。今日もいい天気ですね。</speech>
      </action>
      <a href="#0002">
        <situation trigger="user-word">${Lvcsr:Basic} eq そうですね</situation>
      </a>
      <a href="#0003">
        <situation trigger="user-word">${Lvcsr:Basic} eq 雨ですよ</situation>
      </a>
      <a href="#0004" type="default"/>
      <next href="#0005" type="default"/>
  </topic>
    <topic id="0002" listen="false">
      <action index="1">
        <speech>こんにちは。今日もいい天気ですね。</speech>
      </action>
    </topic>
  …
</body>
</hvml>
```

### 実行ステップ

1. **起動契機 (idle 状態からの開始)** — HVML が実行されていない状態 (idle) から、以下のいずれかで実行が開始される:
   - ユーザによる発話
   - 環境検知イベント (ロボホンが振られた、など。→ [7章](#7-環境検知イベント-environment-events))
   - **accost イベント**による強制 HVML 実行 (→ [accost タグ](#accost-タグ))

2. **topic の選択** — 起動契機が発生すると、登録されているすべての HVML の head 情報から topic が選択される。選択条件:
   - いずれかの `scene` タグの value が、現在シーンとして設定されている状態であること。
   - ユーザ発話契機・環境検知イベント契機の場合、`situation` の条件式に一致すること。複数一致する場合は **priority が最も高い** situation が選択される。priority も同一なら **ランダム**。
   - accost イベント契機の場合、`accost` タグの `word` 属性の値とイベント名が一致すること。

3. **action 実行** — topic が選択されると、topic 内の `action` が `index` 順に実行される。action にはロボホンの発話、モーション再生などが含まれる。

4. **action 終了後の遷移**:
   - `listen="false"` の場合 — `next` タグがあれば href の topic へ遷移。なければ HVML の実行を終了。
   - `listen="true"` の場合 — 次のユーザ発話を待ち受ける。ユーザ発話を受けると、`a` タグおよび他の HVML の head から topic が選択され、ステップ 3 に戻る。
     - 複数条件が合致する場合、最も priority が高い situation の topic が選択される。
     - priority も同一の場合は、**topic に記述されている a タグの記載順 ⇒ HVML の head** という優先順で選択される。
     - 条件が合致する topic が無かった場合は、そのまま対話を終了する。

---

## 3. タグ階層図

公式の階層図 (verbatim):

```
<hvml> : <head> | <body>
    - <head> : <producer> | <scene> | <situation> | <accost> | <version> | <description> | <tool_version>
    - <body> : <topic>
        - <topic> : <rule> | <case> | <action> | <a> | <next>
           - <rule> : <condition>
           - <case> : <action> | <a> | <next>
            - <action> : <speech> | <behavior> | <control> | <memory>
                - <speech> : <emotion> | <wait>
                - <control> : <data>
            - <a> : <situation>
```

### タグ一覧 (アルファベット順)

`<a>` `<accost>` `<action>` `<behavior>` `<body>` `<case>` `<condition>` `<control>` `<data>` `<description>` `<emotion>` `<head>` `<hvml>` `<memory>` `<next>` `<producer>` `<rule>` `<scene>` `<situation>` `<speech>` `<tool_version>` `<topic>` `<version>` `<wait>`

---

## 4. タグリファレンス (全要素)

凡例: 「必須」=required, 「任意」=optional。

### hvml タグ

HVML ファイルのルート要素。

| 項目 | 内容 |
|---|---|
| 書式 | `<hvml version="…">~</hvml>` |
| 親要素 | なし |
| 子要素 | `<head>`, `<body>` |
| 属性 | `version` (必須) |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `version` | 必須 | string | `"2.0"` | HVML のバージョン。**"2.0" を指定する。** |

---

### head タグ

HVML ファイル自体に付随する情報、HVML が選択されるために必要な情報を記載。

| 項目 | 内容 |
|---|---|
| 書式 | `<head>~</head>` |
| 親要素 | `<hvml>` |
| 子要素 | `<producer>`, `<scene>`, `<situation>`, `<accost>`, `<version>` (階層図上では `<description>`, `<tool_version>` も head 配下) |
| 属性 | なし |

---

### body タグ

HVML で実行する内容を記述。一つ以上の `topic` から構成される。

| 項目 | 内容 |
|---|---|
| 書式 | `<body>~</body>` |
| 親要素 | `<hvml>` |
| 子要素 | `<topic>` |
| 属性 | なし |

---

### producer タグ

HVML ファイルの発行元を表す。要素の内容に、HVML を登録したアプリの **package 名** を記載する。

| 項目 | 内容 |
|---|---|
| 書式 | `<producer>~</producer>` |
| 親要素 | `<head>` |
| 子要素 | なし |
| 属性 | なし |

```xml
<hvml version="2.0">
<head>
    <producer>jp.co.sharp.android.sampleProducer</producer>
  …
</head>
  …
</hvml>
```

**注意**: HVML ファイルには必ず本タグを記述すること。一つのファイルに複数記述しないこと。

---

### version タグ

HVML ファイル管理用のバージョン情報。HVML の登録/更新時に利用される。

| 項目 | 内容 |
|---|---|
| 書式 | `<version value="…"/>` |
| 親要素 | `<head>` |
| 子要素 | なし |
| 属性 | `value` (必須) |

| 属性 | 必須 | 型 | 意味 |
|---|---|---|---|
| `value` | 必須 | 正の整数または小数 | バージョン番号。同一ファイル名の HVML が既に登録済みの場合、**value 値が登録済みの値より大きい場合のみ上書き登録**される。 |

```xml
<!-- ○ 正しい version の記載 -->
<version value="1.0.1"/>
<version value="1.0"/>

<!-- × 誤った version の記載 -->
<version value="2.01"/>
```

> 注: ファイル管理用の `<version>` (head 配下) と、ルート要素 `<hvml version="2.0">` の `version` 属性は別物。

---

### tool_version タグ

タグ一覧および階層図 (head 配下) に登場するが、HVML 2.0 リファレンス本文には専用の属性説明セクションがない。ツール (オーサリング) 用のバージョン情報を保持するためのタグと考えられる。HVML 実行時の動作には影響しないとみられる。実運用では使用しなくても差し支えない。

---

### description タグ

HVML ファイル自体の説明を記述する用途。**HVML 実行時の動作には影響しない。**

| 項目 | 内容 |
|---|---|
| 書式 | `<description>~</description>` |
| 親要素 | `<head>` |
| 子要素 | なし |
| 属性 | なし |

```xml
<hvml version="2.0">
<head>
  <description>テスト用シナリオ</description>
  …
</head>
<body>
  …
</body>
</hvml>
```

---

### scene タグ

HVML が実行されるシーンを設定する。シーンはロボホンの内部状態として保持されるパラメータで、実行する HVML を絞り込む用途で使用する。

- ホーム画面ではシーンとして `"home"` が設定される。アプリ起動中は各アプリがシーンを設定する。
- シーンは同時に複数設定可能。
- HVML 実行時、記載された scene タグの value 値が現在設定されているシーンと一致している HVML が選択される。
- シーンに合致しない HVML が(契機から)選択されることはないが、**他 HVML の `a`/`next` タグによる topic 遷移の場合は scene タグを参照しない** (明示的にファイル名・topic id を指定しているため)。ただしこの場合も自アプリ外の HVML ファイルは実行できない。

| 項目 | 内容 |
|---|---|
| 書式 | `<scene value="…"/>` |
| 親要素 | `<head>` |
| 子要素 | なし |
| 属性 | `value` (必須) |

| 属性 | 必須 | 型 | 意味 |
|---|---|---|---|
| `value` | 必須 | string | 実行させるシーン名。ホーム画面で実行する HVML は `"home"` を指定。それ以外はアプリが設定するシーン名を指定。 |

```xml
<hvml version="2.0">
<head>
    <scene value="jp.co.sharp.android.testtest"/>
    <scene value="jp.co.sharp.android.testtest.SettingActivity"/>
  …
</head>
  …
</hvml>
```

**注意**: 必ず記述すること。複数記述可能だが、`"home"` を指定するファイルには他の scene を設定しないこと。

---

### situation タグ

実行する topic を選択するための **契機 (trigger)** および **条件 (条件式)** を記述する。要素の内容に条件式を書き、結果が真 (true) なら合致と判定される。常に真にしたい場合は要素の内容に `true` と書く。属性のルールは親要素によって異なる。

| 項目 | 内容 |
|---|---|
| 書式 | `<situation trigger="…" ~>~</situation>` |
| 親要素 | `<head>`, `<a>` |
| 子要素 | なし |
| 属性 | `trigger` (必須), `value`, `topic_id`, `priority` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `trigger` | 必須 | string | `user-word`, `env-event` | 契機の種別。`user-word`=ユーザ発話を契機に条件式を評価。`env-event`=`value` で指定された環境検知イベントを契機に評価。 |
| `value` | 任意/条件付 | string | 環境検知イベント名 (→ 7章) | `trigger="env-event"` の場合に環境検知イベントの種別を記載。`trigger="user-word"` の場合は不要。 |
| `topic_id` | head 配下では必須 | string | topic の id | head 配下では合致時に遷移する topic の id を指定。`a` 配下では不要 (遷移先は a の href に従う)。 |
| `priority` | scene="home" では必須 / 他は任意 | number | home: 78〜84 / それ以外: 61〜90 | 起動時の優先度。条件に合致する situation が複数ある場合に実行 topic を決定。**数字が小さい方が優先**。 |

**priority の既定値ルール**:
- `scene="home"` の HVML では本属性は **必須**。
- それ以外の HVML で未記載の場合:
  - head の situation タグ → **75** と扱われる。
  - a タグ内の situation タグ → その a タグを含む topic が実行された際の priority (実行契機となった accost / situation の priority) と同じ値と扱われる。
- a タグ内で priority を指定しない場合、その時点の priority が引き継がれる。

```xml
<hvml version="2.0">
<head>
    <situation topic_id="0001" trigger="user-word" priority="78">
誕生日 in ${Lvcsr:Basic} and 覚えて in ${Lvcsr:Basic}
 </situation>
  …
</head>
  …
</hvml>
```

```xml
<!-- 環境検知イベント契機の例 -->
<hvml version="2.0">
<head>
<situation topic_id="0001" trigger="env-event" value="shake" priority="75">true</situation>
  …
</head>
  …
</hvml>
```

**注意**: 一つの親要素内に複数記述可能。head の `trigger="user-word"` に条件式を書く際の記載順 (パフォーマンス上の推奨。→ [6.11](#611-situation-に変数を記載する際の注意点)):
> ユーザ発話変数 (Lvcsr:Basic / Lvcsr:Kana) > `${Hour}` > その他の変数 (memory_p など)

---

### accost タグ

アプリから HVML の topic を実行したい場合に使用する。アプリで word (accost 名称) を実行すると、定義された topic が実行される。**situation と異なり、要素の内容に条件式を記述することはできない。**

| 項目 | 内容 |
|---|---|
| 書式 | `<accost topic_id="…" word="…" ~/>` |
| 親要素 | `<head>` |
| 子要素 | なし |
| 属性 | `word` (必須), `topic_id` (必須), `priority` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `word` | 必須 | string | package 名から始まる一意な文字列 | accost 名称。**必ず一意にする** (重複時の動作は不定)。 |
| `topic_id` | 必須 | string | topic の id | accost イベント受信時に遷移する topic の id。 |
| `priority` | 任意 | number | 61〜90 | HVML 実行の優先度。accost またはsituation で実行された topic が実行中の場合、指定 priority がそれより高ければ実行中の HVML を中断して遷移する。**数字が小さい方が優先**。未記載時は **75**。 |

```xml
<accost topic_id="1" word="jp.co.sharp.rb.sample.accost" priority="75"/>
```

**注意**: head に accost の記述があっても、scene が一致しなければ topic は実行されない。

---

### topic タグ

HVML の body 部を構成する要素。実行したい処理をブロック化する。id を指定することで situation / accost 等から実行できる。

| 項目 | 内容 |
|---|---|
| 書式 | `<topic id="…" listen="…" ~>~</topic>` |
| 親要素 | `<body>` |
| 子要素 | `<rule>`, `<case>`, `<action>`, `<a>`, `<next>` |
| 属性 | `id` (必須), `listen` (必須), `listen_ms`, `dict` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `id` | 必須 | string | 任意 (ファイル内で一意) | situation / accost で遷移先指定に使用。ファイル内で重複しないこと。 |
| `listen` | 必須 | string | `true`, `false` | ユーザ発話を受け付けるか。`true`=全 action 実行後に発話待受、ユーザ発話を受けると a タグに従い遷移。`false`=action 実行後 next があれば遷移、なければ対話終了。 |
| `listen_ms` | 任意 | number (msec) | — | `listen="true"` 時の発話待受最大時間 (msec)。未記載時は **10秒 (10000msec)**。時間内に発話が無ければ next があれば遷移、なければ対話終了。`listen="false"` では無視される。 |
| `dict` | 任意 | string | `Reply` | 肯定/否定の聞き取りを行う場合に `"Reply"` を指定。詳細は参考資料[3]。 |

```xml
<hvml version="2.0">
<head>
  …
</head>
<body>
    <topic id="0001" listen="true">
      <action index="1">
        <speech>今日はいい天気ですか？</speech>
      </action>
    …
  </topic>
    <topic id="0002" listen="false">
    …
  </topic>
  …
</body>
</hvml>
```

> 補足 ([next タグ注意事項](#next-タグ)): `listen="true"` の topic では action 実行後、**10秒間**ユーザ発話が無ければ next の指定先に遷移する。

---

### rule タグ

topic 実行時、その時の条件によって実行するアクションを変えたい場合に使用する。必ず `condition` (条件) と `case` (実行内容) とセットで使う。

| 項目 | 内容 |
|---|---|
| 書式 | `<rule>~</rule>` |
| 親要素 | `<topic>` |
| 子要素 | `<condition>` |
| 属性 | なし |

**注意**: 子要素として必ず一つ以上の condition を記述すること。

```xml
<rule>
    <condition case_id="1" weight="3">${Hour} ge 20</condition>
    <condition case_id="2" weight="1">${Hour} ge 22</condition>
    <condition case_id="3" weight="1">${Hour} ge 22</condition>
   <condition case_id="4" priority="20">true</condition>
 </rule>
```

---

### condition タグ

topic 内で実行する処理 (case) を決定するための条件を記載する。要素の内容に条件式を書き、結果が真 (true) なら合致。常に真にするには `true` と書く。

| 項目 | 内容 |
|---|---|
| 書式 | `<condition case_id="…" ~>~</condition>` |
| 親要素 | `<rule>` |
| 子要素 | なし |
| 属性 | `case_id` (必須), `weight`, `priority` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `case_id` | 必須 | string | case の id | 条件合致時に遷移する case の id。同一 rule 内で複数 condition が同じ case_id を指定してもよい。 |
| `weight` | 任意 | number | 1 以上 | case が選択される比率。未指定時は **10**。 |
| `priority` | 任意 | number | 1〜99 | case 選択時の優先度。**数字が小さい方が優先**。未指定時は **10**。 |

**選択ロジック**:
- 合致する condition が一つ → その case_id の case に遷移。
- 複数合致 → **priority が最も高い** condition の case に遷移。
- priority も同じ → **weight の比率に従ってランダム**で選択。
- condition の記載順は選択確率に影響しない。
- 合致する condition が一つも無い場合 → **その時点で対話が終了**。対話が終了しないよう、条件式 `true` の condition を必ず一つは入れること。
- limit 回数制限がかかっている case は選択対象から除外される (→ case タグ)。

```xml
<rule>
    <condition case_id="1" weight="3">${Hour} ge 20</condition>
    <condition case_id="2" weight="1">${Hour} ge 22</condition>
    <condition case_id="3" weight="1">${Hour} ge 22</condition>
   <condition case_id="4" priority="20">true</condition>
 </rule>
```
- 20〜22時: case 1 が実行される。
- 22〜24時: 60% で case1、20% で case2、20% で case3 (weight 3:1:1)。
- 0〜20時: case4 が実行される。

---

### case タグ

rule に対応する操作を記述する。topic 内で rule の記述に従って case が選択・実行される。選択されなかった case は実行されない。

| 項目 | 内容 |
|---|---|
| 書式 | `<case id="…" ~>~</case>` |
| 親要素 | `<topic>` |
| 子要素 | `<action>`, `<a>`, `<next>` |
| 属性 | `id` (必須), `limit`, `cleardays` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `id` | 必須 | string | 同一 topic 内で一意 | rule で選択される case を識別する id。 |
| `limit` | 任意 | number | 1 以上 | 回数制限。指定回数実行されると以降そのcase は選択されなくなる。`cleardays` で解除。 |
| `cleardays` | 任意 | number | 1 以上 | limit 制限を解除するまでの日数。 |

```xml
<body>
    <topic id="1" listen="false">
        <rule>
            <condition case_id="1" priority="1">true</condition>
            <condition case_id="2" priority="2">true</condition>
        </rule>
        <case id="1" limit="3" cleardays="1">
            <action index="1">
                <speech>おかえり。今日も頑張ったね</speech>
            </action>
        </case>
        <case id="2">
            <action index="1">
                <speech>おかえり</speech>
            </action>
        </case>
    </topic>
</body>
```
- id=1 の topic が実行された場合: 1〜3回目は case id=1「おかえり。今日も頑張ったね」、4回目以降は case id=2「おかえり」。3回目の発話から1日 (24時間) 経過するまでは「おかえり」、1日経過後は再び「おかえり。今日も頑張ったね」。

**注意**: case に limit がかかると選択されなくなる。シナリオが終了しないよう、limit 指定をしない case も併記すること。

---

### action タグ

HVML 実行時に実施される操作を記述する。一つの topic 内に複数記述可能。子要素として `speech`, `behavior`, `control`, `memory` を持つ。これらは一つの action 内に併記可能だが、**memory タグ以外は同一タグを一つの action 内に一つしか記述できない** (memory は複数可)。

| 項目 | 内容 |
|---|---|
| 書式 | `<action index="…">~</action>` |
| 親要素 | `<topic>`, `<case>` |
| 子要素 | `<speech>`, `<behavior>`, `<control>`, `<memory>` |
| 属性 | `index` (必須) |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `index` | 必須 | number | 1 以上 | action の再生順序。**数字が小さい順**に再生。同じ数字の順番は保証されない。 |

```xml
<topic id="0001" listen="true">
<action index="1">
    <speech>こんにちは</speech>
</action>
<action index="2">
    <speech>今日はいい天気ですね</speech>
</action>
</topic>
```

**注意**: 一つの action 内に記述されたタグの内容は、記述順によらず **同時に実行される** (発話とモーションの同時再生など)。

---

### a タグ

`a` はアンカー (anchor) の略。指定した遷移先にジャンプする。一つの topic または case に複数記述可能。**`listen="false"` の topic では a タグは無視される。**

| 項目 | 内容 |
|---|---|
| 書式 | `<a href="…">~</a>` / `<a href="…" ~/>` |
| 親要素 | `<topic>`, `<case>` |
| 子要素 | `<situation>` |
| 属性 | `href` (必須), `type` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `href` | 必須 | string | `HVMLファイル名#topic id` または `#topic id` | 遷移先。別ファイルは `ファイル名#topic id`、同一ファイル内は `#topic id`。 |
| `type` | 任意 | string | `default` | 他のアンカーに合致しない場合の遷移先に `type="default"` を指定。 |

**href フォーマット例**: `href="jp_co_sharp_rb_sample_1.hvml#001"`

```xml
<hvml version="2.0">
<head>
  …
</head>
<body>
    <topic id="0001" listen="true">
      <action index="1">
        <speech>今日はどこに行きますか？</speech>
      </action>
      <a href="#tokyo">
        <situation trigger="user-word">${Lvcsr:Basic} eq 東京</situation>
        <situation trigger="user-word">${Lvcsr:Basic} eq 東京都</situation>
      </a>
      <a href="#osaka">
        <situation trigger="user-word">${Lvcsr:Basic} eq 大阪</situation>
        <situation trigger="user-word">${Lvcsr:Basic} eq 大阪府</situation>
      </a>
      <a href="#retry" type="default" />
    </topic>
    <topic id="tokyo" listen="false">
    …
  </topic>
  …
</body>
</hvml>
```

**注意**: href が同じ situation が複数ある場合、一つの a タグ内に複数の situation を含めることが可能。上記の `#tokyo` の例は以下と同じ動作:

```xml
<a href="#tokyo">
  <situation trigger="user-word">${Lvcsr:Basic} eq 東京</situation>
</a>
<a href="#tokyo">
  <situation trigger="user-word">${Lvcsr:Basic} eq 東京都</situation>
</a>
```

---

### next タグ

topic 実行後、対話を終了せずに別の topic に遷移させたい場合に遷移先を記載する。

| 項目 | 内容 |
|---|---|
| 書式 | `<next href="…" type="…"/>` |
| 親要素 | `<topic>`, `<case>` |
| 子要素 | なし |
| 属性 | `href` (必須), `type` (必須) |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `href` | 必須 | string | `HVMLファイル名#topic id` または `#topic id` | 遷移先。別ファイルは `ファイル名#topic id`、同一ファイル内は `#topic id`。 |
| `type` | 必須 | string | `default` | `type="default"` を指定。 |

```xml
<hvml version="2.0">
<head>
  …
</head>
<body>
    <topic id="0001" listen="false">
      <action index="1">
        <speech>こんにちは</speech>
      </action>
      <next href="#0002" type="default" />
    </topic>
    <topic id="0002" listen="false">
      <action index="1">
        <speech>今日もいい天気ですね</speech>
      </action>
    </topic>
  …
</body>
</hvml>
```

**注意**:
- `listen="false"` の topic では、action 実行直後に href の指定先に遷移する。
- `listen="true"` の topic では、action 実行後 **10秒間**ユーザからの発話が無ければ href の指定先に遷移する。

---

### speech タグ

本タグの内容に文を記述することで、TTS を使ってロボホンに発話させる。

| 項目 | 内容 |
|---|---|
| 書式 | `<speech>~</speech>` |
| 親要素 | `<action>` |
| 子要素 | `<emotion>`, `<wait>` |
| 属性 | なし |

```xml
<topic id="0001" listen="true">
<action index="1">
    <speech>おはよう</speech>
     <behavior type="normal" id="0x040e01"/>
</action>
</topic>
```

**注意**:
- 本タグの内容に HVML で規定している以外のタグを挿入しないこと。
- 一つの action に対して speech と behavior など他タグを併記可能。ただし speech タグ自体は一つの action に複数記述しないこと。
- **発話内に変数を埋め込める**: speech タグの内容は「変数を使用できる箇所」の一つ ([6.10](#610-変数を使用できる箇所))。例: `<speech>15日後は${memory_t:Date}だよ</speech>`。

> **発話の文字数上限について (重要)**: 本 HVML 2.0 リファレンス (PDF) には、speech タグ一発話あたりの **明示的な文字数上限の記載はありません**。文字数制限は本仕様書には規定されていません。実機・API 側に運用上の制約が存在する可能性があるため、長文を読み上げる場合は `wait` タグで区切る、複数 action / topic に分割するなどの設計が無難です (関連する数値制限として、ユーザ発話待受の `listen_ms` 既定値 10000msec があります)。

---

### emotion タグ

ロボホンが嬉しそう/悲しそうに話すといった、発話時の感情 (TTS パラメータ) を設定する。本タグを指定していない発話は **`type="happiness"`, `level="1"`** が設定される。

| 項目 | 内容 |
|---|---|
| 書式 | `<emotion type="…" ~>~</emotion>` |
| 親要素 | `<speech>` |
| 子要素 | なし |
| 属性 | `type` (必須), `level` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `type` | 必須 | string | `happiness`, `sadness`, `anger` | 感情の種別。happiness=嬉しそう、sadness=悲しそう、anger=怒った声。 |
| `level` | 任意 | number | 1〜4 | 感情の度合い。数字が大きいほど感情値が高い。未指定時は **1**。 |

```xml
<topic id="0010" listen="false">
  <action index="1">
    <speech><emotion type="happiness" level="2">おはよう</emotion></speech>
  </action>
</topic>
```

**注意**: 一つの speech タグ内に複数の emotion タグを挿入可能だが、**入れ子にしないこと**。

---

### wait タグ

ロボホンの発話中に間 (ま) を持たせたい場合に使用する。

| 項目 | 内容 |
|---|---|
| 書式 | `<wait ms="…"/>` |
| 親要素 | `<speech>` |
| 子要素 | なし |
| 属性 | `ms` (必須) |

| 属性 | 必須 | 型 | 意味 |
|---|---|---|---|
| `ms` | 必須 | number (msec) | 間を持たせる時間 (msec)。 |

```xml
<topic id="0010" listen="false">
  <action index="1">
    <speech><emotion type="happiness" level="2">おはよう。</emotion><wait ms="300"/><emotion
type="happiness" level="2">今日もいい天気だね</emotion></speech>
  </action>
</topic>
```

**注意**: 一つの speech タグ内に複数の wait タグを挿入可能。

---

### behavior タグ

発話に合わせて行うモーションを指定する。

| 項目 | 内容 |
|---|---|
| 書式 | `<behavior type="…" id="…"/>` |
| 親要素 | `<action>` |
| 子要素 | なし |
| 属性 | `type` (必須), `id` (必須) |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `type` | 必須 | string | `normal` | `"normal"` を指定する。 |
| `id` | 必須 | string | `assign` / `general` / 個別のモーションID | モーションを指定。`assign`・`general` または個別のモーションID (下表) のいずれか。 |

```xml
<topic id="0001" listen="true">
<action index="1">
    <speech>バイバイ</speech>
     <behavior type="normal" id="0x060009"/>
</action>
</topic>
```

> 注: 使用例では `id="0x060009"` のように **`0x` プレフィックス付き16進数**で指定されている (モーションID `060009` = 右手をあげてふる)。`speech` の例でも `id="0x040e01"` が使われている。モーションに関する詳細は参考資料[3] (アプリ開発ガイドライン) を参照。

#### モーションID 一覧

(モーションID / 説明 / 発話例)

| モーションID | モーションの説明 | 発話例 |
|---|---|---|
| 060000 | 腕を軽く前に出す | 好き！、楽しい！ |
| 060001 | バンザイ | やった！、バンザイ！ |
| 060002 | 両腕体横で左右に振る | びっくりー！、すごーい！ |
| 060003 | 両手上下で抗議 | むかつく |
| 060004 | うなだれ | 悲しい、寂しい |
| 060005 | 両手もじもじ | えへへ |
| 060006 | 腰に手 | えっへん |
| 060007 | お辞儀 | ありがとう |
| 060008 | 浅いお辞儀 | お疲れ |
| 060009 | 右手をあげてふる | バイバイ |
| 06000a | 頷いて右手上げる | うん、〜だよね、〜しよう |
| 06000b | 首を振る | 違うよ、ううん |
| 06000c | 首をかしげる | 分からない、〜かな？ |
| 06000d | 両腕を体の横で前後に振る | 帰る、行く、歩く、走る |
| 06000e | 両手でハンドルをにぎにぎ | 車、運転 |
| 06000f | 両腕を体の横でぶらぶら | ひま |
| 060010 | 頭を左右、両腕を前後に動かす | 忙しい |
| 060011 | 書類や本を読む | 会社、学校、勉強、読書 |
| 060012 | 右手を口元に持っていく | 食べる、ご飯に行く、ランチする |
| 060013 | 上を向いてジョッキを飲み干す | 飲む、飲み会、合コン |
| 060015 | 片腕を枕のようにして首を傾ける | 寝る |
| 060016 | お腹のあたりをさする | お腹すいた、空腹 |
| 060017 | 片手で、顔を仰ぐ | 暑い |
| 060018 | 両手を体の横で震わせる | 寒い、凍える |
| 060019 | あくびをする | 眠い |
| 06001a | 電話を掛ける | 電話、携帯電話 |
| 06001b | おでこを指す | カメラ |
| 06001c | おでこを指す | プロジェクター |
| 06001d | 自分を指す | ぼく、ロボホン |
| 06001e | 相手を指す | 君 |
| 06001f | 両手を体の前で振る | 音楽、ダンス |
| 060021 | 1回頷く | うん |
| 060022 | 2回頷く | うんうん |
| 060023 | 両手を広げる | おめでとう！ |
| 060025 | 2回お辞儀 | あけましておめでとうございます |
| 060026 | 両手を広げた後、右手を前 | トリックオアトリート！ |
| 060027 | 両手を広げる | メリークリスマス！ |
| 060028 | ハグする | 大好き |
| 060029 | 両手を広げて頷く | なるほど、そうなんだ |
| 06002a | ぐったり | ツライ、しんどい、二日酔い |
| 06002b | 両手を目にあてる | 淋しい、メソメソ |
| 06002c | 右手を上げる | おはよう！ |
| 06002d | 両手を広げる（怒） | 大嫌い！、最低！ |
| 06002e | 右手で頭をかく | てへぺろ、あちゃー |
| 060030 | 両手を前で広げる | 大きい、広い |
| 060031 | 両手を前で狭める | 小さい、狭い |
| 060032 | 両手を体の横で広げる | 長い |
| 060033 | 右手を上に上げる | 高い |
| 060034 | 足元に片手を持ってくる | 低い |
| 060035 | ドリブルの仕草 | サッカー |
| 060036 | 手でボールをつく | バスケ |
| 06003b | 片手で投げキス | チュッ |
| 06003d | 両手を体の横で、下を向いて首を振る | やれやれ |
| 06003e | 肩をたたく感じ | まあまあ、落ち着いて |
| 06003f | 俯いて悲しく手を振る | バイバイ |
| 060040 | 乾杯 | かんぱい |
| 060041 | そっぽをむく | ふんっ |
| 060043 | 慌てる | あたふた |
| 060044 | 背中をみる | 背中、後ろ |
| 060046 | 両手でお腹周りをたたく | おなかいっぱい |
| 060047 | きょろきょろする | どこにいる？、どこ |
| 060048 | 目を指す | 目 |
| 060049 | 鼻を指す | 鼻 |
| 06004a | 腰を指す | 腰 |
| 06004d | 横を向いて、手を広げて、足を曲げて、前後に体重移動している感じを表現 | スノボ |
| 06004f | 両手を胸の前でドラミング | ゴリラ |
| 060050 | 両手を胸の前に上げて、左右にゆらゆら | 音楽 |
| 060051 | 片手を上げて、片手で弓を弾く動き | バイオリン |
| 060052 | 両手を胸前で左右バラバラに上下に動かす | 太鼓、ティンパニー |
| 060053 | 歯磨きする仕草 | 歯磨き |
| 060057 | 右腕を上げて、体をそる | シャワー |
| 060059 | 息が上がった感じ | 疲れ、腰がいたい |
| 06005a | 腰を曲げて後ろで手を組む | おんぶ |
| 06005b | 片手を上にあげて頭をこつんと打つ感じ | まいった、しまった |
| 06005c | 手を口に当てて少し斜め下を見る | うふふ |
| 06005d | 右腕を前に出して上下に動かす | じゃんけん |
| 06005e | マイクをもって歌っているような動き | カラオケ、歌う |
| 06005f | 両手胸の前に持ってきて右手を口元にもってくる | ラーメン、うどん、丼 |
| 060062 | 両手を横に広げて前から後ろへ動かす | 飛行機 |
| 060064 | ポインターもって白板コツコツ | プレゼン |
| 06007a | 両腕頭で腰を落とす | スクワット |
| 06007b | カンフーのポーズ | カンフー |

> 特殊な id 値: `assign` (発話内容から自動でモーションを割り当てる)、`general` (汎用モーション) も指定可能。

---

### control タグ

アプリとの連携に利用する。本タグを含む action が実行されるタイミングで、指定アプリに対してイベントが発行される。

| 項目 | 内容 |
|---|---|
| 書式 | `<control target="…" function="…">~</control>` / `<control target="…" function="…"/>` |
| 親要素 | `<action>` |
| 子要素 | `<data>` |
| 属性 | `target` (必須), `function` (必須) |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `target` | 必須 | string | アプリの package 名 / `home` | イベント発行先。HVML を登録したアプリの package 名、または `"home"` (ホーム用 HVML でアプリを起動する用途)。 |
| `function` | 必須 | string | 任意 | 発行するイベント名。 |

```xml
<topic id="0010" listen="false">
  <action index="1">
    <control target="jp.co.sharp.android.rb.sample" function="sample_exec">
      <data key="id" value="test"/>
    </control>
  </action>
</topic>
```

**注意**: 子要素として data タグを記述することで target に詳細情報を連携できる。アプリへの通知の詳細は参考資料[2] (API リファレンス)、[3] (ガイドライン) を参照。

> 補足: control の `target` / `function` 属性は「変数を使用できる箇所」に含まれる ([6.10](#610-変数を使用できる箇所))。`start_activity` / `start_service` のような特定の固定 function 名は本 HVML リファレンスには列挙されておらず、イベント名 (function) とその受け取り・Android 側の挙動 (Activity/Service 起動など) は **アプリ側の実装と参考資料[2][3]** に委ねられている。HVML 側はあくまで「`target` アプリに `function` イベントを `data` キー/値付きで発行する」までを担う。

---

### data タグ

control の子要素として使用し、Key-Value のペアで target アプリにパラメータを引き渡す。

| 項目 | 内容 |
|---|---|
| 書式 | `<data key="…" value="…"/>` |
| 親要素 | `<control>` |
| 子要素 | なし |
| 属性 | `key` (必須), `value` (必須) |

| 属性 | 必須 | 型 | 意味 |
|---|---|---|---|
| `key` | 必須 | string | 任意の文字列。一つの親要素 (control) に対して一意になるよう指定。 |
| `value` | 必須 | string | key に対応する値。変数・演算子を使用可能 ([6.10](#610-変数を使用できる箇所))。 |

```xml
<topic id="0010" listen="false">
  <action index="1">
    <control target="jp.co.sharp.android.rb.sample" function="sample_exec">
      <data key="id1" value="data1"/>
      <data key="id2" value="data2"/>
    </control>
  </action>
</topic>
```

アプリ側での受け取り方法は参考資料[3] を参照。

---

### memory タグ

情報を記憶させたい場合に使用する。本タグで保存した情報は、後の対話で **変数** として参照できる。

| 項目 | 内容 |
|---|---|
| 書式 | `<memory key="…" type="…" ~/>` |
| 親要素 | `<action>` |
| 子要素 | なし |
| 属性 | `type` (必須), `key` (必須), `value`, `operation` |

| 属性 | 必須 | 型 | 許可値 | 意味 |
|---|---|---|---|---|
| `type` | 必須 | string | `temporary`, `permanent` | `temporary`=対話 (HVML の実行) 終了時に破棄。`permanent`=明示的に削除するまで保持。 |
| `key` | 必須 | string | (permanent は package 名から始まる文字列) | 保存情報を呼び出すためのキー。`type="permanent"` では衝突回避のため package 名から始める。 |
| `value` | operation="set" 時は必須 | string / number / 式 | — | 記憶する値。**演算子使用可** (カウンタ等)。演算子を使う場合は `()` で囲む。変数・演算子以外では[禁止文字](#610-禁止文字)を使わない。 |
| `operation` | 任意 | string | `set`, `delete` | `set`=上書き登録、`delete`=削除。未指定時は **set**。 |

**key 名記載フォーマット**: `key="jp.co.sharp.rb.sample.sampleKey"`

**変数と演算子の記述例 (カウンタ)**:
```xml
<memory type="temporary" key="loop_count" value="(${memory_t:loop_count} + 1)"/>
```

#### 使用例 (verbatim)

```xml
<topic id="0001" listen="false">
  <action index="1">
<memory type="permanent" key="jp.co.sharp.rb.sample.sampleKey" value="1"/>
<memory type="temporary" key="loop_count" value="0"/>
  </action>
  <next href="#0010" type="default"/>
</topic>
<topic id="0010" listen="false">
  <rule>
    <condition case_id="1">${memory_t:loop_count} eq 0</condition>
    <condition case_id="2">${memory_t:loop_count} eq 1</condition>
  </rule>
  <case id="1">
  <action index="1">
      <speech>え、何？もう一回言ってね</speech>
    </action>
  <action index="2">
<memory type="temporary" key="loop_count" value="(${memory_t:loop_count} + 1)"/>
    </action>
    <next href="#0020" type="default"/>
  </case>
  <case id="2">
  <action index="1">
      <speech>今度また教えてね</speech>
    </action>
    <next href="#0030" type="default"/>
  </case>
</topic>
```
topic 0001 で loop_count に 0 をセット。topic 0010 の rule で loop_count によって処理を分岐。1回目は case 1 が実行され action index="2" で loop_count がカウントアップされるため、次の topic 0010 では case 2 が実行される。

**注意 (重要)**:
- memory タグは一つの action タグ内に **複数記述可能**。speech など他タグと併記も可能。
- **memory で保存した情報は、同一 topic 内では利用できない。次 topic に遷移して以降に利用可能になる。**
- 保存した値の参照は memory 変数 (`${memory_t:...}` / `${memory_p:...}`) を使う (→ [6.7](#67-memory-変数))。

---

## 5. 演算子 (Operators)

### 演算子一覧

`eq (=)` `neq (≠)` `gt (>)` `ge (>=)` `lt (<)` `le (<=)` `and` `in` `outof` `include` `near` `+` `-` `*` `/`

| 演算子 | 記号 | 対象の型 | 意味 |
|---|---|---|---|
| `eq` | `=` | すべての型 | 左辺と右辺が一致すれば真。 |
| `neq` | `≠` | すべての型 | 左辺と右辺が一致しなければ真。 |
| `gt` | `>` | 数値型 / 日時型 | 左辺 > 右辺で真。 |
| `ge` | `>=` | 数値型 / 日時型 | 左辺 >= 右辺で真。 |
| `lt` | `<` | 数値型 / 日時型 | 左辺 < 右辺で真。 |
| `le` | `<=` | 数値型 / 日時型 | 左辺 <= 右辺で真。 |
| `and` | — | 真偽結果の結合 | and で繋がれた演算結果がすべて真なら全体が真。一つでも偽があれば偽。 |
| `in` | — | 左辺=文字列型 / 右辺=文字列型 or リスト型 | 右辺が文字列: 左辺の文字列が右辺中に含まれていれば真。右辺がリスト: 右辺要素に左辺と一致するものがあれば真。 |
| `outof` | — | 左辺=文字列型 / 右辺=文字列型 or リスト型 | 右辺が文字列: 左辺が右辺中に含まれていなければ真。右辺がリスト: 一致要素が無ければ真。 |
| `include` | — | 左辺=文字列型 / 右辺=リスト型 or 文字列型 | 右辺がリスト: 右辺要素のいずれか一つでも左辺に含まれていれば真。右辺が文字列: 右辺の文字列が左辺中に含まれていれば真。(※ in と左右の包含方向が逆) |
| `near` | — | 左辺・右辺=文字列型 | 2つの文字列の近似度が一定範囲内 (近い) なら真。ユーザ発話のゆらぎ吸収に使用。**最低4文字以上**の比較でのみ利用すること (短い文字列だと「テンキ」と「デンキ」のように1文字違いで意味が変わり、近似度も低く成立しない)。 |
| `+` `-` `*` `/` | — | 数値型の四則演算 | 左辺・右辺の加減乗除。`+` `-` は日時型でも利用可能 (→ [日時型](#691-日時型))。 |

### 演算子を利用できる箇所

- `situation` タグの内容
- `condition` タグの内容
- `data` タグの value 属性
- `memory` タグの value 属性

---

## 6. 変数 (Variables)

変数は、日時情報・音声認識の結果・センサー等で取得する環境変数など、**動的な内容**を HVML 内に記述する際に用いる。memory タグで記憶した情報も変数で参照できる。変数は HVML 実行時に取得値へ置換された後にタグが解釈される。

### 6.0 変数のフォーマット

```
${jp.co.sharp.rb.sample:variable}
```
上記では `jp.co.sharp.rb.sample:variable` が変数名。引数を持つ変数は:
```
${ jp.co.sharp.rb.sample:variable (arg1,arg2)}
```
`arg1` が第一引数、`arg2` が第二引数。

### 6.1 音声認識・ユーザ発話変数

| 変数 | 戻り値型 | 意味 |
|---|---|---|
| `Lvcsr:Basic` | 文字列型 | 直前のユーザ発話の認識結果を **かな漢字の文字列**で返す。**大語彙連続音声認識 (LVCSR=Large Vocabulary Continuous Speech Recognition) によるフリーワード認識**。 |
| `Lvcsr:Kana` | 文字列型 | 直前のユーザ発話の認識結果を **カタカナ**で返す。 |
| `Local_Reply:GLOBAL_REPLY_YES` | 文字列型 | 直前の発話が**肯定**の意図の場合に値が格納される。事前に topic の `dict="Reply"` 指定が必要。 |
| `Local_Reply:GLOBAL_REPLY_NO` | 文字列型 | 直前の発話が**否定**の意図の場合に値が格納される。事前に `dict="Reply"` 指定が必要。 |
| `Local:WORD_APPLICATION` | 文字列型 | 直前の発話が登録したアプリ起動文言に一致した場合、一致文言が格納される。**ホーム用 HVML でのみ使用。演算子は `eq` を使うこと。** |
| `Local:WORD_APPLICATION_FREEWORD` | 文字列型 | 同上 (フリーワード版)。ホーム用 HVML でのみ使用、`eq` を使うこと。 |

**認識エラー**: ネットワークエラー等で認識結果が取得できなかった場合、`Lvcsr:Basic`/`Lvcsr:Kana` には **`ＶＯＩＣＥＰＦ＿ＥＲＲ＿ｘｘ`** という文字列 (全角、xx 部分はエラー理由文字列) が格納される。具体的なエラー処理の記述は参考資料[3] を参照。

> **フリーワード / 大語彙認識のしくみ**: `${Lvcsr:Basic}` (および `Lvcsr:Kana`) が **自由発話 (large-vocabulary, free-form) の認識結果**を保持する中核変数。条件式では `${Lvcsr:Basic} eq こんにちは` のような完全一致のほか、`誕生日 in ${Lvcsr:Basic}` のような **部分一致 (`in`)**、`near` による **あいまい一致**、`include` による **リスト要素のいずれか含有判定**を組み合わせて、ユーザの自由な発話を拾う。語彙を限定した肯定/否定認識は `dict="Reply"` + `Local_Reply:*` を使う。

### 6.2 時制取得用変数

| 変数 | 戻り値型 | 意味 |
|---|---|---|
| `Year` | 数値型 | 現在の年 (整数)。 |
| `Month` | 数値型 | 現在の月 (整数)。 |
| `Day` | 数値型 | 現在の日 (整数)。 |
| `Date` | 日時型 | 現在の年月日。 |
| `DayOfWeekJp` | 文字列型 | 現在の曜日。文字列は 月/火… で「曜日」は含まない。 |
| `Hour` | 数値型 | 現在の時 (整数)。 |
| `Minute` | 数値型 | 現在の分 (整数)。 |
| `Second` | 数値型 | 現在の秒 (整数)。 |
| `Time` | 日時型 | 現在の時分秒。 |
| `Now` | 日時型 | 現在日時。 |
| `CurrentDate` | 数値型 | 年内通算日数。1月1日を 0 として 0〜365。 |
| `CurrentYearDate` | 数値型 | 1970/01/01 からの通算日数 (1970/01/01 を 0 とした経過日数, 整数)。 |
| `CurrentTime` | 数値型 | 日内通算秒数。0〜86399。 |

### 6.3 時制判定用変数

| 変数 | 戻り値型 | 引数 | 意味 |
|---|---|---|---|
| `InDateRange` | 真偽型 | 開始月, 開始日, 終了月, 終了日 (すべて数値型) | 現在月日がその範囲内なら true。片側のみ指定したい場合、指定しない月・日に `-1`。 |
| `InTimeRange` | 真偽型 | 開始日内秒数, 終了日内秒数 (数値型) | 現在時刻がその範囲内なら true。片側のみ指定は不要側に `-1`。 |
| `IsWithinTime` | 真偽型 | 1970/01/01 からの通算日数, 日内秒数, 差分秒数 (数値型) | 指定日・秒からの経過時間が差分秒数以内なら true。 |
| `DiffDate` | 数値型 | 基準日時 (日時型), 比較日時 (日時型) | 基準日時に対し比較日時があと何日かを返す。 |

### 6.4 型変換用変数

| 変数 | 戻り値型 | 引数 | 意味 |
|---|---|---|---|
| `SizeOf` | 数値型 | リスト型 | リストの要素数を返す。 |
| `Select` | 文字列型 | 第一=リスト型, 第二=要素番号 | 指定要素の文字列のみ取得。 |
| `YearOf` / `MonthOf` / `DayOf` / `HourOf` / `MinuteOf` / `SecondOf` | 数値型 | 日時型 | 引数の年/月/日/時/分/秒のみを返す。 |

### 6.5 個人情報を扱う変数

(個人情報 = 利用規約「2.センサー情報」に該当)

| 変数 | 戻り値型 | 意味 |
|---|---|---|
| `resolver:contacts:robot_name` | 文字列型 | ロボホンの呼び方。初期値は「ろぼほん」。 |
| `resolver:contacts:user:birthday` | 日時型 | オーナーの誕生年月日。未設定なら null。 |
| `resolver:contacts:user:name_for_speech` | 文字列型 | オーナー名。ロボホンがオーナーを呼ぶ際に利用する。 |

### 6.6 その他変数 (環境・状態)

| 変数 | 戻り値型 | 引数 | 意味 |
|---|---|---|---|
| `Rand` | 数値型 | (任意) 0〜99 | 引数指定時は 0 以上引数未満の整数をランダムで返す。引数なしは 0〜99 をランダム。 |
| `env:robot_iniboot_date` | 日時型 | — | 初期設定を実施した日 (ロボホンと出会った日)。 |
| `resolver:charger_connected` | 真偽型 | — | 充電/USB ケーブル接続中なら true。 |
| `resolver:earphone_connected` | 真偽型 | — | イヤホン接続中なら true。 |
| `memory_p:manner_mode` | 文字列型 | — | マナーモード状態: `off` (非マナー) / `on_partial` (会話のみOFF) / `on` (すべてOFF)。 |
| `resolver:pose` | 文字列型 | — | ロボホンの姿勢: `stand`, `hand_stand`, `sit`, `hand_sit`, `mobile`, `hand_mobile`, `back`, `belly`, `hand_phone`, `projector`, `cradle`, `immobile`。 |
| `resolver:speech_ok` | 文字列型 | `${resolver:ok_id}` | ユーザ発話への返事 (あいづち) の発話内容を返す。引数に `${resolver:ok_id}` を指定。 |
| `resolver:motion_ok` | 文字列型 | `${resolver:ok_id}` | ユーザ発話への返事のモーションID を返す。引数に `${resolver:ok_id}` を指定。 |
| `resolver:ok_id` | 数値型 | — | `resolver:speech_ok` / `resolver:motion_ok` の引数に指定する用途。 |
| `env:telephony_support` | 真偽型 | — | ロボホン(3G・LTE)モデルなら true。※SIM 種別による通話可否は判別不可。 |
| `env:battery:level` | 数値型 | — | 電池残量 0〜100 (%)。 |
| `env:projector_support` | 真偽型 | — | プロジェクター機能ありモデル (SR01MW/SR02MW) なら true。 |
| `env:sitmodel` | 真偽型 | — | ロボホンライトなら true。 |
| `env:robohon_generation` | 数値型 | — | 世代 (1 or 2)。第1世代=SR01MW/SR02MW、第2世代=SR03M/SR04M/SR05M。 |

### 6.7 memory 変数

保存した記憶を変数として利用できる。

- `type="temporary"` で保存した変数: `${memory_t: var}`
- `type="permanent"` で保存した変数: `${memory_p:jp.co.sharp.rb.sample.var}`

`var` / `jp.co.sharp.rb.sample.var` は保存時の key 値を指定。記載した結果として保存済みの value 値が返る。記憶の保存は HVML 内の memory タグまたはアプリから直接保存する方法がある (詳細は参考資料[3])。

### 6.8 変数の追加 (アプリ定義変数)

前述以外の変数を定義し、HVML 実行時にアプリへの callback 通知で値を設定できる。

- 変数名フォーマット: `${アプリパッケージ名:xx}`
- HVML 実行時にアプリへ `VoiceUIListener#onVoiceUIResolveVariable()` が通知されるので、値を設定すると HVML に反映される。
- 詳細は参考資料[2][3]。

これにより、`${com.package:var}` 形式の任意のアプリ定義変数を HVML 中で参照し、ランタイムにアプリ側 (Android/Java) から値を解決できる。

### 6.9 変数型

変数の型は **string (文字列)型 / float (数値)型 / boolean (真偽, true/false)型**、および値が無い/変数が存在しないことを示す **`null`** に分類される。さらに特殊演算用の型がある。型は HVML 内で自動判別される。

#### 6.9.1 日時型

日時専用フォーマット。3 タイプ:

- 日時全て: `YYYY/MM/DD_hh:mm:ss` (例: `2015/08/18_16:26:00`, `2015/8/18_16:26:00`)
- 日付のみ: `YYYY/MM/DD` (例: `2015/08/18`, `2015/8/18`)
- 時間のみ: `hh:mm:ss` (例: `16:26:00`)

日時型同士で `+`, `-`, `eq`, `neq`, `gt`, `lt`, `ge`, `le` が可能。`+`/`-` を使う場合、左辺に日付型、右辺は以下のフォーマット (X は数値):

- `Xyear` / `Xmon` / `Xday` / `Xhour` / `Xmin` / `Xsec`

特定箇所をワイルドカード `*` で指定可能。

**日時型サンプル (verbatim)**:
```xml
<hvml version="2.0">
  …
<body>
  <topic id="date" listen="false">
    <rule>
      <condition case_id="1" priority="1">${Now} eq 2016/01/01</condition>
      <condition case_id="2" priority="2">${Now} eq */1/1</condition>
      <condition case_id="3" priority="2">${Now} ge */7/1</condition>
    </rule>
    <case id="1">
      <action index="1">
        <speech>今日は２０１６年のお正月だよ</speech>
      </action>
    </case>
    <case id="2">
      <action index="1">
        <speech>今日はお正月だよ</speech>
      </action>
    </case>
    <case id="3">
      <action index="1">
        <speech>今年ももう後半だね</speech>
        <memory type="temporary" key="Date" value="(${Date} + 15day)"/>
      </action>
      <next href="#later" type="default"/>
    </case>
  </topic>
  <topic id="later" listen="false">
    <action index="1" >
      <speech>15 日後は${memory_t:Date}だよ</speech>
    </action>
  </topic>
</body>
</hvml>
```
現在日が 2016/01/01 なら case 1、2016年以外の 1/1 なら case 2 (`*/1/1`)、7/1〜12/31 なら case 3 (`*/7/1` 以上) が実行される。

#### 6.9.2 リスト型

複数の値を格納する型。`[abc,ABC,xyz,XYZ]` のような形式。`${SizeOf}` で要素数、`${Select}` で指定要素のみを取得できる。

### 6.10 変数を使用できる箇所

- `situation` タグの内容
- `speech` タグの内容
- `memory` タグの value 属性
- `behavior` タグの type 属性 / id 属性
- `control` タグの target 属性 / function 属性
- `data` タグの value 属性
- `condition` タグの内容
- `a` タグの href 属性
- `next` タグの href 属性

### 6.11 禁止文字

条件判定文の解釈で使用しているため、原則アプリ (Java) から API で設定する変数値や memory タグの value に含めないこと:

`, (カンマ)` ` (半角スペース)` `: (コロン)` `[ ` `]` `(` `)` `{` `}` `\ (バックスラッシュ/円記号)`

ただし以下のケースに限り使用可能:
1. 配列 (リスト型) として `,` `[` `]` を使うケース。
   - 正: `[a1,a2]`
   - 誤: `[a1, a2]` (**半角スペースは使用不可**)
2. 日時型としてコロンを使うケース。例: `2015/08/18_16:26:10`
3. memory タグの value 属性で `()` 内の演算子・変数として記述するケース (→ [memory タグ](#memory-タグ))。

### 6.12 situation に変数を記載する際の注意点

head の `situation trigger="user-word"` に条件式を書く際は、以下の優先度順で記載する (シナリオ選択速度に影響):

> ユーザ発話変数 (Lvcsr:Basic / Lvcsr:Kana) > `${Hour}` > その他の変数 (memory_p など)

```xml
<!-- ○ 正しい situation の記載 -->
<situation trigger="user-word" topic_id="0001">
こんにちは in ${Lvcsr:Basic} and ${Hour} ge 12 and {memory_p:jp.co.sharp.rb_hoge} eq 0
</situation>

<!-- × 誤った situation の記載 -->
<situation trigger="user-word" topic_id="0001">
{memory_p: jp.co.sharp.rb_hoge} eq 0 and こんにちは in ${Lvcsr:Basic} and {Hour} ge 12
</situation>
```
誤った記載でも動作はするが、シナリオ選択時の速度に影響するため注意。

---

## 7. 環境検知イベント (Environment Events)

環境検知イベントは、ロボホンに状態変化があった際に通知され、シナリオ実行の契機として利用するイベント。`situation` の `trigger="env-event"` + `value="イベント名"` で使用する。

| イベント名 | 内容 |
|---|---|
| `put_down_standup` | 置かれた (垂直)。胴体が垂直方向である必要あり。立ち状態・座り状態・充電台に乗っている状態でしか発生しない。 |
| `charge_start` | 充電開始された |
| `charge_stop` | 充電停止された |
| `low_batt` | 電池残量が低下した (20% 以下を低下状態とする) |
| `stop_alarm` | アラームが停止された |
| `earphone_connect` | イヤホン接続された |
| `location` | 現在の位置情報が更新された場合に通知 |
| `put_down_back` | 水平に置いた |
| `shake` | 振られた |
| `upside_down` | 逆さにされた |

### 使用例 (verbatim)

```xml
<hvml version="2.0">
<head>
<situation topic_id="0001" trigger="env-event" value="shake" priority="75">true</situation>
  …
</head>
  …
</hvml>
```

---

## 8. 付録: 音声認識のしくみ要点

カスタム LLM 会話アプリで HVML を生成する際に押さえるべき要点:

### 8.1 トリガー (situation の trigger)

| trigger | 契機 | 必須属性 |
|---|---|---|
| `user-word` | ユーザ発話 | (value 不要) |
| `env-event` | 環境検知イベント | `value`= イベント名 (→ 7章) |
| (accost) | アプリからの強制実行 | `accost` タグの `word` でマッチ (situation ではなく accost タグを使う。条件式不可) |

> タイマー (timer) 起動について: 本 HVML 2.0 リファレンスの `situation` の `trigger` 許可値は `user-word` と `env-event` の2つのみで、純粋な「タイマー (timer) トリガー」は定義されていない。時刻ベースの起動は、`env-event` (例 `charge_start`) や日内秒数・日時型変数 (`${Hour}`, `${Now}`, `InTimeRange`, `IsWithinTime` など) を condition/situation の条件式で組み合わせて実現する。定時の強制起動が必要な場合はアプリ側から `accost` イベントを発火させる。

### 8.2 認識マッチングの組み立て

ユーザの自由発話 (`${Lvcsr:Basic}`, かな漢字 / `${Lvcsr:Kana}`, カタカナ) に対して:

| 演算子 | 用途 | 例 |
|---|---|---|
| `eq` | 完全一致 | `${Lvcsr:Basic} eq こんにちは` |
| `in` | 部分一致 (発話中にキーワードが含まれる) | `誕生日 in ${Lvcsr:Basic}` |
| `outof` | 含まれない | `NG in ${Lvcsr:Basic}` の否定相当 |
| `include` | リスト要素のいずれかが発話に含まれる | `${Lvcsr:Basic} include [東京,大阪]` (右辺リスト) |
| `near` | あいまい一致 (4文字以上) | `${Lvcsr:Basic} near おはようございます` |
| `and` | 複数条件の AND 結合 | `誕生日 in ${Lvcsr:Basic} and 覚えて in ${Lvcsr:Basic}` |

肯定/否定の限定認識は topic に `dict="Reply"` を指定し、`${Local_Reply:GLOBAL_REPLY_YES}` / `${Local_Reply:GLOBAL_REPLY_NO}` を参照する。

### 8.3 辞書変数 (Dictionary variables) まとめ

| 変数 | 役割 |
|---|---|
| `${Lvcsr:Basic}` | 大語彙連続音声認識 (フリーワード) の結果 (かな漢字) |
| `${Lvcsr:Kana}` | 同 (カタカナ) |
| `${Local_Reply:GLOBAL_REPLY_YES/NO}` | `dict="Reply"` 指定時の肯定/否定意図 |
| `${Local:WORD_APPLICATION(_FREEWORD)}` | ホーム用 HVML のアプリ起動文言一致 (`eq` 使用) |
| `${resolver:...}` | 端末状態・個人情報の解決 (姿勢・充電・名前など) |
| `${memory_t:...}` / `${memory_p:...}` | 一時/永続メモリの参照 |
| `${com.package:var}` | アプリ定義変数 (callback `onVoiceUIResolveVariable()` で解決) |

### 8.4 TTS パラメータまとめ

| パラメータ | タグ/属性 | 範囲・既定 |
|---|---|---|
| 感情の種別 | `emotion type` | `happiness` / `sadness` / `anger` (既定 happiness) |
| 感情のレベル | `emotion level` | 1〜4 (既定 1) |
| 間 (ま) | `wait ms` | msec 指定 |
| 発話文字数上限 | (speech 内容) | **本リファレンスに明示記載なし** |

> 速度 (speed) / ピッチ (pitch) / 音量 (volume) について: 本 HVML 2.0 リファレンスでは、発話の TTS パラメータとして規定されているのは **emotion (type/level)** と **wait** のみで、speed / pitch / volume を直接指定する HVML 属性・タグは定義されていない。これらが必要な場合はアプリ側 (API) での制御となる (参考資料[2] API リファレンス参照)。

### 8.5 重要な数値・制約のクイックリファレンス

| 項目 | 値 |
|---|---|
| `hvml version` | `"2.0"` |
| ユーザ発話待受 (`listen_ms`) 既定 | 10000 msec (10秒) |
| `listen="true"` で next へ遷移するまでの無発話時間 | 10秒 |
| situation priority 範囲 (home) | 78〜84 (必須) |
| situation/accost priority 範囲 (非home) | 61〜90 |
| situation priority 既定 (head, 非home) | 75 |
| accost priority 既定 | 75 |
| condition priority 範囲 / 既定 | 1〜99 / 既定 10 (小さいほど優先) |
| condition weight 既定 | 10 |
| emotion level | 1〜4 (既定 1) |
| Rand 引数 | 0〜99 |
| `near` 最小文字数 | 4文字以上 |
| **speech 一発話の文字数上限** | **本仕様書に明示なし** |

---

## 参考資料 (References, 原典記載)

| # | Title |
|---|---|
| [1] | RoBoHoN_アプリ開発スタートガイド |
| [2] | RoBoHoN_API リファレンス |
| [3] | RoBoHoN_アプリ開発ガイドライン |

> 本 HVML リファレンスは「[2] API リファレンス」「[3] アプリ開発ガイドライン」を頻繁に参照している。特に「モーションの詳細」「肯定/否定 (dict=Reply) 認識の詳細」「control の target/function とアプリ連携 (Activity/Service 起動)」「マナーモード」「変数の callback 解決」などは [2][3] 側に詳細がある。
