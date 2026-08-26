[CmdletBinding()]
param(
    [string]$ExpectedVersionName = "2.18.1",
    [int]$ExpectedVersionCode = 103,
    [switch]$AllowDirty
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot
try {
    function Assert-Check([bool]$Condition, [string]$Message) {
        if (-not $Condition) { throw $Message }
        Write-Host "[OK] $Message"
    }

    $gradle = Get-Content -Raw -Encoding UTF8 -LiteralPath "app/build.gradle.kts"
    Assert-Check ($gradle -match "versionName\s*=\s*`"$([regex]::Escape($ExpectedVersionName))`"") "versionName 为 $ExpectedVersionName"
    Assert-Check ($gradle -match "versionCode\s*=\s*$ExpectedVersionCode\b") "versionCode 为 $ExpectedVersionCode"

    $compileSdkMatch = [regex]::Match($gradle, 'compileSdk\s*=\s*(\d+)')
    Assert-Check $compileSdkMatch.Success "已读取 compileSdk"
    $compileSdk = $compileSdkMatch.Groups[1].Value

    $wrapperProperties = Get-Content -Raw -Encoding UTF8 -LiteralPath "gradle/wrapper/gradle-wrapper.properties"
    $gradleVersionMatch = [regex]::Match($wrapperProperties, 'gradle-([0-9]+(?:\.[0-9]+)+)-(?:all|bin)\.zip')
    Assert-Check $gradleVersionMatch.Success "已读取 Gradle Wrapper 版本"
    $gradleVersion = $gradleVersionMatch.Groups[1].Value

    $readme = Get-Content -Raw -Encoding UTF8 -LiteralPath "README.md"
    $maintenance = Get-Content -Raw -Encoding UTF8 -LiteralPath "docs/MAINTENANCE.md"
    $readmeVersionMarker = ('`{0}`（versionCode {1}）' -f $ExpectedVersionName, $ExpectedVersionCode)
    $maintenanceVersionMarker = "当前候选版本：$ExpectedVersionName，versionCode $ExpectedVersionCode"
    Assert-Check ($readme.Contains($readmeVersionMarker)) "README 版本与构建配置一致"
    Assert-Check ($maintenance.Contains($maintenanceVersionMarker)) "维护文档版本与构建配置一致"
    Assert-Check ($readme.Contains("Android SDK $compileSdk")) "README Android SDK 与 compileSdk 一致"
    Assert-Check ($readme.Contains("Gradle $gradleVersion")) "README Gradle 版本与 Wrapper 一致"
    Assert-Check (Test-Path -LiteralPath "scripts/build.ps1") "JDK 17 构建预检脚本存在"

    $manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath "app/src/main/AndroidManifest.xml"
    Assert-Check ($manifest -match 'android:allowBackup="false"') "系统自动备份已关闭"
    Assert-Check ($manifest -notmatch 'android\.permission\.INTERNET') "Manifest 未声明 INTERNET 权限"
    Assert-Check (Test-Path -LiteralPath "app/src/main/res/xml/backup_rules.xml") "旧版系统备份排除规则存在"
    Assert-Check (Test-Path -LiteralPath "app/src/main/res/xml/data_extraction_rules.xml") "Android 12+ 数据提取规则存在"

    $gradleProperties = Get-Content -Raw -Encoding UTF8 -LiteralPath "gradle.properties"
    Assert-Check ($gradleProperties -notmatch '(?im)^\s*org\.gradle\.jvmargs=.*-Dfile\.encoding') "未强制覆盖 Gradle JVM 文件编码"

    # 同时检查已跟踪文件和未被 .gitignore 排除的新文件，避免“尚未 git add”
    # 的签名材料或真实课表在后续统一提交时漏过审计。
    $publishable = @(git ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw "无法读取 Git 待发布文件" }
    $sensitive = @($publishable | Where-Object {
        $_ -match '(?i)(^|/)(local\.properties|keystore\.properties|signing\.properties)$' -or
        $_ -match '(?i)\.(jks|keystore|p12|pfx|pem|key|xls|xlsx|apk|aab|apks)$' -or
        $_ -match '(?i)(course-table-backup|课表备份).*\.json$'
    })
    Assert-Check ($sensitive.Count -eq 0) "Git 待发布文件不含签名材料、真实课表、APK 或完整备份"

    foreach ($probe in @("local.properties", "private.xlsx", "private.keystore", "release.apk", "course-table-backup.json")) {
        git check-ignore --no-index --quiet -- $probe
        Assert-Check ($LASTEXITCODE -eq 0) ".gitignore 会排除 $probe"
    }

    $status = @(git status --porcelain=v1)
    if ($LASTEXITCODE -ne 0) { throw "无法读取 Git 状态" }
    if ($status.Count -gt 0 -and -not $AllowDirty) {
        throw "Git 工作区不是干净状态；请审查改动后再发布，或仅在开发验证时使用 -AllowDirty"
    }
    if ($status.Count -gt 0) {
        Write-Host "[INFO] Git 工作区有 $($status.Count) 条改动（已通过 -AllowDirty 允许）"
    } else {
        Write-Host "[OK] Git 工作区干净"
    }

    Write-Host "发布前本地审计通过；脚本未访问网络，也不会读取仓库外文件。"
} finally {
    Pop-Location
}
