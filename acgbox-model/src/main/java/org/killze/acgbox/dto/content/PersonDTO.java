package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 人物资料数据传输对象。
 */
@Data
public class PersonDTO {

    /**
     * 修改时的人物 ID，创建时不使用客户端 ID。
     */
    private Long id;

    /**
     * 姓名或署名，去除首尾空格后最多 100 字符，允许同名。
     */
    @NotBlank(message = "人物姓名不能为空")
    private String name;

    /**
     * 人物简介，空串或全空白保存为 NULL。
     */
    private String description;
}
