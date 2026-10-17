package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

/**
 * 用户账号密码重置数据传输对象。
 */
@Data
public class UserAccountPasswordDTO {

    /**
     * 需要重置密码的用户账号 ID。
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

    /**
     * 新密码，不记录到日志，不进行去空格或大小写转换。
     */
    @NotBlank(message = "新密码不能为空")
    @ToString.Exclude
    private String newPassword;
}
