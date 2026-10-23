package org.killze.acgbox.vo.common;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 管理端基础设施连接检测结果，检测时间在缓存命中时保持不变。
 */
@Data
@Builder
public class SystemHealthVO {

    /**
     * 本次实际检测完成时间，使用带时区的时间格式。
     */
    private OffsetDateTime checkedAt;

    /**
     * 数据库连接检测结果。
     */
    private DependencyHealthVO database;

    /**
     * Redis 连接检测结果。
     */
    private DependencyHealthVO redis;

    /**
     * SMTP 连接检测结果，不代表实际邮件投递成功。
     */
    private DependencyHealthVO smtp;
}
