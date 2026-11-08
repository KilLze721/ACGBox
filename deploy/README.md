# GitHub Actions 自动部署

适用于当前 Ubuntu 22 + 宝塔 Nginx + Docker 部署。两个 GitHub 仓库分别监听 `main` 推送；已有测试和构建全部成功才上传文件。PR 不部署，手动运行只允许发布当前 `main`。两个仓库有未提交的业务修改，本次部署配置没有提交或推送它们。

## 发布过程

- 后端：GitHub 使用 Java 21 和临时 PostgreSQL 18，向临时 `acgbox_ci` 库导入现有 `sql/public.sql` 后运行 `mvn verify`。CI 使用固定的测试凭据，不读取生产私有配置。SQL 脚本绝不在生产库执行。
- 前端：GitHub 使用 Node.js 24，执行 `npm ci`、现有 Vitest 测试、类型检查和 Vite 构建。
- 通过专用 SSH 账号上传成功构建的文件。GitHub 严格校验 SSH 主机公钥，不在运行时自动信任 `ssh-keyscan` 的结果。
- 前端沿用原网站目录，先发布资源，再原子替换首页，保留旧哈希资源。通过 Nginx 读取首页并核对内容，失败则恢复原文件。
- 后端沿用原 `acgbox-app` 容器及其网络、挂载和启动命令，停止容器后原位替换挂载的 JAR，再启动。启动探测失败则恢复旧 JAR 并启动原容器。此方式有短暂后端停机，不是零停机发布。
- GitHub 串行处理同仓库发布，并跳过已经落后于 `main` 的构建；服务器另有发布锁。前后端独立发布，不保证两个仓库同时切换。

## 1. 服务器一次性安装

先通过宝塔文件管理将本仓库整个 `deploy` 目录上传到 `/tmp/acgbox-cicd/deploy`。在独立 SSH 终端执行：

也可以上传本机生成的 `acgbox-app/target/acgbox-cicd-setup.zip` 到服务器 `/tmp`，先执行 `unzip /tmp/acgbox-cicd-setup.zip -d /tmp/acgbox-cicd`，得到相同目录结构。

```bash
sudo apt-get update
sudo apt-get install -y rsync curl openssl
sudo bash /tmp/acgbox-cicd/deploy/setup.sh
```

脚本创建专用 `acgbox-deploy` 用户，安装 `/usr/local/sbin/acgbox-deploy`，只授权这个部署入口的免密码 sudo。不会将用户加入 Docker 组，不修改数据库、现有网站或容器。

核对非敏感部署参数：

```bash
sudo nano /etc/acgbox/deploy.env
```

默认值如下，网站如果实际使用其他端口，应调整 `FRONTEND_CHECK_URL`。`FRONTEND_HOST` 填宝塔站点绑定的 IP 或域名：

```bash
FRONTEND_ROOT=/www/wwwroot/acgbox
FRONTEND_HOST=192.144.149.163
FRONTEND_CHECK_URL=http://127.0.0.1:8081/index.html
BACKEND_CONTAINER=acgbox-app
BACKEND_JAR=/opt/acgbox/acgbox-app-0.0.1-SNAPSHOT.jar
BACKEND_CHECK_URL=http://127.0.0.1:8080/anime/page
```

首次启用前确认现有前端文件存在、容器正常运行。以下命令只显示 JAR 挂载，不会打印完整容器配置中的凭据：

```bash
test -f /www/wwwroot/acgbox/index.html
sudo docker inspect -f '{{range .Mounts}}{{if eq .Destination "/app/acgbox-app-0.0.1-SNAPSHOT.jar"}}{{.Source}}{{end}}{{end}}' acgbox-app
curl --noproxy '*' -I -H 'Host: 192.144.149.163' http://127.0.0.1:8081/index.html
curl --noproxy '*' -i http://127.0.0.1:8080/anime/page
```

JAR 源路径应与 `BACKEND_JAR` 一致；前端预期 200，匿名后端探测预期 401。后端探测验证服务启动和访问控制，不代表已验证登录、数据库读写或所有业务。初始化不是数据库迁移，生产数据库结构变更必须另行安排兼容迁移和备份。

## 2. 创建专用 SSH 密钥

在本机 PowerShell 执行，文件已存在时不要覆盖：

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\acgbox_deploy" -C "github-actions-acgbox"
```

自动部署密钥设置空口令，私钥只填入 GitHub Secrets，不要发到聊天中或提交 Git。将 `.pub` 公钥文件单独上传到服务器 `/tmp/acgbox-deploy.pub`，然后执行：

```bash
sudo sh -c '{ printf "restrict "; cat /tmp/acgbox-deploy.pub; } >> /home/acgbox-deploy/.ssh/authorized_keys'
sudo chown acgbox-deploy:acgbox-deploy /home/acgbox-deploy/.ssh/authorized_keys
sudo chmod 600 /home/acgbox-deploy/.ssh/authorized_keys
```

`restrict` 禁用该密钥的端口转发和终端分配，正常 SSH 文件传输及部署命令仍可使用。生产账号、JWT、SMTP 和 COS 密钥继续保存在服务器原 `/etc/acgbox/application-secrets.yml` 中，不上传给 CI。

## 3. 核实 SSH 主机公钥

通过可信的云控制台终端，在服务器执行以下命令，得到供 GitHub 校验服务器身份的公钥行：

```bash
sudo awk '{print "192.144.149.163 " $1 " " $2}' /etc/ssh/ssh_host_ed25519_key.pub
```

这是公开的主机公钥，不是 SSH 登录私钥。若 SSH 端口不是 22，将输出首列改为 `[192.144.149.163]:实际SSH端口`。宝塔面板的 31256、网站的 8081 和 SSH 端口不是同一个端口。

## 4. 配置两个 GitHub 仓库

分别进入 `KilLze721/ACGBox` 和 `KilLze721/ACGBox-UI`：

`Settings → Secrets and variables → Actions → New repository secret`

两个仓库都设置：

| Secret | 值 |
|---|---|
| `DEPLOY_HOST` | 当前服务器公网 IP，默认部署参数为 `192.144.149.163` |
| `DEPLOY_PORT` | SSH 端口，通常 22；不填时使用 22 |
| `DEPLOY_USER` | `acgbox-deploy` |
| `DEPLOY_SSH_KEY` | 本机 `acgbox_deploy` 私钥文件完整内容 |
| `DEPLOY_KNOWN_HOSTS` | 上一步从可信服务器终端获得的完整公钥行 |

使用 GitHub 托管 runner 上传需要服务器 SSH 端口能接收 runner 的连接。如果当前 SSH 安全组仅允许自己电脑的 IP，流水线会连接超时；应根据自己的网络策略配置固定出口 runner 或合适的访问方式，不要直接关闭防火墙。

先完成服务器初始化和 Secrets，再分别提交工作流到两个仓库并推送 `main`。在仓库 `Actions` 页面查看 `Backend CI/CD` 和 `Frontend CI/CD`。发布失败会显示红色并保留日志；本机其他未提交改动不会自动上传。

## 5. 日常更新、回退与维护

正常提交并推送到各自 `main` 即可触发对应项目更新。前端更新不会重启后端。新增不兼容接口时，先发布兼容旧前端的后端，再发布前端。

旧版本上传文件保存在 `/var/lib/acgbox-deploy/incoming/{frontend,backend}/提交SHA`，快照保存在 `/opt/acgbox/releases/{frontend,backend}/提交SHA-时间`。已有上传版本可以在服务器手动重新发布：

```bash
sudo /usr/local/sbin/acgbox-deploy frontend 完整的旧提交SHA
sudo /usr/local/sbin/acgbox-deploy backend 完整的旧提交SHA
```

首次接入前的版本也会备份到首次发布快照的 `previous` 目录或 `previous.jar`。如需恢复首次接入前的版本，按对应快照恢复首页/资源或停止容器后恢复 JAR，再重新探测。

代码回退不回滚数据库结构或业务数据。旧版本和旧哈希资源暂不自动删除，需定期检查磁盘容量，在确认不再需要回滚和旧页面资源后再清理。

本地语法与构建检查不等于线上发布成功。服务器安装、Secrets、SSH 连通性和第一轮 GitHub Actions 需要在实际环境中验证。

## 本次检查记录（2026-11-08）

- 两份 YAML 解析通过；服务器脚本和工作流内嵌 Shell 的 `bash -n` 检查通过。
- 修复 `sql/public.sql` 中 `adaptation_type_id` 类型文本损坏，保持字段名和预期 `int4` 类型不变。修复后的脚本已完整导入本机隔离 PostgreSQL 18。
- `mvn --batch-mode --no-transfer-progress verify -pl acgbox-app -am`：637 项测试全部通过，后端构建成功。使用临时数据库和测试密钥，实例已停止。
- `npm run build`：类型检查和构建通过。
- `npm run test:unit -- --run`：152 项通过，16 项失败，涉及动画编辑/列表、偏好、系列、设置和工作台标签页；这些现有测试尚未修复，前端流水线因此会阻止发布。
- 以上结果基于当前工作区，包含用户原有的未提交业务修改；不代表 GitHub 上的提交或服务器发布已验证。本次没有改动这些业务文件。
- 未运行服务器安装、线上发布或回滚演练，未配置 GitHub Secrets，未提交或推送代码。

本次新增文件：后端 `.gitattributes`、`.github/workflows/cicd.yml` 和本 `deploy` 目录的四个文件；前端 `.github/workflows/cicd.yml`、`docs/CI-CD部署.md`。修改文件：后端 `sql/public.sql`。复用现有构建命令、测试、数据库结构、Nginx 目录、Docker 容器和私有配置，没有新增应用运行依赖。
