package org.killze.acgbox.dto.content;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

/**
 * 系列分页查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SeriesPageDTO extends PageDTO {

    /**
     * 系列名称
     */
    private String name;

    /**
     * 传 NAME_INITIAL 按名称首字母排序，未传时沿用原排序。
     */
    private String sortBy;
}
