package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 当前登录账号绑定或换绑邮箱数据传输对象。
 */
@Data
public class UserAccountEmailDTO {

    /**
     * 待绑定邮箱，保持字母大小写。
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过 254 个字符")
    private String email;

    /**
     * 新邮箱收到的 BIND 验证码。
     */
    @NotBlank(message = "新邮箱验证码不能为空")
    @Pattern(regexp = "[0-9]{6}", message = "验证码必须为 6 位数字")
    @ToString.Exclude
    private String newEmailCode;

    /**
     * 换绑时验证原邮箱后获得的短期凭证，首次绑定或验证原邮箱时不需要。
     */
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "邮箱换绑凭证格式不正确")
    @ToString.Exclude
    private String emailChangeToken;
}
