# 本地隔离集成测试

先启动 Docker Desktop（Linux 容器模式），确保 JDK 21、Maven 和 Docker 命令可用。在 PowerShell 中运行：

```powershell
& 'D:\ACGBox\ACGBox\scripts\test-integration.ps1'
```

若当前 Windows 策略禁止执行脚本，可仅对新启动的测试进程放开策略：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\ACGBox\ACGBox\scripts\test-integration.ps1'
```

不需要管理员终端，不修改系统永久环境变量或执行策略。首次运行需要下载 `postgres:16`、`redis:7` 镜像以及尚未缓存的 Maven 依赖。

## 每次运行的行为

1. 以随机名称创建 PostgreSQL 和 Redis 临时容器，端口仅绑定本机且由 Docker 自动分配，不复用之前手动创建的测试容器。
2. 只读挂载当前 `sql/public.sql` 初始化新数据库。当前脚本已包含 `data_import_batch`，无需另一个增量文件；初始化后检查关键表。
3. 随机生成测试数据库密码和 JWT 密钥，通过当前进程的 `SPRING_APPLICATION_JSON` 覆盖测试连接，关闭真实邮件及 COS 上传。
4. 在后端根目录执行 `mvn -ntp -pl acgbox-app -am test`，运行现有单元测试及集成测试。
5. 成功或发生普通异常时通过 `finally` 回收本次容器、丢弃临时数据、恢复原环境变量和工作目录。测试失败返回非零退出码，报告保留在 `acgbox-app/target/surefire-reports`。

`public.sql` 含删除重建语句，仅用于这个全新的临时数据库。PostgreSQL 数据目录与 Redis 的 `/data` 使用 tmpfs；Docker 镜像会保留以供下次复用。脚本不读取业务数据库，也不修改原 SQL。

## 验证边界与异常退出

搭建真实服务不会把模拟测试自动变成真实接口测试，尚未编写的导入并发、缓存故障与完整接口闭环场景仍需补充。脚本不验证真实 SMTP 或 COS，不保证所有现有测试必然通过。

强制结束 PowerShell 进程或机器断电可能绕过清理。可在 Docker Desktop 中通过 `acgbox.integration-test=true` 标签定位遗留容器，确认后只删除该次测试容器；不要批量清理其他容器。初始化失败时容器可能已因 `--rm` 自动删除，可检查当前 SQL 或重新运行时观察 Docker 错误输出。
