package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 标签数据传输对象
 */
@Data
public class TagDTO {

    /**
     * 标签 ID
     */
    private Long id;

    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空")
    private String name;
}
