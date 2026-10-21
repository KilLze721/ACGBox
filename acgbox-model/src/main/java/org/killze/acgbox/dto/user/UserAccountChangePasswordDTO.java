package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/**
 * 当前登录账号修改密码数据传输对象。
 */
@Data
public class UserAccountChangePasswordDTO {

    /**
     * 当前登录账号的原密码。
     */
    @NotBlank(message = "当前密码不能为空")
    @ToString.Exclude
    private String currentPassword;

    /**
     * 新密码，不进行去空格或大小写转换。
     */
    @NotBlank(message = "新密码不能为空")
    @ToString.Exclude
    private String newPassword;
}
