package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 公司数据传输对象
 */
@Data
public class CompanyDTO {

    /**
     * 公司 ID
     */
    private Long id;

    /**
     * 公司名称
     */
    @NotBlank(message = "公司名称不能为空")
    private String name;

    /**
     * 公司简介
     */
    private String description;
}
