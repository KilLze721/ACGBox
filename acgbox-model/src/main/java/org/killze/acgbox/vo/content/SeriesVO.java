package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 系列视图对象。
 */
@Data
@Builder
public class SeriesVO {

    /**
     * 系列 id
     */
    private Long id;

    /**
     * 系列名称
     */
    private String name;

    /**
     * 系列简介
     */
    private String description;
}
