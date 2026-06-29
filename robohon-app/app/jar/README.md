# フレームワーク jar について

このフォルダには、アプリのコンパイルに必要なシャープ RoBoHoN SDK のフレームワーク jar を置きます。
これらは**シャープの配布物（再配布禁止）**のため、リポジトリにはコミットしていません（`.gitignore` 対象）。

クローン後は、公式 SDK（`RoBoHoN_SDK_2_0_0.zip`）の `jar/` から以下を**このフォルダへコピー**してください。

- `jp.co.sharp.android.voiceui.framework.jar` （必須：音声対話）
- `jp.co.sharp.android.rb.addressbook.framework.jar` （必須：名前/電話帳取得）
- `jp.co.sharp.android.rb.song.framework.jar` （必須：歌）
- `jp.co.sharp.android.rb.rbdance.framework.jar` （必須：ダンス）
- `jp.co.sharp.android.rb.action.framework.jar` （必須：アクション=歩く等）

これらは実行時には端末が提供するため、ビルドでは `compileOnly`（APK には同梱しない）として参照します。
SDK は RoBoHoN 開発者サイトから入手できます。
