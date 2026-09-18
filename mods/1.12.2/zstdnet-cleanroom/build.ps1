param(
    [string[]]$Tasks = @('build'),
    [string]$Proxy = ''
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$toolRoot = Join-Path $repo '.tools'
New-Item -ItemType Directory -Force $toolRoot | Out-Null
function Get-Toolchain([string]$Name, [string]$Directory, [string]$Url, [string]$Checksum) {
    $destination = Join-Path $toolRoot $Name
    $jdk = Join-Path $destination $Directory
    if (!(Test-Path (Join-Path $jdk 'bin/java.exe'))) {
        $archive = Join-Path $toolRoot ($Name + '.zip')
        $valid = (Test-Path $archive) -and ((Get-FileHash $archive -Algorithm SHA256).Hash -eq $Checksum)
        if (!$valid) {
            $download = @('-fL', '--retry', '3', '--connect-timeout', '30', '--max-time', '600', '-o', $archive, $Url)
            if ($Proxy) { $download = @('--proxy', $Proxy) + $download }
            & curl.exe @download
            if ($LASTEXITCODE -ne 0) { throw "Download failed: $Name" }
        }
        if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $Checksum) { throw "Checksum mismatch: $Name" }
        Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
    }
    return $jdk
}
$jdk25 = Get-Toolchain 'jdk25' 'jdk-25.0.4.1+1' 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip' '00c847d804f4a78e9f04f2683faf14fed898535b177b7fc704486cb0284e9283'
$jdk8 = Get-Toolchain 'jdk8' 'jdk8u504-b01' 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip' 'ea43d46ede95b51e44a12c66711706cddc762e0a766c54bccea18954e902b2aa'
$savedJava = $env:JAVA_HOME
$savedGradle = $env:GRADLE_USER_HOME
$savedOptions = $env:JAVA_OPTS
try {
    $env:JAVA_HOME = $jdk25
    $env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-home'
    if ($Proxy) {
        $uri = [Uri]$Proxy
        if ($uri.Scheme -ne 'http' -or $uri.UserInfo) { throw 'Use an HTTP proxy URL without credentials.' }
        $env:JAVA_OPTS = "$savedOptions -Dhttps.proxyHost=$($uri.Host) -Dhttps.proxyPort=$($uri.Port) -Dhttp.proxyHost=$($uri.Host) -Dhttp.proxyPort=$($uri.Port)"
    }
    $jdkPaths = ($jdk25 + ',' + $jdk8).Replace('\', '/')
    & (Join-Path $PSScriptRoot 'gradlew.bat') -p $PSScriptRoot "-Porg.gradle.java.installations.paths=$jdkPaths" --console=plain --no-daemon @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $savedJava
    $env:GRADLE_USER_HOME = $savedGradle
    $env:JAVA_OPTS = $savedOptions
}
