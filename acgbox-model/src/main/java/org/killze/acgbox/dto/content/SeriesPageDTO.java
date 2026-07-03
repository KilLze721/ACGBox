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
}
