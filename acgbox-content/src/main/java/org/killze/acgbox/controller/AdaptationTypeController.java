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

    @PostMapping("/create")
    public Result<AdaptationTypeVO> create(
            @Valid @RequestBody AdaptationTypeDTO dto) {

        return Result.success(
                adaptationTypeService.createAdaptationType(dto)
        );
    }

    @PostMapping("/update")
    public Result<AdaptationTypeVO> update(
            @Valid @RequestBody AdaptationTypeDTO dto) {

        return Result.success(
                adaptationTypeService.updateAdaptationType(dto)
        );
    }

    @PostMapping("/delete")
    public Result<Void> delete(
            @RequestBody List<Integer> ids) {

        adaptationTypeService.deleteAdaptationTypes(ids);

        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<AdaptationTypeVO> getById(
            @PathVariable Integer id) {

        return Result.success(
                adaptationTypeService.getAdaptationTypeById(id)
        );
    }

    @GetMapping("/list")
    public Result<List<AdaptationTypeVO>> list() {

        return Result.success(
                adaptationTypeService.listAdaptationTypes()
        );
    }
}
