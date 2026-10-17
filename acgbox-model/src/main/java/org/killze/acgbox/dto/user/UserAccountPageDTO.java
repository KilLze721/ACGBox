package org.killze.acgbox.dto.user;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

/**
 * 用户账号分页查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAccountPageDTO extends PageDTO {

    /**
     * 登录用户名，忽略字母大小写的模糊查询。
     */
    private String username;

    /**
     * 登录邮箱，忽略字母大小写的模糊查询。
     */
    private String email;

    /**
     * 账号角色：ADMIN、EDITOR、USER。
     */
    private String role;

    /**
     * 账号是否启用，不传时查询全部状态。
     */
    private Boolean enabled;
}
