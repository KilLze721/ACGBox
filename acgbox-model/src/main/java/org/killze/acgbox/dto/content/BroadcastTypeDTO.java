package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 放送类型数据传输对象
 */
@Data
public class BroadcastTypeDTO {

    /**
     * 放送类型id
     */
    private Integer id;

    /**
     * 放送类型名称
     */
    @NotBlank(message = "放送类型名称不能为空")
    private String name;
}
