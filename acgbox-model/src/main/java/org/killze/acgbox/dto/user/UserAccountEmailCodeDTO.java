package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 当前登录账号发送邮箱验证码数据传输对象。
 */
@Data
public class UserAccountEmailCodeDTO {

    /**
     * 验证用途：BIND 验证新邮箱，CHANGE 确认原邮箱换绑，UNBIND 确认解绑。
     */
    @NotBlank(message = "验证码用途不能为空")
    @Pattern(regexp = "BIND|CHANGE|UNBIND", message = "验证码用途仅支持 BIND、CHANGE、UNBIND")
    private String purpose;

    /**
     * BIND 时填写待绑定邮箱，其他用途从当前账号读取原邮箱。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过 254 个字符")
    private String email;

    /**
     * 换绑时验证原邮箱后获得的短期凭证，向新邮箱发送 BIND 验证码时使用。
     */
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "邮箱换绑凭证格式不正确")
    @ToString.Exclude
    private String emailChangeToken;
}
