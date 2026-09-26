package org.killze.acgbox.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户账号实体，对应 user_account 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_account")
public class UserAccount {

    /**
     * 用户账号 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名。
     */
    private String username;

    /**
     * 登录邮箱。
     */
    private String email;

    /**
     * 密码哈希值。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 展示昵称。
     */
    private String nickname;

    /**
     * 账号角色：ADMIN、EDITOR、USER。
     */
    private String role;

    /**
     * 账号是否启用。
     */
    private Boolean enabled;

    /**
     * 邮箱验证完成时间。
     */
    @TableField("email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /**
     * 记录创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
