#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo '请使用 sudo bash deploy/setup.sh。'; exit 2; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
for command in docker rsync curl flock openssl; do command -v "$command" >/dev/null; done

if ! id acgbox-deploy >/dev/null 2>&1; then
    useradd --create-home --shell /bin/bash acgbox-deploy
fi
install -d -o acgbox-deploy -g acgbox-deploy -m 750 \
    /var/lib/acgbox-deploy/incoming/frontend /var/lib/acgbox-deploy/incoming/backend
install -d -o acgbox-deploy -g acgbox-deploy -m 700 /home/acgbox-deploy/.ssh
install -d -o root -g root -m 755 /etc/acgbox /opt/acgbox/releases
install -o root -g root -m 755 "$script_dir/acgbox-deploy.sh" /usr/local/sbin/acgbox-deploy
if [[ ! -e /etc/acgbox/deploy.env ]]; then
    install -o root -g root -m 600 "$script_dir/deploy.env.example" /etc/acgbox/deploy.env
fi
sudoers_tmp=$(mktemp)
trap 'rm -f "$sudoers_tmp"' EXIT
printf '%s\n' 'acgbox-deploy ALL=(root) NOPASSWD: /usr/local/sbin/acgbox-deploy' > "$sudoers_tmp"
visudo -cf "$sudoers_tmp"
install -o root -g root -m 440 "$sudoers_tmp" /etc/sudoers.d/acgbox-deploy
echo '部署工具已安装。请核对 /etc/acgbox/deploy.env，再安装专用 SSH 公钥并配置 GitHub Secrets。'
