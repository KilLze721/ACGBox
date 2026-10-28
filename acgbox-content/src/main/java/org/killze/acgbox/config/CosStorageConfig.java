package org.killze.acgbox.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理 COS 客户端生命周期，关闭功能时不创建客户端。
 */
@Configuration
@EnableConfigurationProperties(CosStorageProperties.class)
public class CosStorageConfig {
    /**
     * 启用后共享客户端连接池，应用关闭时释放资源；初始化不进行云端探测。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "storage.cos", name = "enabled", havingValue = "true")
    public COSClient cosClient(CosStorageProperties properties) {
        properties.validate();
        ClientConfig config = new ClientConfig(new Region(properties.getRegion()));
        config.setHttpProtocol(HttpProtocol.https);
        config.setConnectionRequestTimeout(5000);
        config.setConnectionTimeout(5000);
        config.setSocketTimeout(30000);
        config.setMaxErrorRetry(0);
        return new COSClient(new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey()), config);
    }
}
