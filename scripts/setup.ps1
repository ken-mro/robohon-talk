# =============================================================================
# robohon-intelligence クローン後セットアップ
#
# git 管理外（シャープ配布物・機密）のファイル群を復元・生成する。
# 冪等：既に揃っているステップはスキップするので何度実行してもよい。
#
#   pwsh -File scripts/setup.ps1
#
# シャープSDK（RoBoHoN_SDK_2_0_0.zip）は再配布禁止のため自動ダウンロード不可。
# RoBoHoN開発者サポートサイト（要開発者登録）から入手し、リポジトリ直下か
# Downloads フォルダに置いてから実行すること。
# =============================================================================

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$manual = @()   # 手動対応が必要な項目
$done = @()     # このスクリプトが今回行ったこと

function Step($msg) { Write-Host "== $msg" -ForegroundColor Cyan }

# --- 1. SDK zip の所在確認 → vendor/ へ展開 --------------------------------
Step "1/5 シャープSDK (vendor/RoBoHoN_SDK_2_0_0)"
$vendorSdk = Join-Path $root "vendor\RoBoHoN_SDK_2_0_0"
if (Test-Path (Join-Path $vendorSdk "jar")) {
    Write-Host "   OK: 展開済み"
} else {
    $zipCandidates = @(
        (Join-Path $root "RoBoHoN_SDK_2_0_0.zip"),
        (Join-Path $env:USERPROFILE "Downloads\RoBoHoN_SDK_2_0_0.zip")
    )
    $zip = $zipCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($zip) {
        Write-Host "   展開中: $zip → vendor/"
        # zip のトップレベルは RoBoHoN_SDK_2_0_0/ なので vendor 直下に展開すればよい
        Expand-Archive -Path $zip -DestinationPath (Join-Path $root "vendor") -Force
        $done += "SDK zip を vendor/ へ展開"
    } else {
        $manual += "RoBoHoN_SDK_2_0_0.zip を RoBoHoN開発者サポートサイト（要開発者登録）から入手し、リポジトリ直下に置いて本スクリプトを再実行（再配布禁止のため自動取得不可）"
        Write-Host "   NG: zip が見つからない（後述の手動手順参照）" -ForegroundColor Yellow
    }
}

# --- 2. フレームワーク jar を robohon-app/app/jar へコピー ------------------
Step "2/5 フレームワーク jar (robohon-app/app/jar)"
# 必須 jar 一覧は robohon-app/app/jar/README.md が正
$jars = @(
    "jp.co.sharp.android.voiceui.framework.jar",
    "jp.co.sharp.android.rb.addressbook.framework.jar",
    "jp.co.sharp.android.rb.song.framework.jar",
    "jp.co.sharp.android.rb.rbdance.framework.jar",
    "jp.co.sharp.android.rb.action.framework.jar",
    "jp.co.sharp.android.rb.cameralibrary.jar"
)
$jarSrc = Join-Path $vendorSdk "jar"
$jarDst = Join-Path $root "robohon-app\app\jar"
$missingJars = $jars | Where-Object { -not (Test-Path (Join-Path $jarDst $_)) }
if (-not $missingJars) {
    Write-Host "   OK: 6 jar 配置済み"
} elseif (Test-Path $jarSrc) {
    foreach ($j in $missingJars) {
        Copy-Item (Join-Path $jarSrc $j) $jarDst
        Write-Host "   コピー: $j"
    }
    $done += "フレームワーク jar $($missingJars.Count) 件をコピー"
} else {
    $manual += "SDK展開後に再実行して jar をコピー（ステップ1が前提）"
    Write-Host "   NG: SDK未展開のためコピー不可" -ForegroundColor Yellow
}

# --- 3. relay-server/.env ----------------------------------------------------
Step "3/5 relay-server/.env"
$envFile = Join-Path $root "relay-server\.env"
$envExample = Join-Path $root "relay-server\.env.example"
if (Test-Path $envFile) {
    Write-Host "   OK: 既存"
} elseif (Test-Path $envExample) {
    Copy-Item $envExample $envFile
    $done += ".env を .env.example から作成"
    $manual += "relay-server/.env に ANTHROPIC_API_KEY を設定（未設定でもモックモードで動作可）"
    Write-Host "   作成: .env（キーは空＝モックモード）"
} else {
    $manual += "relay-server/.env を作成し ANTHROPIC_API_KEY を設定"
    Write-Host "   NG: .env.example が無い" -ForegroundColor Yellow
}

# --- 4. relay-server 依存 (pnpm install) ------------------------------------
Step "4/5 relay-server 依存パッケージ"
if (Test-Path (Join-Path $root "relay-server\node_modules")) {
    Write-Host "   OK: node_modules あり"
} elseif (Get-Command pnpm -ErrorAction SilentlyContinue) {
    # TLS傍受プロキシ環境では Windows ルートCAの PEM を指定（README 参照）
    $pem = Join-Path $env:USERPROFILE ".config\windows-root-ca.pem"
    if (Test-Path $pem) { $env:NODE_EXTRA_CA_CERTS = $pem }
    Push-Location (Join-Path $root "relay-server")
    try { pnpm install } finally { Pop-Location }
    $done += "pnpm install 実行"
} else {
    $manual += "pnpm を導入（corepack enable 推奨）して relay-server で pnpm install"
    Write-Host "   NG: pnpm が見つからない" -ForegroundColor Yellow
}

# --- 5. robohon-app/local.properties ----------------------------------------
Step "5/5 robohon-app/local.properties"
$lp = Join-Path $root "robohon-app\local.properties"
if (Test-Path $lp) {
    Write-Host "   OK: 既存"
} else {
    $sdkDir = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    $sdkLine = if (Test-Path $sdkDir) { "sdk.dir=" + ($sdkDir -replace "\\", "/") }
               else { "# sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk  # Android SDK の場所に合わせて設定" }
    @"
## Machine-specific. Not committed (gitignored).
$sdkLine

## 中継サーバ（公開リポジトリには載せない・gitignored）
## Cloudflare Workers にデプロイ済みの URL とトークンを設定する（docs/architecture.md 参照）。
## 未設定時はローカルLAN既定値 http://192.168.3.4:8787/chat が使われる（app/build.gradle）。
# relay.url=https://<worker>.workers.dev/chat
# relay.token=<X-Relay-Token の値>
"@ | Set-Content -Path $lp -Encoding utf8
    $done += "local.properties の雛形を生成"
    $manual += "robohon-app/local.properties の relay.url / relay.token を実値に設定（実機ビルド前に必須）"
    Write-Host "   作成: 雛形（relay.url / relay.token は要記入）"
}

# --- サマリ ------------------------------------------------------------------
Write-Host ""
Write-Host "===== セットアップ結果 =====" -ForegroundColor Green
if ($done) { $done | ForEach-Object { Write-Host " 済: $_" } } else { Write-Host " （新規に行った処理なし）" }
if ($manual) {
    Write-Host ""
    Write-Host "残りの手動手順:" -ForegroundColor Yellow
    $manual | ForEach-Object { Write-Host " ・$_" }
}
Write-Host ""
Write-Host "検証: cd relay-server; pnpm test   /   cd robohon-app; .\gradlew.bat assembleDebug"
