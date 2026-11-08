#!/usr/bin/env bash
set -Eeuo pipefail

# 只允许发布工作流上传的指定版本，不接受任意文件路径。
component=${1:-}
revision=${2:-}
[[ "$component" == frontend || "$component" == backend ]] || exit 2
[[ "$revision" =~ ^[0-9a-f]{40}$ ]] || exit 2
[[ $EUID == 0 ]] || { echo '请通过 sudo 运行部署命令。'; exit 2; }

source /etc/acgbox/deploy.env
incoming="/var/lib/acgbox-deploy/incoming/$component/$revision"
release="/opt/acgbox/releases/$component/$revision-$(date +%Y%m%d%H%M%S)"
exec 9>"/run/lock/acgbox-$component.lock"
flock -w 300 9
[[ -d "$incoming" && ! -L "$incoming" ]] || exit 2
install -d -m 700 "$release"

check_backend() {
    local code
    for ((attempt=0; attempt<45; attempt++)); do
        code=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' --max-time 3 "$BACKEND_CHECK_URL") || true
        if [[ "$code" == 401 ]] && [[ "$(docker inspect -f '{{.State.Running}}' "$BACKEND_CONTAINER")" == true ]]; then
            return 0
        fi
        sleep 2
    done
    return 1
}

if [[ "$component" == frontend ]]; then
    [[ -f "$incoming/index.html" && -f "$FRONTEND_ROOT/index.html" ]] || exit 2
    # 保留完整旧版本；资源先上传，入口最后原子替换，旧哈希资源继续保留。
    mkdir "$release/previous" "$release/new"
    rsync -a "$FRONTEND_ROOT/" "$release/previous/"
    rsync -a --no-owner --no-group "$incoming/" "$release/new/"
    [[ -z "$(find "$release/new" ! -type f ! -type d -print -quit)" ]] || { echo '上传内容包含非普通文件，停止发布。'; exit 2; }
    rollback_frontend() {
        trap - ERR HUP INT TERM
        echo '前端发布失败，恢复旧页面。'
        rsync -a --exclude=index.html "$release/previous/" "$FRONTEND_ROOT/"
        install -m 644 "$release/previous/index.html" "$FRONTEND_ROOT/.index-rollback.html"
        mv -f "$FRONTEND_ROOT/.index-rollback.html" "$FRONTEND_ROOT/index.html"
        exit 1
    }
    trap rollback_frontend ERR HUP INT TERM
    rsync -a --chmod=D755,F644 --exclude=index.html "$release/new/" "$FRONTEND_ROOT/"
    install -m 644 "$release/new/index.html" "$FRONTEND_ROOT/.index-$revision.html"
    mv -f "$FRONTEND_ROOT/.index-$revision.html" "$FRONTEND_ROOT/index.html"
    curl --noproxy '*' -fsS --max-time 10 -H "Host: $FRONTEND_HOST" \
        -H 'Cache-Control: no-cache' "$FRONTEND_CHECK_URL" -o "$release/served.html"
    cmp "$release/new/index.html" "$release/served.html"
    trap - ERR HUP INT TERM
else
    [[ -f "$incoming/acgbox-app-0.0.1-SNAPSHOT.jar" && -f "$BACKEND_JAR" ]] || exit 2
    # 沿用当前容器的网络、私有配置和启动命令，确认部署文件确实挂载给它。
    jar_source=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/app/acgbox-app-0.0.1-SNAPSHOT.jar"}}{{.Source}}{{end}}{{end}}' "$BACKEND_CONTAINER")
    [[ "$jar_source" == "$BACKEND_JAR" ]] || { echo '现有容器的 JAR 挂载与部署配置不一致。'; exit 2; }
    [[ "$(docker inspect -f '{{.State.Running}}' "$BACKEND_CONTAINER")" == true ]] || { echo '现有容器未运行，停止发布。'; exit 2; }
    install -m 644 "$BACKEND_JAR" "$release/previous.jar"
    cp -P "$incoming/acgbox-app-0.0.1-SNAPSHOT.jar" "$release/new.jar"
    [[ -f "$release/new.jar" && ! -L "$release/new.jar" ]] || exit 2
    rollback_backend() {
        trap - ERR HUP INT TERM
        echo '后端发布失败，恢复旧 JAR。'
        docker stop -t 30 "$BACKEND_CONTAINER" || true
        install -m 644 "$release/previous.jar" "$BACKEND_JAR"
        docker start "$BACKEND_CONTAINER"
        if ! check_backend; then echo '旧版本未恢复，请检查容器日志。'; fi
        exit 1
    }
    trap rollback_backend ERR HUP INT TERM
    docker stop -t 30 "$BACKEND_CONTAINER"
    # 在容器停止时原位替换，保留文件挂载关系，随后重新启动原容器。
    cat "$release/new.jar" > "$BACKEND_JAR"
    docker start "$BACKEND_CONTAINER"
    check_backend
    trap - ERR HUP INT TERM
fi
printf '%s\n' "$revision" > "/opt/acgbox/releases/$component/current-revision"
echo "$component 发布完成：$revision"
