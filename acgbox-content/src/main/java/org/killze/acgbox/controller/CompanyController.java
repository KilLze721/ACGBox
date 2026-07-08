package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.CompanyDTO;
import org.killze.acgbox.dto.content.CompanyPageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.CompanyService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.CompanyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公司控制器
 *
 * @author killze
 */
@RestController
@RequestMapping("/companies")
@Slf4j
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    /**
     * 创建公司
     */
    @PostMapping("/create")
    public Result<CompanyVO> createCompany(@Valid @RequestBody CompanyDTO companyDTO) {
        log.info("创建公司：{}", companyDTO);
        return Result.success(companyService.createCompany(companyDTO));
    }

    /**
     * 修改公司
     */
    @PostMapping("/update")
    public Result<CompanyVO> updateCompany(@Valid @RequestBody CompanyDTO companyDTO) {
        log.info("修改公司：{}", companyDTO);
        return Result.success(companyService.updateCompany(companyDTO));
    }

    /**
     * 批量删除公司
     */
    @PostMapping("/delete")
    public Result<Void> deleteCompany(@RequestBody List<Long> ids) {
        log.info("删除公司：{}", ids);
        companyService.deleteCompanies(ids);
        return Result.success();
    }

    /**
     * 根据 ID 获取公司
     */
    @GetMapping("/{id}")
    public Result<CompanyVO> getCompanyById(@PathVariable Long id) {
        log.info("根据 ID 获取公司：{}", id);
        return Result.success(companyService.getCompanyById(id));
    }

    /**
     * 分页查询公司
     */
    @GetMapping("/page")
    public Result<PageVO<CompanyVO>> pageCompany(CompanyPageDTO pageDTO) {
        log.info("分页查询公司：{}", pageDTO);
        return Result.success(companyService.pageCompany(pageDTO));
    }
}
