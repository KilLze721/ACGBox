package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;

/**
 * 当前登录账号解绑邮箱数据传输对象。
 */
@Data
public class UserAccountUnbindEmailDTO {

    /**
     * 原邮箱收到的 UNBIND 验证码。
     */
    @NotBlank(message = "邮箱验证码不能为空")
    @Pattern(regexp = "[0-9]{6}", message = "验证码必须为 6 位数字")
    @ToString.Exclude
    private String verificationCode;
}
