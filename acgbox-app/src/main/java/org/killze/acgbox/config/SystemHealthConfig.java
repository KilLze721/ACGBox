package org.killze.acgbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 连接检测专用线程池，不占用业务线程池，也不无限排队等待外部依赖。
 */
@Configuration
public class SystemHealthConfig {

    /**
     * 最多同时运行三个检测，任务仍被底层网络阻塞时拒绝新任务。
     *
     * @return 连接检测线程池
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService systemHealthExecutor() {
        return new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), Thread.ofPlatform().daemon(true).name("system-health-", 0L).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
