$base = "c:\Users\admin\BTLN4\BTLN4\src\main\java\com\auction"

mkdir -Force "$base\core\factory" | Out-Null
mkdir -Force "$base\service\security" | Out-Null

$factories = "VehicleFactory.java","ArtFactory.java","ElectronicsFactory.java","ItemFactory.java"
$securities = "PasswordHashService.java"

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

Move-And-UpdatePackage $factories "com.auction.infra.factory" "com.auction.core.factory" "$base\infra\factory" "$base\core\factory"
Move-And-UpdatePackage $securities "com.auction.infra.security" "com.auction.service.security" "$base\infra\security" "$base\service\security"

if (Test-Path "$base\infra\factory") { Remove-Item "$base\infra\factory" -Recurse -Force }
if (Test-Path "$base\infra\security") { Remove-Item "$base\infra\security" -Recurse -Force }

$mappings = @{
    "com.auction.infra.factory.VehicleFactory" = "com.auction.core.factory.VehicleFactory"
    "com.auction.infra.factory.ArtFactory" = "com.auction.core.factory.ArtFactory"
    "com.auction.infra.factory.ElectronicsFactory" = "com.auction.core.factory.ElectronicsFactory"
    "com.auction.infra.factory.ItemFactory" = "com.auction.core.factory.ItemFactory"
    "com.auction.infra.security.PasswordHashService" = "com.auction.service.security.PasswordHashService"
    "import com\.auction\.infra\.factory\.\*;" = "import com.auction.core.factory.*;"
    "import com\.auction\.infra\.security\.\*;" = "import com.auction.service.security.*;"
}

$files = Get-ChildItem -Path $base -Recurse -Filter *.java

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false

    foreach ($key in $mappings.Keys) {
        $regexKey = $key
        if (-not ($key -match "\\\*")) {
            $regexKey = [regex]::Escape($key)
        }
        
        if ($content -match $regexKey) {
            $content = $content -replace $regexKey, $mappings[$key]
            $modified = $true
        }
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Output "Refactoring completed."
