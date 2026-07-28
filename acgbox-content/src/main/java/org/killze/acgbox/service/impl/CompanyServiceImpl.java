package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.content.CompanyDTO;
import org.killze.acgbox.dto.content.CompanyPageDTO;
import org.killze.acgbox.entity.content.Company;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.CompanyMapper;
import org.killze.acgbox.service.CompanyService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.CompanyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公司服务实现类
 *
 * @author killze
 */
@Service
public class CompanyServiceImpl implements CompanyService {

    @Autowired
    private CompanyMapper companyMapper;

    /**
     * 创建公司
     *
     * @param companyDTO 公司信息
     * @return 公司信息
     */
    @Override
    public CompanyVO createCompany(CompanyDTO companyDTO) {
        // 判断此公司是否存在
        Company exist = companyMapper.selectOne(
                new LambdaQueryWrapper<Company>()
                        .eq(Company::getName, companyDTO.getName())
        );
        if (exist != null) {
            throw new BusinessException("此公司已存在");
        }
        // 创建公司
        Company company = Company.builder()
                .name(companyDTO.getName())
                .description(companyDTO.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        companyMapper.insert(company);
        // 返回公司信息
        return buildCompanyVO(company);
    }

    /**
     * 修改公司
     *
     * @param companyDTO 公司信息
     * @return 公司信息
     */
    @Override
    public CompanyVO updateCompany(CompanyDTO companyDTO) {
        // 判断公司是否存在
        Company company = companyMapper.selectById(companyDTO.getId());
        if (company == null) {
            throw new BusinessException("此公司不存在");
        }
        // 判断新名称是否被其他公司使用
        Company exist = companyMapper.selectOne(
                new LambdaQueryWrapper<Company>()
                        .eq(Company::getName, companyDTO.getName())
                        .ne(Company::getId, companyDTO.getId())
        );
        if (exist != null) {
            throw new BusinessException("此公司已存在");
        }
        // 修改公司
        company.setName(companyDTO.getName());
        company.setDescription(companyDTO.getDescription());
        company.setUpdatedAt(LocalDateTime.now());
        companyMapper.updateById(company);
        // 返回公司信息
        return buildCompanyVO(company);
    }

    /**
     * 批量删除公司
     *
     * @param ids 公司 ID 列表
     */
    @Override
    public void deleteCompanies(List<Long> ids) {
        // 判断 ids 是否为空
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的公司");
        }
        // TODO 判断公司是否被作品引用

        // 删除公司
        companyMapper.deleteByIds(ids);
    }

    /**
     * 根据 ID 获取公司
     *
     * @param id 公司 ID
     * @return 公司信息
     */
    @Override
    public CompanyVO getCompanyById(Long id) {
        // 判断公司是否存在
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException("此公司不存在");
        }
        // 返回公司信息
        return buildCompanyVO(company);
    }

    /**
     * 分页查询公司
     *
     * @param pageDTO 分页参数
     * @return 公司列表
     */
    @Override
    public PageVO<CompanyVO> pageCompany(CompanyPageDTO pageDTO) {
        // 创建分页对象
        Page<Company> page = new Page<>(pageDTO.getPageNum(), pageDTO.getPageSize());
        // 模糊名称查询
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(
                // 判断名称是否为空
                StringUtils.hasText(pageDTO.getName()),
                Company::getName,
                pageDTO.getName()
        );
        wrapper.orderByDesc(Company::getUpdatedAt);
        // 分页查询
        Page<Company> result = companyMapper.selectPage(page, wrapper);
        // 返回结果
        List<CompanyVO> rows = result.getRecords().stream().map(this::buildCompanyVO).toList();
        return PageVO.<CompanyVO>builder()
                .pageNum(result.getCurrent())
                .pageSize(result.getSize())
                .total(result.getTotal())
                .pages(result.getPages())
                .rows(rows)
                .build();
    }

    private CompanyVO buildCompanyVO(Company company) {
        return CompanyVO.builder()
                .id(company.getId())
                .name(company.getName())
                .description(company.getDescription())
                .build();
    }
}
