package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.UserAccountService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户账号服务实现类。
 */
@Service
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired
    private UserAccountMapper userAccountMapper;

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
