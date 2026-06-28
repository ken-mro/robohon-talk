# HVML スキーマ早見表 (hvml_schema_Ver110.xsd)

SDK同梱の `vendor/RoBoHoN_SDK_2_0_0/schema/hvml_schema_Ver110.xsd`（Ver 1.1.0, 2017/5/24）を要素・属性レベルで整理した早見表。タグの意味・使い方の詳細は [hvml-reference.md](hvml-reference.md) を参照。HVMLファイルの `<hvml version="...">` 属性自体は `"2.0"` 固定（ガイドライン）。XSDのバージョンとHVML文書バージョンは別物。

> スキーマ導入手順は「アプリ開発スタートガイド 3.1.7 HVML構文チェックツールの導入」参照（[develop-start-guide.md](develop-start-guide.md)）。Ver1.1.0で `href` 属性の `#` 漏れチェックが追加された。

## ルート構造

```
hvml (version:decimal 必須)
├── head
│   └── (以下を任意個・順不同 choice)
│       ├── producer        (string)  … パッケージ名
│       ├── description     (string)
│       ├── tool_version    (string)
│       ├── scene  →        <scene value=…/>
│       ├── situation →     <situation .../>   音声/イベントの発火条件
│       ├── accost →        <accost .../>      アプリ起点の発話トリガ
│       └── version →       <version value=…/>
└── body
    └── topic (1個以上, maxOccurs=unbounded)
        ├── 属性: id(必須), listen(boolean必須), listen_ms(uint任意),
        │         recog_type(任意 = "#robohonprocess"), dict(string任意)
        └── (以下を任意個 choice)
            ├── action   → 発話・動作・制御の実行単位
            ├── a        → 分岐リンク
            ├── rule     → condition で case を選ぶ
            ├── case     → id(必須), limit(uint任意), cleardays(uint任意)
            └── next     → 次topicへ
```

## 要素別 属性表

### `<hvml>`
| 属性 | 必須 | 型 | 備考 |
|---|---|---|---|
| version | ○ | decimal | 文書バージョン。実運用は `"2.0"` |

### `<scene>`
| 属性 | 必須 | 型 |
|---|---|---|
| value | ○ | string |

scene名はパッケージ名を含めること（アプリ固有scene）。`home` は予約（ホーム起動シナリオ用）。

### `<situation>` — 発火条件（音声認識・環境イベント）
| 属性 | 必須 | 型 | 値 |
|---|---|---|---|
| trigger | ○ | triggerType | `user-word` / `env-event` / `timer` ※XSDには3種あるが、リファレンス上 実用は `user-word`・`env-event`。timerはネイティブ非対応（日時変数 or アプリ起点accostで代替） |
| value | × | string | |
| topic_id | × | string | 発火時に実行するtopicのid |
| priority | × | priorityValue(1–99) | ホームシナリオは78–84が目安(既定78) |

本文(テキスト)に認識条件式を書く。例: `${Lvcsr:Basic} include [おはよう,こんにちは]` / `${Local:WORD_APPLICATION} eq てんぷれーと`。演算子は eq/in/include/near/and（near は4文字以上のみ）。

### `<accost>` — アプリ起点の発話トリガ
| 属性 | 必須 | 型 | 備考 |
|---|---|---|---|
| topic_id | ○ | string | 発話させるtopic |
| word | ○ | string | accost名（パッケージ名を含める）。Javaの `startSpeech(accost)` で指定 |
| priority | × | priorityValue(1–99) | 例75 |

### `<version>`
| 属性 | 必須 | 型 |
|---|---|---|
| value | ○ | float |

### `<topic>` — 会話の単位
| 属性 | 必須 | 型 | 備考 |
|---|---|---|---|
| id | ○ | string | |
| listen | ○ | boolean | 発話後にユーザ発話を待ち受けるか |
| listen_ms | × | uint | 待受時間(ms)。既定10000(=10秒) |
| recog_type | × | enum | `#robohonprocess` |
| dict | × | string | 認識辞書指定（例 `Reply` でYes/No） |

### `<action>` — 実行単位（speech/動作/制御）
| 属性 | 必須 | 型 |
|---|---|---|
| index | × | uint |

子(choice, 1個以上): `speech` / `sound` / `behavior` / `control` / `extra` / `memory` / `memoryset`

### `<speech>` — 発話テキスト（mixed=文字とタグ混在可）
子(任意・任意個): `emotion` / `speed` / `pitch` / `volume` / `wait`
※XSDでは speed/pitch/volume が speech の子として定義されるが、HVMLリファレンス上はTTSパラメータとしての speed/pitch/volume はHVML属性に無く API/ツール側調整。emotion と wait が実質的に使える。
本文に `${...}` 変数を埋め込み動的発話可（例 `${com.robohon.template:speech}` をアプリが変数解決）。

### `<emotion>`
| 属性 | 必須 | 型 | 値 |
|---|---|---|---|
| type | ○ | emotionType | `happiness` / `sadness` / `anger` |
| level | × | emotionLevel | 1 / 2 / 3 / 4 |

### `<speed>` / `<pitch>` / `<volume>`
| 属性 | 必須 | 型 |
|---|---|---|
| value | ○ | uint |

### `<wait>`
| 属性 | 必須 | 型 | 意味 |
|---|---|---|---|
| ms | ○ | uint | 発話中の間(ポーズ) |

### `<sound>`
| 属性 | 必須 | 型 |
|---|---|---|
| src | ○ | string |

### `<behavior>` — モーション
| 属性 | 必須 | 型 | 備考 |
|---|---|---|---|
| type | ○ | string | 例 `normal` |
| id | × | string | モーションID。`assign`=自動割当（読み<30文字の発話に付与）。一覧は hvml-reference.md |

### `<control>` — アプリ/機能への制御指示
| 属性 | 必須 | 型 | 備考 |
|---|---|---|---|
| target | ○ | string | 対象パッケージ名 or `home` |
| function | ○ | string | 機能名（例 `start_activity`, `start_service`, `end_app` 等アプリ定義） |

子 `<data>` (任意個):
| 属性 | 必須 | 型 |
|---|---|---|
| key | ○ | string |
| value | ○ | string |

`control` が実行されるとアプリの `VoiceUIListener.onVoiceUIEvent`（発話と同時）/`onVoiceUIActionEnd`（発話後）に通知される。例: home起動シナリオの `function="start_activity"` + `data(package_name, class_name)` でActivity起動。

### `<extra>`
| 属性 | 必須 | 型 |
|---|---|---|
| value | × | string |

### `<memory>` — 記憶の参照/設定（action内）
| 属性 | 必須 | 型 | 値 |
|---|---|---|---|
| type | ○ | memoryType | `temporary` / `permanent` |
| key | ○ | string | |
| value | × | string | |
| operation | × | memoryOperation | `set` / `append` / `delete` |

`permanent` は `${memory_p:key}` で参照。アプリからは `setMemory/clearMemory`（updateAppInfo/removeVariable）で操作。**memory_p は 1値≤1KB・最大100変数/アプリ**（ガイドライン）。

### `<memoryset>` — 複数データの記憶セット
| 属性 | 必須 | 型 | 値 |
|---|---|---|---|
| key | ○ | string | |
| operation | ○ | memorysetOperation | `append` / `delete` / `update` |
| condition | × | string | |
| alias | × | string | |

子 `<data>` (任意個): key(必須), value(必須)

### `<rule>` / `<condition>` / `<case>` / `<next>` / `<a>`
- `rule` > `condition`: `case_id`(必須), `weight`(uint任意), `priority`(1–99任意)。本文に条件式（例 `true`）。
- `case`: `id`(必須), `limit`(uint任意=実行回数上限), `cleardays`(uint任意)。子に action/a/next。
- `next`: `href`(必須, `#topic` か `${...}` を含む形式), `type`(任意=`default`)。子に situation(任意)。
- `a`: `href`(必須, 同上), `type`(任意=`default`)。子に situation(任意)。

## simpleType 列挙値まとめ
| 型 | 値 |
|---|---|
| triggerType | user-word, env-event, timer |
| recog_type_type | #robohonprocess |
| priorityValue | 1–99 (integer) |
| emotionType | happiness, sadness, anger |
| emotionLevel | 1, 2, 3, 4 |
| memoryType | temporary, permanent |
| memoryOperation | set, append, delete |
| memorysetOperation | append, delete, update |
| aType / nextType | default |

## href の形式制約
`a` / `next` の `href` は正規表現 `.*#.*|.*$\{.*\}.*` に一致必須＝ `#` を含む（`#topicId`）か `${...}` 変数を含むこと。Ver1.1.0でこの `#` 漏れチェックが追加された。
