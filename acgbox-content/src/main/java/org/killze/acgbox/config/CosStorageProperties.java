package org.killze.acgbox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * COS 图片存储配置，凭证只从后端环境或外部私有配置读取。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "storage.cos")
public class CosStorageProperties {
    /** 上传开关，默认关闭。 */
    private boolean enabled;
    /** 桶所在地域。 */
    private String region;
    /** 带 APPID 后缀的完整桶名称。 */
    private String bucket;
    /** 受限账号的访问凭证标识。 */
    private String secretId;
    /** 受限账号的访问密钥，不生成包含该字段的日志文本。 */
    private String secretKey;
    /** 公开图片的 HTTPS 域名基址，不作为 SDK 上传端点。 */
    private String publicBaseUrl;

    /**
     * 仅在启用时检查必填项和格式，不连接 COS，也不在异常中输出配置值。
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (region == null || !region.matches("[a-z]+(?:-[a-z0-9]+)+")) {
            throw new IllegalStateException("storage.cos.region 必须填写有效的 COS 地域");
        }
        if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9-]*-[0-9]+")) {
            throw new IllegalStateException("storage.cos.bucket 必须填写带 APPID 后缀的完整桶名称");
        }
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("storage.cos.secret-id 和 secret-key 不能为空");
        }
        try {
            URI uri = URI.create(publicBaseUrl == null ? "" : publicBaseUrl);
            String path = uri.getRawPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !uri.getHost().contains(".") || uri.getHost().matches("[0-9.]+")
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (path != null && !path.matches("/*"))) {
                throw new IllegalArgumentException();
            }
            publicBaseUrl = "https://" + uri.getHost();
            // 分隔斜杠加最长对象键 anime/yyyy/MM/随机名.png 共 51 个字符。
            if (publicBaseUrl.length() + 51 > 500) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("storage.cos.public-base-url 必须为不带路径、端口或参数的 HTTPS 域名基址");
        }
    }
}
