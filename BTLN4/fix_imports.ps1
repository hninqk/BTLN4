$base = "c:\Users\admin\BTLN4\BTLN4\src\main\java\com\auction"
$files = Get-ChildItem -Path $base -Recurse -Filter *.java

$packages = @{
    "com.auction.ui.util" = @("AlertHelper", "AnimationUtil", "AuctionChartHelper", "ImageLoaderUtil", "NavigationManager")
    "com.auction.core.util" = @("BidLadderUtil", "CacheManager", "CatboxUploader", "CurrencyUtil", "DataReceiver", "HotItemCache", "NotificationManager", "SessionManager", "TimeSyncManager")
    "com.auction.api.config" = @("AppConfig", "ServerConfig")
    "com.auction.infra.db" = @("DatabaseConnection")
    "com.auction.core.factory" = @("VehicleFactory", "ArtFactory", "ElectronicsFactory", "ItemFactory")
    "com.auction.service.security" = @("PasswordHashService")
}

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw
    
    # Check if we should process
    $filePkg = ""
    if ($content -match "(?m)^package\s+(.*?);") {
        $filePkg = $Matches[1]
    }
    
    $newImports = @()
    
    foreach ($pkg in $packages.Keys) {
        if ($pkg -eq $filePkg) { continue }
        $classes = $packages[$pkg]
        foreach ($cls in $classes) {
            # Match the class name as a whole word, but ensure we don't just match the import statement itself
            # We will first remove all our wildcards, then check.
            # But wait, what if the class was already explicitly imported?
            # We don't want to add duplicate explicit imports.
            if ($content -match "(?m)^import\s+${pkg}\.${cls};") {
                continue
            }
            if ($content -match "\b$cls\b") {
                $newImports += "import ${pkg}.${cls};"
            }
        }
    }
    
    # Remove all wildcard imports we previously injected
    $content = $content -replace "(?m)^import com\.auction\.ui\.util\.\*;\r?\n?", ""
    $content = $content -replace "(?m)^import com\.auction\.core\.util\.\*;\r?\n?", ""
    $content = $content -replace "(?m)^import com\.auction\.api\.config\.\*;\r?\n?", ""
    $content = $content -replace "(?m)^import com\.auction\.infra\.db\.\*;\r?\n?", ""
    $content = $content -replace "(?m)^import com\.auction\.core\.factory\.\*;\r?\n?", ""
    $content = $content -replace "(?m)^import com\.auction\.service\.security\.\*;\r?\n?", ""
    
    # If we have new explicit imports, inject them after package declaration
    if ($newImports.Length -gt 0) {
        $importsStr = ($newImports -join "`r`n") + "`r`n"
        $content = $content -replace '(?m)^(package\s+.*?;)\s*$', "`$1`r`n$importsStr"
    }
    
    Set-Content -Path $file.FullName -Value $content -NoNewline
}
Write-Output "Imports fixed."
