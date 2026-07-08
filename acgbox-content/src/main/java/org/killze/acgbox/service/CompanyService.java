package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.CompanyDTO;
import org.killze.acgbox.dto.content.CompanyPageDTO;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.CompanyVO;

import java.util.List;

/**
 * 公司服务接口
 *
 * @author killze
 */
public interface CompanyService {

    /**
     * 创建公司
     *
     * @param companyDTO 公司信息
     * @return 公司信息
     */
    CompanyVO createCompany(@Valid CompanyDTO companyDTO);

    /**
     * 修改公司
     *
     * @param companyDTO 公司信息
     * @return 公司信息
     */
    CompanyVO updateCompany(@Valid CompanyDTO companyDTO);

    /**
     * 批量删除公司
     *
     * @param ids 公司 ID 列表
     */
    void deleteCompanies(List<Long> ids);

    /**
     * 根据 ID 查询公司
     *
     * @param id 公司 ID
     * @return 公司信息
     */
    CompanyVO getCompanyById(Long id);

    /**
     * 分页查询公司
     *
     * @param pageDTO 分页参数
     * @return 公司列表
     */
    PageVO<CompanyVO> pageCompany(CompanyPageDTO pageDTO);
}
