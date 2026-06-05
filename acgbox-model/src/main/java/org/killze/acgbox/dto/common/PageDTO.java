package org.killze.acgbox.dto.common;

import lombok.Data;

/**
 * 分页参数
 */
@Data
public class PageDTO {

    /**
     * 当前页
     */
    private Integer pageNum;

    /**
     * 每页数量
     */
    private Integer pageSize;
}
