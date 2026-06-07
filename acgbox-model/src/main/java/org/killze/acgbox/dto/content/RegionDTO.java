package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 区域数据传输对象
 */
@Data
public class RegionDTO {
    /**
     * 地区id
     */
    private Integer id;

    /**
     * 地区名称
     */
    @NotBlank(message = "地区名称不能为空")
    private String name;
}
