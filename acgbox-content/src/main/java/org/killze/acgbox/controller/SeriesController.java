package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.SeriesDTO;
import org.killze.acgbox.dto.content.SeriesPageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.SeriesService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.SeriesVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系列控制器。
 *
 * @author killze
 */
@RestController
@RequestMapping("/series")
@Slf4j
public class SeriesController {

    @Autowired
    private SeriesService seriesService;

    /**
     * 创建系列
     */
    @PostMapping("/create")
    public Result<SeriesVO> createSeries(@Valid @RequestBody SeriesDTO seriesDTO) {
        log.info("创建系列：{}", seriesDTO);
        return Result.success(seriesService.createSeries(seriesDTO));
    }

    /**
     * 修改系列
     */
    @PostMapping("/update")
    public Result<SeriesVO> updateSeries(@Valid @RequestBody SeriesDTO seriesDTO) {
        log.info("修改系列：{}", seriesDTO);
        return Result.success(seriesService.updateSeries(seriesDTO));
    }

    /**
     * 批量删除系列
     */
    @PostMapping("/delete")
    public Result<Void> deleteSeries(@RequestBody List<Long> ids) {
        log.info("删除系列：{}", ids);
        seriesService.deleteSeries(ids);
        return Result.success();
    }

    /**
     * 根据 id 获取系列
     */
    @GetMapping("/{id}")
    public Result<SeriesVO> getSeriesById(@PathVariable Long id) {
        log.info("根据 id 获取系列：{}", id);
        return Result.success(seriesService.getSeriesById(id));
    }

    /**
     * 分页查询系列
     */
    @GetMapping("/page")
    public Result<PageVO<SeriesVO>> pageSeries(SeriesPageDTO pageDTO) {
        log.info("分页查询系列：{}", pageDTO);
        return Result.success(seriesService.pageSeries(pageDTO));
    }
}
