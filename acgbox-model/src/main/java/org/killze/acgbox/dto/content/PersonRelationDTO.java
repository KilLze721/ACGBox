package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.killze.acgbox.deserializer.SortOrderDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 动画人物关联请求，目标类型和目标 ID 由动画服务设置。
 */
@Data
public class PersonRelationDTO {

    /**
     * 已有人物 ID。
     */
    @NotNull(message = "人物不能为空")
    @Positive(message = "人物 ID 必须大于 0")
    private Long personId;

    /**
     * 本作品中的职责，去除首尾空格后最多 50 字符。
     */
    @NotBlank(message = "人物职责不能为空")
    private String role;

    /**
     * 非负署名排序值，未传或为空时使用数组位置。
     */
    @Min(value = 0L, message = "人物排序值不能小于 0")
    @Max(value = 2147483647L, message = "人物排序值不能大于 2147483647")
    @JsonDeserialize(using = SortOrderDeserializer.class)
    private Long sortOrder;
}
