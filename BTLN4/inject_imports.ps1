$base = "c:\Users\admin\BTLN4\BTLN4\src\main\java\com\auction"
$dirs = @("$base\ui\util", "$base\api\config", "$base\core\util", "$base\infra\db")

$imports = @"
import com.auction.ui.util.*;
import com.auction.core.util.*;
import com.auction.api.config.*;
import com.auction.infra.db.*;
"@

foreach ($dir in $dirs) {
    if (Test-Path $dir) {
        $files = Get-ChildItem -Path $dir -Filter *.java
        foreach ($file in $files) {
            $content = Get-Content -Path $file.FullName -Raw
            if (-not ($content -match "import com\.auction\.core\.util\.\*;")) {
                # Insert imports right after the package declaration
                $content = $content -replace '(?m)^(package\s+.*?;)\s*$', "`$1`r`n$imports`r`n"
                Set-Content -Path $file.FullName -Value $content -NoNewline
            }
        }
    }
}
Write-Output "Imports injected."
