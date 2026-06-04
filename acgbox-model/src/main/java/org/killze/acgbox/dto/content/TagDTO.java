package org.killze.acgbox.dto.content;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class TagDTO {

    private Integer id;

    @NotBlank(message = "标签名称不能为空")
    private String name;
}
