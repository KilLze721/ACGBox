package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.killze.acgbox.dto.user.LoginDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.JwtService;
import org.killze.acgbox.service.LoginService;
import org.killze.acgbox.vo.user.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端登录服务实现类。
 */
@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 校验用户名密码登录。
     *
     * @param loginDTO 登录信息
     * @return 校验通过的账号
     */
    @Override
    public UserAccount verifyByUsername(LoginDTO loginDTO) {
        if (!StringUtils.hasText(loginDTO.getUsername())) {
            throw new BusinessException("登录用户名不能为空");
        }

        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .apply("lower(username) = lower({0})", loginDTO.getUsername().trim())
        );
        return verifyAccount(userAccount, loginDTO.getPassword());
    }

    /**
     * 校验邮箱密码登录，邮箱区分大小写。
     *
     * @param loginDTO 登录信息
     * @return 校验通过的账号
     */
    @Override
    public UserAccount verifyByEmail(LoginDTO loginDTO) {
        if (!StringUtils.hasText(loginDTO.getEmail())) {
            throw new BusinessException("登录邮箱不能为空");
        }

        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getEmail, loginDTO.getEmail().trim())
        );
        return verifyAccount(userAccount, loginDTO.getPassword());
    }

    /**
     * 校验账号密码、启用状态和管理端登录权限。
     *
     * @param userAccount 查询到的账号
     * @param password 登录密码
     * @return 校验通过的账号，供后端生成登录令牌使用
     */
    private UserAccount verifyAccount(UserAccount userAccount, String password) {
        // 账号不存在或密码错误时使用相同提示，避免泄露账号是否存在。
        if (userAccount == null
                || !passwordEncoder.matches(password, userAccount.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        if (!Boolean.TRUE.equals(userAccount.getEnabled())) {
            throw new BusinessException("此账号已被禁用");
        }
        if (!"ADMIN".equals(userAccount.getRole())) {
            throw new BusinessException("此账号无管理端登录权限");
        }
        return userAccount;
    }

    /** 使用用户名和密码登录，返回登录信息。
     * @param loginDTO 登录信息
     * @return 登录信息
     */
    @Override
    public LoginVO loginByUsername(LoginDTO loginDTO) {
        return createLoginVO(verifyByUsername(loginDTO));
    }

    /** 使用邮箱和密码登录，返回登录信息。
     *
     * @param loginDTO 登录信息
     * @return 登录信息
     */
    @Override
    public LoginVO loginByEmail(LoginDTO loginDTO) {
        return createLoginVO(verifyByEmail(loginDTO));
    }

    /** 为校验通过的账号签发令牌并组装登录信息。
     *
     * @param userAccount 校验通过的账号
     * @return 登录信息
     */
    private LoginVO createLoginVO(UserAccount userAccount) {
        return LoginVO.builder()
                .token(jwtService.createToken(userAccount))
                .expiresIn(expiration)
                .id(userAccount.getId())
                .username(userAccount.getUsername())
                .role(userAccount.getRole())
                .build();
    }
}
