package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import org.killze.acgbox.deserializer.SortOrderDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

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

    /**
     * 排序值，为空时已有公司关联保留原值，新关联按清洗后的数组位置赋值。
     */
    @Min(value = -2147483648L, message = "排序值不能小于 -2147483648")
    @Max(value = 2147483647L, message = "排序值不能大于 2147483647")
    @JsonDeserialize(using = SortOrderDeserializer.class)
    private Long sortOrder;
}
