package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.TagDTO;
import org.killze.acgbox.dto.content.TagPageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.TagService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.TagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 *
 * @author killze
 */
@RestController
@RequestMapping("/tags")
@Slf4j
public class TagController {

    @Autowired
    private TagService tagService;

    /**
     * 创建标签
     */
    @PostMapping("/create")
    public Result<TagVO> createTag(@Valid @RequestBody TagDTO tagDTO) {
        log.info("创建标签：{}", tagDTO);
        return Result.success(tagService.createTag(tagDTO));
    }

    /**
     * 修改标签
     */
    @PostMapping("/update")
    public Result<TagVO> updateTag(@Valid @RequestBody TagDTO tagDTO) {
        log.info("修改标签：{}", tagDTO);
        return Result.success(tagService.updateTag(tagDTO));
    }

    /**
     * 批量删除标签
     */
    @PostMapping("/delete")
    public Result<Void> deleteTag(@RequestBody List<Long> ids) {
        log.info("删除标签：{}", ids);
        tagService.deleteTags(ids);
        return Result.success();
    }

    /**
     * 根据 ID 获取标签
     */
    @GetMapping("/{id}")
    public Result<TagVO> getTagById(@PathVariable Long id) {
        log.info("根据 ID 获取标签：{}", id);
        return Result.success(tagService.getTagById(id));
    }

    /**
     * 分页查询标签
     */
    @GetMapping("/page")
    public Result<PageVO<TagVO>> pageTag(TagPageDTO pageDTO) {
        log.info("分页查询标签：{}", pageDTO);
        return Result.success(tagService.pageTag(pageDTO));
    }
}
