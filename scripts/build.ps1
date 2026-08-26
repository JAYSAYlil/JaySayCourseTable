[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string[]]$Tasks = @(
        ":app:testDebugUnitTest",
        ":app:lintRelease",
        ":app:assembleDebug",
        ":app:assembleRelease"
    ),
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Resolve-JavaExecutable([string]$CandidateHome) {
    if (-not [string]::IsNullOrWhiteSpace($CandidateHome)) {
        $candidate = Join-Path $CandidateHome "bin\java.exe"
        if (Test-Path -LiteralPath $candidate) { return (Resolve-Path $candidate).Path }
        throw "JAVA_HOME/JavaHome 无效：$CandidateHome。请指向完整的 JDK 17 安装目录。"
    }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $command) { throw "未找到 Java。请安装 JDK 17，或通过 -JavaHome 指定其目录。" }
    return $command.Source
}

$javaExecutable = Resolve-JavaExecutable $JavaHome
$versionOutput = (& $javaExecutable -version 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw "无法读取 Java 版本：$javaExecutable" }
$versionMatch = [regex]::Match($versionOutput, 'version\s+"(?<major>\d+)')
if (-not $versionMatch.Success) { throw "无法识别 Java 版本：$versionOutput" }
$major = [int]$versionMatch.Groups["major"].Value
if ($major -ne 17) {
    throw "当前是 JDK $major，项目固定使用 JDK 17。请仅为当前终端设置 JAVA_HOME，或传入 -JavaHome；脚本不会修改系统 Java 配置。"
}
$resolvedJavaHome = Split-Path (Split-Path $javaExecutable -Parent) -Parent
Write-Host "[OK] 使用 JDK 17：$javaExecutable"

if ($CheckOnly) { return }
if ($Tasks.Count -eq 0) { throw "至少需要提供一个 Gradle 任务。" }

Push-Location $repoRoot
$previousJavaHome = $env:JAVA_HOME
try {
    # 只约束本次构建子进程，避免系统 JAVA_HOME 或已有高版本 JDK 被 Gradle 误用。
    $env:JAVA_HOME = $resolvedJavaHome
    & (Join-Path $repoRoot "gradlew.bat") @Tasks
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
