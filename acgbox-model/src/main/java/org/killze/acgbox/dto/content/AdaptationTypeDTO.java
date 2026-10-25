package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import org.killze.acgbox.deserializer.SortOrderDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 改编类型数据传输对象
 */
@Data
public class AdaptationTypeDTO {

    /**
     * 改编类型 ID
     */
    private Long id;

    /**
     * 改编类型名称
     */
    @NotBlank(message = "改编类型名称不能为空")
    private String name;

    /**
     * 排序值，创建时为空默认 0，修改时为空保留原值。
     */
    @Min(value = -2147483648L, message = "排序值不能小于 -2147483648")
    @Max(value = 2147483647L, message = "排序值不能大于 2147483647")
    @JsonDeserialize(using = SortOrderDeserializer.class)
    private Long sortOrder;
}
