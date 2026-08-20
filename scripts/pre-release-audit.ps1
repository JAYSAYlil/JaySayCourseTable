[CmdletBinding()]
param(
    [string]$ExpectedVersionName = "2.12.0",
    [int]$ExpectedVersionCode = 85,
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

    $gradle = Get-Content -Raw -LiteralPath "app/build.gradle.kts"
    Assert-Check ($gradle -match "versionName\s*=\s*`"$([regex]::Escape($ExpectedVersionName))`"") "versionName 为 $ExpectedVersionName"
    Assert-Check ($gradle -match "versionCode\s*=\s*$ExpectedVersionCode\b") "versionCode 为 $ExpectedVersionCode"

    $manifest = Get-Content -Raw -LiteralPath "app/src/main/AndroidManifest.xml"
    Assert-Check ($manifest -match 'android:allowBackup="false"') "系统自动备份已关闭"
    Assert-Check ($manifest -notmatch 'android\.permission\.INTERNET') "Manifest 未声明 INTERNET 权限"
    Assert-Check (Test-Path -LiteralPath "app/src/main/res/xml/backup_rules.xml") "旧版系统备份排除规则存在"
    Assert-Check (Test-Path -LiteralPath "app/src/main/res/xml/data_extraction_rules.xml") "Android 12+ 数据提取规则存在"

    $gradleProperties = Get-Content -Raw -LiteralPath "gradle.properties"
    Assert-Check ($gradleProperties -notmatch '(?im)^\s*org\.gradle\.jvmargs=.*-Dfile\.encoding') "未强制覆盖 Gradle JVM 文件编码"

    $tracked = @(git ls-files)
    if ($LASTEXITCODE -ne 0) { throw "无法读取 Git 跟踪文件" }
    $sensitive = @($tracked | Where-Object {
        $_ -match '(?i)(^|/)(local\.properties|keystore\.properties|signing\.properties)$' -or
        $_ -match '(?i)\.(jks|keystore|p12|pfx|pem|key|xls|xlsx|apk|aab|apks)$' -or
        $_ -match '(?i)(course-table-backup|课表备份).*\.json$'
    })
    Assert-Check ($sensitive.Count -eq 0) "Git 未跟踪签名材料、真实课表、APK 或完整备份"

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
