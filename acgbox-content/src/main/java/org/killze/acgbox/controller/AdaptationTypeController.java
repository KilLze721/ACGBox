package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.AdaptationTypeService;
import org.killze.acgbox.vo.content.AdaptationTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 改编类型控制器
 *
 * @author killze
 */
@RestController
@RequestMapping("/adaptation-type")
@Slf4j
public class AdaptationTypeController {

    @Autowired
    private AdaptationTypeService adaptationTypeService;

    /**
     * 创建改编类型
     */
    @PostMapping("/create")
    public Result<AdaptationTypeVO> create(@Valid @RequestBody AdaptationTypeDTO dto) {
        log.info("创建改编类型：{}", dto);
        return Result.success(adaptationTypeService.createAdaptationType(dto));
    }

    /**
     * 修改改编类型
     */
    @PostMapping("/update")
    public Result<AdaptationTypeVO> update(@Valid @RequestBody AdaptationTypeDTO dto) {
        log.info("修改改编类型：{}", dto);
        return Result.success(adaptationTypeService.updateAdaptationType(dto));
    }

    /**
     * 批量删除改编类型
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody List<Integer> ids) {
        log.info("删除改编类型：{}", ids);
        adaptationTypeService.deleteAdaptationTypes(ids);
        return Result.success();
    }

    /**
     * 根据id获取改编类型
     */
    @GetMapping("/{id}")
    public Result<AdaptationTypeVO> getById(@PathVariable Integer id) {
        log.info("根据id获取改编类型：{}", id);
        return Result.success(adaptationTypeService.getAdaptationTypeById(id));
    }

    /**
     * 获取全部改编类型
     */
    @GetMapping("/list")
    public Result<List<AdaptationTypeVO>> list() {
        log.info("获取全部改编类型");
        return Result.success(adaptationTypeService.listAdaptationTypes());
    }
}
