package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.killze.acgbox.dto.user.LoginDTO;
import org.killze.acgbox.dto.user.EmailCodeLoginDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.JwtService;
import org.killze.acgbox.service.LoginService;
import org.killze.acgbox.service.EmailVerificationService;
import org.killze.acgbox.vo.user.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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

    @Autowired
    private EmailVerificationService emailVerificationService;

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
        if (!"ADMIN".equals(userAccount.getRole())
                && !"EDITOR".equals(userAccount.getRole())) {
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

    /**
     * 仅向已验证邮箱且具有管理端权限的启用账号发送登录验证码。
     * 不符合条件时不发送邮件并正常返回，避免通过响应直接枚举账号。
     *
     * @param loginDTO 邮箱验证码发送信息
     */
    @Override
    public void sendLoginEmailCode(EmailCodeLoginDTO loginDTO) {
        UserAccount userAccount = findEmailCodeAccount(loginDTO.getEmail());
        if (userAccount == null) {
            return;
        }
        emailVerificationService.sendCode(userAccount.getId(), userAccount.getTokenVersion(),
                "LOGIN", userAccount.getEmail());
    }

    /**
     * 按数据库中的当前账号版本校验并消费登录验证码，成功后复用登录响应。
     * 登录不修改账号资料或版本，其他用途的验证码不能用于登录。
     *
     * @param loginDTO 邮箱验证码登录信息
     * @return 登录信息
     */
    @Override
    public LoginVO loginByEmailCode(EmailCodeLoginDTO loginDTO) {
        if (!StringUtils.hasText(loginDTO.getVerificationCode())) {
            throw new BusinessException("登录验证码不能为空");
        }
        UserAccount userAccount = findEmailCodeAccount(loginDTO.getEmail());
        if (userAccount == null) {
            throw new BusinessException("验证码错误或已过期，请重新获取");
        }
        emailVerificationService.verifyAndConsume(userAccount.getId(), userAccount.getTokenVersion(),
                "LOGIN", userAccount.getEmail(), loginDTO.getVerificationCode());
        return createLoginVO(userAccount);
    }

    /**
     * 沿用区分大小写的邮箱查询，只读取验证码登录需要的字段，不读取密码哈希。
     *
     * @param email 登录邮箱
     * @return 符合验证码登录条件的账号，不符合时返回空
     */
    private UserAccount findEmailCodeAccount(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("登录邮箱不能为空");
        }
        UserAccount userAccount = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .select(UserAccount::getId, UserAccount::getUsername, UserAccount::getEmail,
                        UserAccount::getRole, UserAccount::getEnabled,
                        UserAccount::getEmailVerifiedAt, UserAccount::getTokenVersion)
                .eq(UserAccount::getEmail, email.trim()));
        if (userAccount == null || !Boolean.TRUE.equals(userAccount.getEnabled())
                || userAccount.getEmailVerifiedAt() == null
                || (!"ADMIN".equals(userAccount.getRole()) && !"EDITOR".equals(userAccount.getRole()))) {
            return null;
        }
        return userAccount;
    }

    /**
     * 退出管理端登录，使账号当前版本的全部令牌失效。
     *
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     */
    @Override
    public void logout(Long id, Long tokenVersion) {
        // 只递增当前令牌对应的版本，已失效的并发退出请求不再修改账号。
        userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, id)
                .eq(UserAccount::getTokenVersion, tokenVersion)
                .set(UserAccount::getUpdatedAt, LocalDateTime.now())
                .setSql("token_version = token_version + 1")
        );
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
