package org.killze.acgbox.dto.content;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

/**
 * 公司分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyPageDTO extends PageDTO {

    /**
     * 公司名称
     */
    private String name;
}
