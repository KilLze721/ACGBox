package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 用户账号创建数据传输对象，不接受账号 ID 或令牌版本。
 */
@Data
public class UserAccountCreateDTO {

    /**
     * 登录用户名，保留大小写，不允许使用邮箱格式。
     */
    @NotBlank(message = "登录用户名不能为空")
    @Size(max = 50, message = "登录用户名不能超过 50 个字符")
    @Pattern(regexp = "[^@]+", message = "登录用户名不能包含 @")
    private String username;

    /**
     * 登录邮箱，可不填写，不转换大小写。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱不能超过 254 个字符")
    private String email;

    /**
     * 初始密码，不记录到日志，不进行去空格或大小写转换。
     */
    @NotBlank(message = "初始密码不能为空")
    @ToString.Exclude
    private String password;

    /**
     * 账号角色，仅允许创建编辑或普通用户，禁止创建管理员。
     */
    @NotBlank(message = "账号角色不能为空")
    @Pattern(regexp = "EDITOR|USER", message = "创建账号仅支持 EDITOR、USER 角色")
    private String role;

    /**
     * 账号是否启用。
     */
    @NotNull(message = "账号启用状态不能为空")
    private Boolean enabled;
}
