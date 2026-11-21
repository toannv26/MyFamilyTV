$ErrorActionPreference = "Stop"

$proj = "C:\Users\HP\.gemini\antigravity\scratch\sony-clean-launcher"
$appDir = Join-Path $proj "android-app"
$buildDir = Join-Path $proj "build"
$sdk = "D:\PROGRAM\Android\Sdk"
$bt = Join-Path $sdk "build-tools\28.0.3"
$androidJar = Join-Path $sdk "platforms\android-28\android.jar"
$jdk = "C:\Program Files\Java\jdk-11.0.4\bin"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.4"
$env:PATH = "$jdk;$env:PATH"

if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$buildDir\res_flat","$buildDir\gen","$buildDir\classes","$buildDir\dex" | Out-Null

Write-Host "[1/6] Compiling resources with aapt2..."
$resFiles = Get-ChildItem -Path "$appDir\res" -Recurse -File
foreach ($f in $resFiles) {
    & "$bt\aapt2.exe" compile $f.FullName -o "$buildDir\res_flat"
    if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed on $($f.Name)" }
}

Write-Host "[2/6] Linking resources..."
$flatFiles = (Get-ChildItem "$buildDir\res_flat" -File).FullName
& "$bt\aapt2.exe" link `
    -I $androidJar `
    --manifest "$appDir\AndroidManifest.xml" `
    --java "$buildDir\gen" `
    -o "$buildDir\base.apk" `
    @flatFiles
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/6] Compiling Java sources..."
$javaFiles = @(Get-ChildItem -Path "$appDir\src" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName) +
             @(Get-ChildItem -Path "$buildDir\gen" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
& "$jdk\javac.exe" `
    --release 8 `
    -encoding UTF-8 `
    -classpath $androidJar `
    -d "$buildDir\classes" `
    @javaFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/6] Converting bytecode to DEX with d8..."
$classFiles = (Get-ChildItem -Path "$buildDir\classes" -Recurse -Filter "*.class").FullName
& "$jdk\java.exe" -Xmx512m -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 21 --lib $androidJar --output "$buildDir\dex" @classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/6] Packaging classes.dex & zipalign..."
Copy-Item "$buildDir\base.apk" "$buildDir\unaligned.apk"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$buildDir\unaligned.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$buildDir\dex\classes.dex", "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()

& "$bt\zipalign.exe" -f -p 4 "$buildDir\unaligned.apk" "$buildDir\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[6/6] Signing Release APK..."
$keystore = Join-Path $proj "release.jks"
if (-not (Test-Path $keystore)) {
    & "$jdk\keytool.exe" -genkeypair -v `
        -keystore $keystore -storepass familytv123 -keypass familytv123 `
        -alias familytv -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=FamilyCleanTV, OU=Home, O=Family, L=Hanoi, C=VN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

$finalApk = Join-Path $proj "FamilyCleanTV-release.apk"
& "$jdk\java.exe" -jar "$bt\lib\apksigner.jar" sign `
    --ks $keystore --ks-pass pass:familytv123 --key-pass pass:familytv123 --ks-key-alias familytv `
    --out $finalApk "$buildDir\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

$sizeKb = [math]::Round((Get-Item $finalApk).Length / 1KB, 1)
Write-Host "SUCCESS! Built $finalApk ($sizeKb KB)"
