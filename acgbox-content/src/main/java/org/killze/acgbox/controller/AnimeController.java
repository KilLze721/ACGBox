package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.AnimeService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.AnimePageVO;
import org.killze.acgbox.vo.content.AnimeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 动画控制器
 *
 * @author killze
 */
@RestController
@RequestMapping("/anime")
@Slf4j
public class AnimeController {

    @Autowired
    private AnimeService animeService;

    /**
     * 创建动画
     */
    @PostMapping("/create")
    public Result<AnimeVO> createAnime(@Valid @RequestBody AnimeDTO animeDTO) {
        log.info("创建动画：{}", animeDTO);
        return Result.success(animeService.createAnime(animeDTO));
    }

    /**
     * 修改动画
     */
    @PostMapping("/update")
    public Result<AnimeVO> updateAnime(@Valid @RequestBody AnimeDTO animeDTO) {
        log.info("修改动画：{}", animeDTO);
        return Result.success(animeService.updateAnime(animeDTO));
    }

    /**
     * 批量删除动画
     */
    @PostMapping("/delete")
    public Result<Void> deleteAnime(@RequestBody List<Long> ids) {
        log.info("删除动画：{}", ids);
        animeService.deleteAnime(ids);
        return Result.success();
    }

    /**
     * 分页查询动画
     */
    @GetMapping("/page")
    public Result<PageVO<AnimePageVO>> pageAnime(@Valid AnimePageDTO pageDTO) {
        log.info("分页查询动画：{}", pageDTO);
        return Result.success(animeService.pageAnime(pageDTO));
    }

    /**
     * 根据 ID 获取动画详情
     */
    @GetMapping("/{id}")
    public Result<AnimeVO> getAnimeById(@PathVariable Long id) {
        log.info("根据 ID 获取动画详情：{}", id);
        return Result.success(animeService.getAnimeById(id));
    }
}
