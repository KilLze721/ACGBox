package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
}
