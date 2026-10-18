package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

/**
 * 用户账号单条删除数据传输对象。
 */
@Data
public class UserAccountDeleteDTO {

    /**
     * 需要删除的用户账号 ID，不允许删除管理员。
     */
    @NotNull(message = "用户账号 ID 不能为空")
    @Min(value = 1, message = "用户账号 ID 必须大于等于 1")
    private Long id;

    /**
     * 执行操作的管理员当前密码，不是目标账号的原密码。
     */
    @NotBlank(message = "管理员密码不能为空")
    @ToString.Exclude
    private String currentPassword;
}
