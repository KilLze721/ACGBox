package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系列数据传输对象。
 */
@Data
public class SeriesDTO {

    /**
     * 系列 id
     */
    private Long id;

    /**
     * 系列名称
     */
    @NotBlank(message = "系列名称不能为空")
    private String name;

    /**
     * 系列简介
     */
    private String description;
}
