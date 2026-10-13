package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端登录数据传输对象
 */
@Data
public class LoginDTO {

    /**
     * 登录用户名，使用用户名登录时填写
     */
    private String username;

    /**
     * 登录邮箱，使用邮箱登录时填写
     */
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 登录密码
     */
    @NotBlank(message = "登录密码不能为空")
    private String password;
}
