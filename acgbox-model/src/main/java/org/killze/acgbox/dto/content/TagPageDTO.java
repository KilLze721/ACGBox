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
}
