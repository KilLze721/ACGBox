package org.killze.acgbox.vo.common;

import lombok.Builder;
import lombok.Data;

/**
 * 单个基础设施依赖的连接检测结果，不包含连接地址、凭据或异常详情。
 */
@Data
@Builder
public class DependencyHealthVO {

    /**
     * 检测状态：UP、DOWN、DISABLED、UNCONFIGURED、NOT_CHECKED。
     */
    private String status;

    /**
     * 可向管理页面展示的简短说明。
     */
    private String message;
}
