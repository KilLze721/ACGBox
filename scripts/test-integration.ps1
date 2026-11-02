#requires -Version 5.1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$testRoot = Split-Path -Parent $PSScriptRoot
$testSchema = Join-Path $testRoot 'sql\public.sql'
$testPreviousJson = [Environment]::GetEnvironmentVariable('SPRING_APPLICATION_JSON', 'Process')
$testExitCode = 1
$testContainers = @()
$testDocker = $null
$testRunId = [Guid]::NewGuid().ToString('N')
$testPostgres = "acgbox-it-pg-$testRunId"
$testRedis = "acgbox-it-redis-$testRunId"

function Wait-TestContainer {
    param([string]$Name)

    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        $state = & $testDocker inspect --format '{{.State.Status}}|{{.State.Health.Status}}' $Name
        if ($LASTEXITCODE -ne 0) { throw "无法检查测试容器：$Name" }
        if ($state -eq 'running|healthy') { return }
        if ($state -match '^(exited|dead)\|' -or $state -match '\|unhealthy$') {
            throw "测试容器初始化失败：$Name。可在 Docker Desktop 中检查镜像和初始化错误。"
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "等待测试容器就绪超时：$Name"
}

Push-Location $testRoot
try {
    $testDocker = Get-Command docker -ErrorAction Stop
    $testMaven = Get-Command mvn -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $testSchema -PathType Leaf)) {
        throw "找不到测试建表脚本：$testSchema"
    }
    & $testDocker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker 引擎未就绪，请先启动 Docker Desktop。' }

    $testDbPassword = [Guid]::NewGuid().ToString('N')
    Write-Host '创建 PostgreSQL 16 临时测试库，加载当前 public.sql。'
    # 只挂载当前完整建表脚本，不挂载业务数据库目录；端口由 Docker 分配。
    & $testDocker run --detach --rm --name $testPostgres `
        --label 'acgbox.integration-test=true' `
        --publish '127.0.0.1::5432' `
        --env 'POSTGRES_DB=acgbox_it' `
        --env 'POSTGRES_USER=acgbox_it' `
        --env "POSTGRES_PASSWORD=$testDbPassword" `
        --tmpfs '/var/lib/postgresql/data' `
        --mount "type=bind,source=$testSchema,target=/docker-entrypoint-initdb.d/01-schema.sql,readonly" `
        --health-cmd 'pg_isready -h 127.0.0.1 -U acgbox_it -d acgbox_it' `
        --health-interval '1s' --health-timeout '3s' --health-retries 60 `
        postgres:16 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '创建 PostgreSQL 测试容器失败。' }
    $testContainers += $testPostgres

    Write-Host '创建 Redis 7 临时测试实例。'
    & $testDocker run --detach --rm --name $testRedis `
        --label 'acgbox.integration-test=true' `
        --publish '127.0.0.1::6379' --tmpfs '/data' `
        --health-cmd 'redis-cli ping' `
        --health-interval '1s' --health-timeout '3s' --health-retries 60 `
        redis:7 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '创建 Redis 测试容器失败。' }
    $testContainers += $testRedis

    Wait-TestContainer $testPostgres
    Wait-TestContainer $testRedis

    # TCP 健康检查通过后，再确认当前 SQL 已完成关键表的初始化。
    $testTables = & $testDocker exec $testPostgres psql -U acgbox_it -d acgbox_it `
        -v ON_ERROR_STOP=1 -Atc "SELECT CASE WHEN to_regclass('public.anime') IS NOT NULL AND to_regclass('public.book') IS NOT NULL AND to_regclass('public.data_import_batch') IS NOT NULL AND to_regclass('public.user_account') IS NOT NULL THEN 1 ELSE 0 END;"
    if ($LASTEXITCODE -ne 0 -or $testTables -ne '1') {
        throw '测试库关键表未创建完成，请检查 sql/public.sql。'
    }
    $testPgAddress = & $testDocker port $testPostgres '5432/tcp'
    if ($LASTEXITCODE -ne 0) { throw '无法读取 PostgreSQL 测试端口。' }
    $testRedisAddress = & $testDocker port $testRedis '6379/tcp'
    if ($LASTEXITCODE -ne 0) { throw '无法读取 Redis 测试端口。' }
    $testPgPort = [int]($testPgAddress -replace '^127\.0\.0\.1:', '')
    $testRedisPort = [int]($testRedisAddress -replace '^127\.0\.0\.1:', '')

    $testKeyBytes = New-Object byte[] 32
    $testRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $testRandom.GetBytes($testKeyBytes) } finally { $testRandom.Dispose() }

    # 仅覆盖当前进程环境，子进程继承；结束时恢复原值，不保存临时凭证。
    $env:SPRING_APPLICATION_JSON = @{
        spring = @{
            datasource = @{
                url = "jdbc:postgresql://127.0.0.1:$testPgPort/acgbox_it"
                username = 'acgbox_it'
                password = $testDbPassword
            }
            data = @{
                redis = @{
                    host = '127.0.0.1'
                    port = $testRedisPort
                    username = ''
                    password = ''
                    database = 0
                }
            }
            mail = @{ host = ''; username = ''; password = '' }
        }
        jwt = @{ secret = [Convert]::ToBase64String($testKeyBytes) }
        'account-email' = @{ enabled = $false; from = '' }
        storage = @{ cos = @{ enabled = $false } }
    } | ConvertTo-Json -Depth 8 -Compress

    Write-Host "隔离环境已就绪：PostgreSQL 端口 $testPgPort，Redis 端口 $testRedisPort。"
    Write-Host '开始运行现有后端测试；不会自动补充尚未编写的测试场景。'
    & $testMaven -ntp -pl acgbox-app -am test
    $testExitCode = $LASTEXITCODE
}
catch {
    Write-Error $_ -ErrorAction Continue
    $testExitCode = 1
}
finally {
    $env:SPRING_APPLICATION_JSON = $testPreviousJson
    # 仅回收本次成功创建的随机名称容器，不清理其他容器或 Docker 镜像。
    if ($testContainers.Count -gt 0) {
        Write-Host '清理本次 PostgreSQL 和 Redis 测试容器。'
        $testCleanupPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $testDocker rm --force --volumes @testContainers 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "测试容器清理失败，请在 Docker Desktop 中检查名称包含 $testRunId 的容器。"
                if ($testExitCode -eq 0) { $testExitCode = 1 }
            }
        }
        finally { $ErrorActionPreference = $testCleanupPreference }
    }
    Pop-Location
}

Write-Host "测试报告目录：$(Join-Path $testRoot 'acgbox-app\target\surefire-reports')"
exit $testExitCode
