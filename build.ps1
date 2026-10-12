param(
    [string]$Manifest = 'AndroidManifest.xml',
    [string]$OutputName = '',
    [string]$SdkPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$JavaPath = (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
    [string]$KeyStore = (Join-Path $env:USERPROFILE '.android\debug.keystore'),
    [string]$KeyAlias = 'androiddebugkey',
    [switch]$Distribution
)
$ErrorActionPreference = 'Stop'


$toolsPath = Join-Path $sdkPath 'build-tools\36.0.0'
$androidJar = Join-Path $sdkPath 'platforms\android-35\android.jar'
$env:JAVA_HOME = $javaPath
$env:PATH = "$javaPath\bin;$env:PATH"
Set-Location $PSScriptRoot
[xml]$appManifest = Get-Content -LiteralPath $Manifest -Raw
$versionName = $appManifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
if ([string]::IsNullOrWhiteSpace($OutputName)) { $OutputName = "Raw4All-v$versionName.apk" }
$buildWorkPath = Join-Path $PSScriptRoot ('build\compile-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force (Join-Path $buildWorkPath 'classes'),(Join-Path $buildWorkPath 'dex') | Out-Null
function CheckExit { if ($LASTEXITCODE -ne 0) { throw "Build tool failed: $LASTEXITCODE" } }
& "$toolsPath\aapt2.exe" compile --dir res -o build\resources.zip
CheckExit
& "$toolsPath\aapt2.exe" link --manifest $Manifest -I $androidJar build\resources.zip -o build\unsigned.apk
CheckExit
& "$javaPath\bin\javac.exe" --release 8 -classpath $androidJar -d (Join-Path $buildWorkPath 'classes') src\local\s23probe\MainActivity.java src\local\s23probe\FpsPolicy.java src\local\s23probe\HorizonRecorder.java src\local\s23probe\HorizonFilter.java src\local\s23probe\Raw10.java src\local\s23probe\RawDng.java src\local\s23probe\RecordingClock.java
CheckExit
& "$javaPath\bin\jar.exe" cf build\classes.jar -C (Join-Path $buildWorkPath 'classes') .
CheckExit
& "$toolsPath\d8.bat" --min-api 29 --lib $androidJar --output (Join-Path $buildWorkPath 'dex') build\classes.jar
CheckExit
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkArchive = [System.IO.Compression.ZipFile]::Open((Join-Path $PSScriptRoot 'build\unsigned.apk'), 'Update')
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkArchive, (Join-Path $buildWorkPath 'dex\classes.dex'), 'classes.dex') | Out-Null
} finally { $apkArchive.Dispose() }
& "$toolsPath\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk
CheckExit
if ($Distribution) {
    if ([string]::IsNullOrWhiteSpace($env:RAW4ALL_STORE_PASSWORD)) { throw 'Set RAW4ALL_STORE_PASSWORD for distribution signing.' }
    & "$javaPath\bin\java.exe" -jar "$toolsPath\lib\apksigner.jar" sign --ks $KeyStore --ks-key-alias $KeyAlias --ks-pass env:RAW4ALL_STORE_PASSWORD --key-pass env:RAW4ALL_STORE_PASSWORD --out "build\$OutputName" build\aligned.apk
} else {
    & "$javaPath\bin\java.exe" -jar "$toolsPath\lib\apksigner.jar" sign --ks $KeyStore --ks-key-alias $KeyAlias --ks-pass pass:android --key-pass pass:android --out "build\$OutputName" build\aligned.apk
}
CheckExit
& "$javaPath\bin\java.exe" -jar "$toolsPath\lib\apksigner.jar" verify --verbose --print-certs "build\$OutputName"
CheckExit
$artifactPath = Join-Path $PSScriptRoot "build\$OutputName"
$artifactHash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$artifactPath.sha256" -Value "$artifactHash  $OutputName" -Encoding ASCII
Write-Output "Built Raw4All $versionName : $artifactPath"
Write-Output "SHA256: $artifactHash"
