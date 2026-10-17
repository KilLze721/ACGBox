package org.killze.acgbox.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 用户账号修改数据传输对象。
 */
@Data
public class UserAccountDTO {

    /**
     * 用户账号 ID。
     */
    @NotNull(message = "用户账号 ID 不能为空")
    @Min(value = 1, message = "用户账号 ID 必须大于等于 1")
    private Long id;

    /**
     * 登录用户名，保留大小写，不允许使用邮箱格式。
     */
    @NotBlank(message = "登录用户名不能为空")
    @Size(max = 50, message = "登录用户名不能超过 50 个字符")
    @Pattern(regexp = "[^@]+", message = "登录用户名不能包含 @")
    private String username;

    /**
     * 登录邮箱，传空值时清空，不转换大小写。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱不能超过 254 个字符")
    private String email;

    /**
     * 账号角色，已有管理员只能保留 ADMIN，其他账号不能改为 ADMIN。
     */
    @NotBlank(message = "账号角色不能为空")
    @Pattern(regexp = "ADMIN|EDITOR|USER", message = "账号角色仅支持 ADMIN、EDITOR、USER")
    private String role;

    /**
     * 账号是否启用，管理员必须保持启用。
     */
    @NotNull(message = "账号启用状态不能为空")
    private Boolean enabled;

    /**
     * 执行操作的管理员当前密码，仅在邮箱实际变化时必填，不记录到日志。
     */
    @ToString.Exclude
    private String currentPassword;
}
