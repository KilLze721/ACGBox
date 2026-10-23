package org.killze.acgbox.service;

import org.killze.acgbox.vo.common.SystemHealthVO;

/**
 * 管理端基础设施连接检测服务。
 */
public interface SystemHealthService {

    /**
     * 返回数据库、Redis 和 SMTP 的连接检测结果，复用三十秒内的检测结果。
     *
     * @return 连接检测结果
     */
    SystemHealthVO getSystemHealth();
}
