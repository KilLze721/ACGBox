package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.dto.user.UserAccountDTO;
import org.killze.acgbox.dto.user.UserAccountPasswordDTO;
import org.killze.acgbox.dto.user.UserAccountCreateDTO;
import org.killze.acgbox.dto.user.UserAccountDeleteDTO;
import org.killze.acgbox.dto.user.UserAccountChangePasswordDTO;
import org.killze.acgbox.dto.user.UserAccountEmailCodeDTO;
import org.killze.acgbox.dto.user.UserAccountEmailDTO;
import org.killze.acgbox.dto.user.UserAccountUnbindEmailDTO;
import org.killze.acgbox.dto.user.UserAccountVerifyEmailDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.UserAccountService;
import org.killze.acgbox.service.EmailVerificationService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.killze.acgbox.vo.user.UserAccountEmailChangeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 用户账号服务实现类。
 */
@Service
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    /**
     * 创建编辑或普通用户账号，初始令牌版本固定为零。
     *
     * @param userAccountCreateDTO 用户账号创建信息
     * @return 用户账号信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccountVO createUserAccount(UserAccountCreateDTO userAccountCreateDTO) {
        String role = userAccountCreateDTO.getRole();
        if (!"EDITOR".equals(role) && !"USER".equals(role)) {
            throw new BusinessException("创建账号仅支持 EDITOR、USER 角色");
        }
        String username = userAccountCreateDTO.getUsername().trim();
        String email = StringUtils.hasText(userAccountCreateDTO.getEmail()) ? userAccountCreateDTO.getEmail().trim() : null;
        String password = userAccountCreateDTO.getPassword();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("密码不能超过 72 个 UTF-8 字节");
        }
        if (userAccountMapper.exists(new LambdaQueryWrapper<UserAccount>()
                .apply("lower(username) = lower({0})", username))) {
            throw new BusinessException("此登录用户名已存在");
        }
        if (email != null && userAccountMapper.exists(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, email))) {
            throw new BusinessException("此邮箱已被使用");
        }

        LocalDateTime now = LocalDateTime.now();
        UserAccount userAccount = UserAccount.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .enabled(userAccountCreateDTO.getEnabled())
                .tokenVersion(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            userAccountMapper.insert(userAccount);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("登录用户名或邮箱已被使用");
        }
        return buildUserAccountVO(userAccount);
    }

    /**
     * 分页查询用户账号。
     *
     * @param pageDTO 分页查询参数
     * @return 用户账号分页结果
     */
    @Override
    public PageVO<UserAccountVO> pageUserAccount(UserAccountPageDTO pageDTO) {
        if (pageDTO.getPageNum() == null) {
            pageDTO.setPageNum(1L);
        }
        if (pageDTO.getPageSize() == null) {
            pageDTO.setPageSize(20L);
        }
        if (pageDTO.getPageNum() < 1L) {
            throw new BusinessException("pageNum 必须大于等于 1");
        }
        if (pageDTO.getPageSize() < 1L || pageDTO.getPageSize() > 100L) {
            throw new BusinessException("pageSize 必须处于 1 到 100 之间");
        }

        Page<UserAccount> page = new Page<>(pageDTO.getPageNum(), pageDTO.getPageSize());
        LambdaQueryWrapper<UserAccount> wrapper = buildUserAccountQuery();
        wrapper.apply(
                StringUtils.hasText(pageDTO.getUsername()),
                "username ILIKE '%' || {0} || '%'",
                pageDTO.getUsername()
        );
        wrapper.apply(
                StringUtils.hasText(pageDTO.getEmail()),
                "email ILIKE '%' || {0} || '%'",
                pageDTO.getEmail()
        );
        wrapper.eq(StringUtils.hasText(pageDTO.getRole()), UserAccount::getRole, pageDTO.getRole());
        wrapper.eq(pageDTO.getEnabled() != null, UserAccount::getEnabled, pageDTO.getEnabled());
        wrapper.orderByDesc(UserAccount::getUpdatedAt, UserAccount::getId);

        Page<UserAccount> result = userAccountMapper.selectPage(page, wrapper);
        List<UserAccountVO> rows = result.getRecords().stream().map(this::buildUserAccountVO).toList();
        return PageVO.<UserAccountVO>builder()
                .pageNum(result.getCurrent())
                .pageSize(result.getSize())
                .total(result.getTotal())
                .pages(result.getPages())
                .rows(rows)
                .build();
    }

    /**
     * 根据 ID 查询用户账号。
     *
     * @param id 用户账号 ID
     * @return 用户账号信息
     */
    @Override
    public UserAccountVO getUserAccountById(Long id) {
        if (id == null || id < 1L) {
            throw new BusinessException("用户账号 ID 必须大于等于 1");
        }
        UserAccount userAccount = userAccountMapper.selectOne(
                buildUserAccountQuery().eq(UserAccount::getId, id)
        );
        if (userAccount == null) {
            throw new BusinessException("此用户账号不存在");
        }
        return buildUserAccountVO(userAccount);
    }

    /**
     * 修改用户账号，实际变更时递增令牌版本。
     *
     * @param userAccountDTO 用户账号信息
     * @param operatorId 当前登录的管理员 ID
     * @return 修改后的用户账号信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccountVO updateUserAccount(UserAccountDTO userAccountDTO, Long operatorId) {
        UserAccount userAccount = getUserAccount(userAccountDTO.getId());
        String username = userAccountDTO.getUsername().trim();
        String email = StringUtils.hasText(userAccountDTO.getEmail()) ? userAccountDTO.getEmail().trim() : null;
        String role = userAccountDTO.getRole();
        Boolean enabled = userAccountDTO.getEnabled();

        // 管理员只能保留原角色并保持启用，其他账号不能升级为管理员。
        if ("ADMIN".equals(userAccount.getRole())) {
            if (!"ADMIN".equals(role) || !Boolean.TRUE.equals(enabled)) {
                throw new BusinessException("管理员不能降级或禁用");
            }
        } else if ("ADMIN".equals(role)) {
            throw new BusinessException("不允许将其他账号修改为管理员");
        }

        boolean usernameChanged = !Objects.equals(userAccount.getUsername(), username);
        boolean emailChanged = !Objects.equals(userAccount.getEmail(), email);
        if (!usernameChanged && !emailChanged
                && Objects.equals(userAccount.getRole(), role)
                && Objects.equals(userAccount.getEnabled(), enabled)) {
            return buildUserAccountVO(userAccount);
        }
        if (emailChanged) {
            verifyAdminPassword(operatorId, userAccountDTO.getCurrentPassword());
        }
        if (usernameChanged && userAccountMapper.exists(new LambdaQueryWrapper<UserAccount>()
                .ne(UserAccount::getId, userAccount.getId())
                .apply("lower(username) = lower({0})", username))) {
            throw new BusinessException("此登录用户名已存在");
        }
        if (emailChanged && email != null && userAccountMapper.exists(new LambdaQueryWrapper<UserAccount>()
                .ne(UserAccount::getId, userAccount.getId())
                .eq(UserAccount::getEmail, email))) {
            throw new BusinessException("此邮箱已被使用");
        }

        LambdaUpdateWrapper<UserAccount> wrapper = new LambdaUpdateWrapper<UserAccount>()
                .set(UserAccount::getUsername, username)
                .set(UserAccount::getEmail, email)
                .set(UserAccount::getRole, role)
                .set(UserAccount::getEnabled, enabled)
                .set(emailChanged, UserAccount::getEmailVerifiedAt, null);
        updateAccountWithVersion(userAccount, wrapper);

        userAccount.setUsername(username);
        userAccount.setEmail(email);
        userAccount.setRole(role);
        userAccount.setEnabled(enabled);
        if (emailChanged) {
            userAccount.setEmailVerifiedAt(null);
        }
        return buildUserAccountVO(userAccount);
    }

    /**
     * 验证管理员当前密码后重置目标账号密码。
     *
     * @param passwordDTO 密码重置信息
     * @param operatorId 当前登录的管理员 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetUserAccountPassword(UserAccountPasswordDTO passwordDTO, Long operatorId) {
        UserAccount userAccount = getUserAccount(passwordDTO.getId());
        verifyAdminPassword(operatorId, passwordDTO.getCurrentPassword());
        String newPassword = passwordDTO.getNewPassword();
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("新密码不能超过 72 个 UTF-8 字节");
        }
        if (passwordEncoder.matches(newPassword, userAccount.getPasswordHash())) {
            return;
        }
        updateAccountWithVersion(userAccount, new LambdaUpdateWrapper<UserAccount>()
                .set(UserAccount::getPasswordHash, passwordEncoder.encode(newPassword)));
    }

    /**
     * 验证管理员密码后物理删除单个账号，不删除管理员或并发修改后的账号。
     *
     * @param userAccountDeleteDTO 用户账号删除信息
     * @param operatorId 当前登录的管理员 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserAccount(UserAccountDeleteDTO userAccountDeleteDTO, Long operatorId) {
        UserAccount userAccount = getUserAccount(userAccountDeleteDTO.getId());
        if ("ADMIN".equals(userAccount.getRole())) {
            throw new BusinessException("不允许删除管理员账号");
        }
        verifyAdminPassword(operatorId, userAccountDeleteDTO.getCurrentPassword());
        // 删除时再次限制角色和版本，避免在查询与删除之间发生的账号变更被忽略。
        int deleted = userAccountMapper.delete(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getId, userAccount.getId())
                .ne(UserAccount::getRole, "ADMIN")
                .eq(UserAccount::getTokenVersion, userAccount.getTokenVersion()));
        if (deleted != 1) {
            throw new BusinessException("账号信息已发生变化，请刷新后重试");
        }
    }

    /**
     * 校验当前密码后修改自己的密码，实际变更时递增令牌版本。
     *
     * @param passwordDTO 密码修改信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeCurrentUserAccountPassword(UserAccountChangePasswordDTO passwordDTO, Long id, Long tokenVersion) {
        UserAccount userAccount = verifyCurrentUserAccount(id, tokenVersion, passwordDTO.getCurrentPassword());
        String newPassword = passwordDTO.getNewPassword();
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("新密码不能超过 72 个 UTF-8 字节");
        }
        if (passwordEncoder.matches(newPassword, userAccount.getPasswordHash())) {
            return;
        }
        updateAccountWithVersion(userAccount, new LambdaUpdateWrapper<UserAccount>()
                .set(UserAccount::getPasswordHash, passwordEncoder.encode(newPassword)));
    }

    /**
     * 发送邮箱验证码，原邮箱始终从当前账号读取，换绑的新邮箱须先通过原邮箱验证。
     *
     * @param emailCodeDTO 验证码发送信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     */
    @Override
    public void sendCurrentUserAccountEmailCode(UserAccountEmailCodeDTO emailCodeDTO, Long id, Long tokenVersion) {
        UserAccount userAccount = verifyCurrentUserAccount(id, tokenVersion);
        String purpose = emailCodeDTO.getPurpose();
        String email;
        if ("BIND".equals(purpose)) {
            email = checkBindingEmail(userAccount, emailCodeDTO.getEmail());
            if (userAccount.getEmail() != null && !Objects.equals(userAccount.getEmail(), email)) {
                emailVerificationService.verifyEmailChangeToken(id, tokenVersion,
                        userAccount.getEmail(), emailCodeDTO.getEmailChangeToken(), false);
            }
        } else if ("CHANGE".equals(purpose) || "UNBIND".equals(purpose)) {
            email = userAccount.getEmail();
            if (email == null) {
                throw new BusinessException("当前账号尚未绑定邮箱");
            }
        } else {
            throw new BusinessException("验证码用途仅支持 BIND、CHANGE、UNBIND");
        }
        emailVerificationService.sendCode(id, tokenVersion, purpose, email);
    }

    /**
     * 验证原邮箱后签发短期换绑凭证，保留原邮箱直至新邮箱验证成功。
     *
     * @param emailDTO 原邮箱验证信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @return 邮箱换绑凭证
     */
    @Override
    public UserAccountEmailChangeVO verifyCurrentUserAccountEmail(UserAccountVerifyEmailDTO emailDTO,
                                                                Long id, Long tokenVersion) {
        UserAccount userAccount = verifyCurrentUserAccount(id, tokenVersion);
        if (userAccount.getEmail() == null) {
            throw new BusinessException("当前账号尚未绑定邮箱");
        }
        emailVerificationService.verifyAndConsume(id, tokenVersion, "CHANGE",
                userAccount.getEmail(), emailDTO.getCurrentEmailCode());
        String emailChangeToken = emailVerificationService.createEmailChangeToken(id, tokenVersion, userAccount.getEmail());
        return UserAccountEmailChangeVO.builder().emailChangeToken(emailChangeToken).expiresIn(300L).build();
    }

    /**
     * 首次绑定只验证新邮箱，换绑时先校验原邮箱凭证，再验证新邮箱并消费凭证。
     *
     * @param emailDTO 邮箱绑定信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindCurrentUserAccountEmail(UserAccountEmailDTO emailDTO, Long id, Long tokenVersion) {
        UserAccount userAccount = verifyCurrentUserAccount(id, tokenVersion);
        String email = checkBindingEmail(userAccount, emailDTO.getEmail());
        boolean emailChanged = !Objects.equals(userAccount.getEmail(), email);
        if (emailChanged && userAccount.getEmail() != null) {
            emailVerificationService.verifyEmailChangeToken(id, tokenVersion,
                    userAccount.getEmail(), emailDTO.getEmailChangeToken(), false);
        }
        emailVerificationService.verifyAndConsume(id, tokenVersion, "BIND", email, emailDTO.getNewEmailCode());
        if (emailChanged && userAccount.getEmail() != null) {
            emailVerificationService.verifyEmailChangeToken(id, tokenVersion,
                    userAccount.getEmail(), emailDTO.getEmailChangeToken(), true);
        }
        updateAccountWithVersion(userAccount, new LambdaUpdateWrapper<UserAccount>()
                .set(UserAccount::getEmail, email)
                .set(UserAccount::getEmailVerifiedAt, LocalDateTime.now()));
    }

    /**
     * 仅验证原邮箱验证码后解绑邮箱，不影响用户名密码登录。
     *
     * @param emailDTO 邮箱解绑信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindCurrentUserAccountEmail(UserAccountUnbindEmailDTO emailDTO, Long id, Long tokenVersion) {
        UserAccount userAccount = verifyCurrentUserAccount(id, tokenVersion);
        if (userAccount.getEmail() == null) {
            throw new BusinessException("当前账号尚未绑定邮箱");
        }
        emailVerificationService.verifyAndConsume(id, tokenVersion, "UNBIND",
                userAccount.getEmail(), emailDTO.getVerificationCode());
        updateAccountWithVersion(userAccount, new LambdaUpdateWrapper<UserAccount>()
                .set(UserAccount::getEmail, null)
                .set(UserAccount::getEmailVerifiedAt, null));
    }

    /**
     * 再次校验自己的账号状态、令牌版本及当前密码，避免使用认证后已失效的账号信息。
     *
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @param currentPassword 当前密码
     * @return 当前登录账号
     */
    private UserAccount verifyCurrentUserAccount(Long id, Long tokenVersion, String currentPassword) {
        UserAccount userAccount = verifyCurrentUserAccount(id, tokenVersion);
        if (!StringUtils.hasText(currentPassword)
                || currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(currentPassword, userAccount.getPasswordHash())) {
            throw new BusinessException("当前密码错误");
        }
        return userAccount;
    }

    /**
     * 校验当前账号状态和令牌版本，邮箱操作不额外要求密码。
     */
    private UserAccount verifyCurrentUserAccount(Long id, Long tokenVersion) {
        UserAccount userAccount = getUserAccount(id);
        if (!Boolean.TRUE.equals(userAccount.getEnabled())
                || !Objects.equals(userAccount.getTokenVersion(), tokenVersion)
                || (!"ADMIN".equals(userAccount.getRole()) && !"EDITOR".equals(userAccount.getRole()))) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        return userAccount;
    }

    /**
     * 校验待绑定邮箱，沿用区分大小写的邮箱唯一性规则。
     *
     * @param userAccount 当前登录账号
     * @param email 待绑定邮箱
     * @return 去除首尾空格后的邮箱
     */
    private String checkBindingEmail(UserAccount userAccount, String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        email = email.trim();
        if (Objects.equals(userAccount.getEmail(), email) && userAccount.getEmailVerifiedAt() != null) {
            throw new BusinessException("此邮箱已绑定并验证");
        }
        if (userAccountMapper.exists(new LambdaQueryWrapper<UserAccount>()
                .ne(UserAccount::getId, userAccount.getId())
                .eq(UserAccount::getEmail, email))) {
            throw new BusinessException("此邮箱已被使用");
        }
        return email;
    }

    /**
     * 查询需要修改的账号，包含版本校验和密码重置需要的字段。
     *
     * @param id 用户账号 ID
     * @return 用户账号
     */
    private UserAccount getUserAccount(Long id) {
        if (id == null || id < 1L) {
            throw new BusinessException("用户账号 ID 必须大于等于 1");
        }
        UserAccount userAccount = userAccountMapper.selectById(id);
        if (userAccount == null) {
            throw new BusinessException("此用户账号不存在");
        }
        return userAccount;
    }

    /**
     * 敏感操作重新验证操作者自己的管理员密码，不验证目标账号原密码。
     *
     * @param operatorId 当前登录的管理员 ID
     * @param currentPassword 管理员当前密码
     */
    private void verifyAdminPassword(Long operatorId, String currentPassword) {
        if (!StringUtils.hasText(currentPassword)) {
            throw new BusinessException("管理员密码不能为空");
        }
        UserAccount operator = userAccountMapper.selectById(operatorId);
        if (operator == null || !Boolean.TRUE.equals(operator.getEnabled()) || !"ADMIN".equals(operator.getRole())) {
            throw new BusinessException("管理员身份已失效，请重新登录");
        }
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(currentPassword, operator.getPasswordHash())) {
            throw new BusinessException("管理员密码错误");
        }
    }

    /**
     * 在同一条更新语句中保存修改并递增版本，版本冲突时要求刷新重试。
     *
     * @param userAccount 修改前的用户账号
     * @param wrapper 需要更新的账号字段
     */
    private void updateAccountWithVersion(UserAccount userAccount, LambdaUpdateWrapper<UserAccount> wrapper) {
        LocalDateTime now = LocalDateTime.now();
        wrapper.eq(UserAccount::getId, userAccount.getId())
                .eq(UserAccount::getTokenVersion, userAccount.getTokenVersion())
                .set(UserAccount::getUpdatedAt, now)
                .setSql("token_version = token_version + 1");
        try {
            if (userAccountMapper.update(null, wrapper) != 1) {
                throw new BusinessException("账号信息已发生变化，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("登录用户名或邮箱已被使用");
        }
        userAccount.setTokenVersion(userAccount.getTokenVersion() + 1L);
        userAccount.setUpdatedAt(now);
    }

    /**
     * 构建用户账号查询，仅选择允许展示的字段，避免读取密码哈希。
     *
     * @return 用户账号查询条件
     */
    private LambdaQueryWrapper<UserAccount> buildUserAccountQuery() {
        return new LambdaQueryWrapper<UserAccount>()
                .select(UserAccount::getId, UserAccount::getUsername, UserAccount::getEmail,
                        UserAccount::getRole, UserAccount::getEnabled, UserAccount::getEmailVerifiedAt,
                        UserAccount::getCreatedAt, UserAccount::getUpdatedAt);
    }

    /**
     * 构建用户账号视图对象。
     *
     * @param userAccount 用户账号
     * @return 用户账号信息
     */
    private UserAccountVO buildUserAccountVO(UserAccount userAccount) {
        return UserAccountVO.builder()
                .id(userAccount.getId())
                .username(userAccount.getUsername())
                .email(userAccount.getEmail())
                .role(userAccount.getRole())
                .enabled(userAccount.getEnabled())
                .emailVerifiedAt(userAccount.getEmailVerifiedAt())
                .createdAt(userAccount.getCreatedAt())
                .updatedAt(userAccount.getUpdatedAt())
                .build();
    }
}
