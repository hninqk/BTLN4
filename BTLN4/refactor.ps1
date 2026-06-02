$base = "c:\Users\admin\BTLN4\BTLN4\src\main\java\com\auction"

# 1. Create directories
mkdir -Force "$base\ui\util" | Out-Null
mkdir -Force "$base\api\config" | Out-Null
mkdir -Force "$base\core\util" | Out-Null
mkdir -Force "$base\infra\db" | Out-Null

# Define maps
$uiUtils = "AlertHelper.java","AnimationUtil.java","AuctionChartHelper.java","ImageLoaderUtil.java","NavigationManager.java"
$apiConfigs = "AppConfig.java","ServerConfig.java"
$coreUtils = "NotificationManager.java","CatboxUploader.java","HotItemCache.java","SessionManager.java","CurrencyUtil.java","CacheManager.java","TimeSyncManager.java","BidLadderUtil.java","DataReceiver.java"

# 2. Update package and Move files
function Move-And-UpdatePackage($files, $srcPkg, $destPkg, $srcDir, $destDir) {
    foreach ($f in $files) {
        $srcPath = "$srcDir\$f"
        $destPath = "$destDir\$f"
        if (Test-Path $srcPath) {
            $content = Get-Content -Path $srcPath -Raw
            $content = $content -replace "package $srcPkg;", "package $destPkg;"
            Set-Content -Path $srcPath -Value $content -NoNewline
            Move-Item $srcPath $destPath
        }
    }
}

Move-And-UpdatePackage $uiUtils "com.auction.infra.util" "com.auction.ui.util" "$base\infra\util" "$base\ui\util"
Move-And-UpdatePackage $apiConfigs "com.auction.infra.util" "com.auction.api.config" "$base\infra\util" "$base\api\config"
Move-And-UpdatePackage $coreUtils "com.auction.infra.util" "com.auction.core.util" "$base\infra\util" "$base\core\util"
Move-And-UpdatePackage @("DatabaseConnection.java") "com.auction.infra.util" "com.auction.infra.db" "$base\infra\util" "$base\infra\db"
Move-And-UpdatePackage @("AuctionManager.java") "com.auction.infra.manager" "com.auction.service" "$base\infra\manager" "$base\service"

# Remove empty dirs
if (Test-Path "$base\infra\util") { Remove-Item "$base\infra\util" -Recurse -Force }
if (Test-Path "$base\infra\manager") { Remove-Item "$base\infra\manager" -Recurse -Force }

# 3. Fix Imports Everywhere
$mappings = @{
    "com.auction.infra.util.AlertHelper" = "com.auction.ui.util.AlertHelper"
    "com.auction.infra.util.AnimationUtil" = "com.auction.ui.util.AnimationUtil"
    "com.auction.infra.util.AuctionChartHelper" = "com.auction.ui.util.AuctionChartHelper"
    "com.auction.infra.util.ImageLoaderUtil" = "com.auction.ui.util.ImageLoaderUtil"
    "com.auction.infra.util.NavigationManager" = "com.auction.ui.util.NavigationManager"
    
    "com.auction.infra.util.AppConfig" = "com.auction.api.config.AppConfig"
    "com.auction.infra.util.ServerConfig" = "com.auction.api.config.ServerConfig"
    
    "com.auction.infra.util.NotificationManager" = "com.auction.core.util.NotificationManager"
    "com.auction.infra.util.CatboxUploader" = "com.auction.core.util.CatboxUploader"
    "com.auction.infra.util.HotItemCache" = "com.auction.core.util.HotItemCache"
    "com.auction.infra.util.SessionManager" = "com.auction.core.util.SessionManager"
    "com.auction.infra.util.CurrencyUtil" = "com.auction.core.util.CurrencyUtil"
    "com.auction.infra.util.CacheManager" = "com.auction.core.util.CacheManager"
    "com.auction.infra.util.TimeSyncManager" = "com.auction.core.util.TimeSyncManager"
    "com.auction.infra.util.BidLadderUtil" = "com.auction.core.util.BidLadderUtil"
    "com.auction.infra.util.DataReceiver" = "com.auction.core.util.DataReceiver"
    
    "com.auction.infra.util.DatabaseConnection" = "com.auction.infra.db.DatabaseConnection"
    
    "com.auction.infra.manager.AuctionManager" = "com.auction.service.AuctionManager"
}

$files = Get-ChildItem -Path $base -Recurse -Filter *.java

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false

    foreach ($key in $mappings.Keys) {
        $escapedKey = [regex]::Escape($key)
        if ($content -match $escapedKey) {
            $content = $content -replace $escapedKey, $mappings[$key]
            $modified = $true
        }
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}
Write-Output "Refactoring completed."
