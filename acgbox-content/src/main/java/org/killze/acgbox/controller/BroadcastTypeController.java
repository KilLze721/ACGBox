package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.BroadcastTypeDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.BroadcastTypeService;
import org.killze.acgbox.vo.content.BroadcastTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 放送类型控制器
 *
 * @author killze
 */
@RestController
@RequestMapping("/broadcast-type")
@Slf4j
public class BroadcastTypeController {

    @Autowired
    private BroadcastTypeService broadcastTypeService;

    /**
     * 创建放送类型
     */
    @PostMapping("/create")
    public Result<BroadcastTypeVO> create(@Valid @RequestBody BroadcastTypeDTO dto) {
        log.info("创建放送类型：{}", dto);
        return Result.success(broadcastTypeService.createBroadcastType(dto));
    }

    /**
     * 修改放送类型
     */
    @PostMapping("/update")
    public Result<BroadcastTypeVO> update(@Valid @RequestBody BroadcastTypeDTO dto) {
        log.info("修改放送类型：{}", dto);
        return Result.success(broadcastTypeService.updateBroadcastType(dto));
    }

    /**
     * 批量删除放送类型
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody List<Integer> ids) {
        log.info("删除放送类型：{}", ids);
        broadcastTypeService.deleteBroadcastTypes(ids);
        return Result.success();
    }

    /**
     * 根据ID获取放送类型
     */
    @GetMapping("/{id}")
    public Result<BroadcastTypeVO> getById(@PathVariable Integer id) {
        log.info("根据id获取放送类型：{}", id);
        return Result.success(broadcastTypeService.getBroadcastTypeById(id));
    }

    /**
     * 获取全部放送类型
     */
    @GetMapping("/list")
    public Result<List<BroadcastTypeVO>> list() {
        log.info("获取全部放送类型");
        return Result.success(broadcastTypeService.listBroadcastTypes());
    }
}