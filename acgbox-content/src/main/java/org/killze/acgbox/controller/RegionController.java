package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.RegionDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.RegionService;
import org.killze.acgbox.vo.content.RegionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地区控制器
 *
 * @author killze
 */
@RestController
@RequestMapping("/region")
@Slf4j
public class RegionController {

    @Autowired
    private RegionService regionService;

    /**
     * 创建地区
     */
    @PostMapping("/create")
    public Result<RegionVO> createRegion(@Valid @RequestBody RegionDTO regionDTO) {
        log.info("创建地区：{}", regionDTO);
        return Result.success(regionService.createRegion(regionDTO));
    }

    /**
     * 修改地区
     */
    @PostMapping("/update")
    public Result<RegionVO> updateRegion(@Valid @RequestBody RegionDTO regionDTO) {
        log.info("修改地区：{}", regionDTO);
        return Result.success(regionService.updateRegion(regionDTO));
    }

    /**
     * 批量删除地区
     */
    @PostMapping("/delete")
    public Result<Void> deleteRegion(@RequestBody List<Long> ids) {
        log.info("删除地区：{}", ids);
        regionService.deleteRegions(ids);
        return Result.success();
    }

    /**
     * 根据 ID 获取地区
     */
    @GetMapping("/{id}")
    public Result<RegionVO> getRegionById(@PathVariable Long id) {
        log.info("根据 ID 获取地区：{}", id);
        return Result.success(regionService.getRegionById(id));
    }

    /**
     * 查询地区
     */
    @GetMapping("/list")
    public Result<List<RegionVO>> listRegion() {
        log.info("获取全部地区");
        return Result.success(regionService.listRegions());
    }
}
