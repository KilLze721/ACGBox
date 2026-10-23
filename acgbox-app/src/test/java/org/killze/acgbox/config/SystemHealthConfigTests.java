package org.killze.acgbox.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证检测线程有数量上限，不为超时网络操作无限排队。
 */
class SystemHealthConfigTests {

    @Test
    void shouldRejectFourthTaskWhileThreeWorkersAreBusy() throws Exception {
        var executor = new SystemHealthConfig().systemHealthExecutor();
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int index = 0; index < 3; index++) {
                executor.submit(() -> {
                    started.countDown();
                    release.await();
                    return null;
                });
            }
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> null));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
