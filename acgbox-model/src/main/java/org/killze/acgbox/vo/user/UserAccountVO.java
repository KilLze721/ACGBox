package org.killze.acgbox.vo.user;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号视图对象，不包含密码哈希。
 */
@Data
@Builder
public class UserAccountVO {

    /**
     * 用户账号 ID。
     */
    private Long id;

    /**
     * 登录用户名。
     */
    private String username;

    /**
     * 登录邮箱，未设置时为空。
     */
    private String email;

    /**
     * 账号角色：ADMIN、EDITOR、USER。
     */
    private String role;

    /**
     * 账号是否启用。
     */
    private Boolean enabled;

    /**
     * 邮箱验证完成时间，未验证时为空。
     */
    private LocalDateTime emailVerifiedAt;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间。
     */
    private LocalDateTime updatedAt;
}
