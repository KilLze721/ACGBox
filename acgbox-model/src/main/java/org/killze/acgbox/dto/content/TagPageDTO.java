package org.killze.acgbox.dto.content;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

/**
 * 标签分页参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TagPageDTO extends PageDTO {

    /**
     * 标签名称
     */
    private String name;

    /**
     * 传 NAME_INITIAL 按名称首字母排序，未传时沿用原排序。
     */
    private String sortBy;

    /**
     * 首字母排序方向，支持 ASC、DESC，默认 ASC。
     */
    private String sortDirection;
}
