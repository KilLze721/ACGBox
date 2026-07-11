package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 公司关联数据传输对象
 */
@Data
public class CompanyRelationDTO {

    /**
     * 公司 ID
     */
    @NotNull(message = "公司不能为空")
    private Long companyId;

    /**
     * 公司在对象中的职责，例如制作、发行、企划
     */
    private String role;
}
