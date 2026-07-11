package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 公司关联视图对象
 */
@Data
@Builder
public class CompanyRelationVO {

    /**
     * 公司 ID
     */
    private Long companyId;

    /**
     * 公司在对象中的职责，例如制作、发行、企划
     */
    private String role;
}
