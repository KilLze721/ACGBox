package org.killze.acgbox.dto.common;

import lombok.Data;

/**
 * 分页参数
 */
@Data
public class PageDTO {

    /**
     * 当前页码
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;
}
