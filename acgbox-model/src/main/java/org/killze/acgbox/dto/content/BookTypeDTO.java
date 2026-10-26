package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.killze.acgbox.deserializer.IntegerValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 书籍类型请求，排序创建缺省为 0，更新缺省保留。
 */
@Data
public class BookTypeDTO {
    /**
     * 修改时必须传现存类型 ID。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long id;

    /**
     * 去除首尾空格后必填，最多 50 字符。
     */
    @NotBlank(message = "书籍类型名称不能为空")
    private String name;

    /**
     * 0 到 2147483647 的整数。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long sortOrder;
}
