package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 管理端邮箱登录验证码发送及登录数据传输对象。
 */
@Data
public class EmailCodeLoginDTO {

    /**
     * 已绑定的登录邮箱，保持字母大小写。
     */
    @NotBlank(message = "登录邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过 254 个字符")
    private String email;

    /**
     * 登录时填写六位数字验证码，发送验证码时可以不传，必填校验由登录服务执行。
     */
    @Pattern(regexp = "[0-9]{6}", message = "验证码必须为 6 位数字")
    @ToString.Exclude
    private String verificationCode;
}
