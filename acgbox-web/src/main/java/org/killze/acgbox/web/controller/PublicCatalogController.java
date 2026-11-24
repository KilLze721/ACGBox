package org.killze.acgbox.web.controller;

import org.killze.acgbox.result.Result;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.web.dto.PublicCatalogPageDTO;
import org.killze.acgbox.web.service.PublicCatalogService;
import org.killze.acgbox.web.vo.PublicCatalogVO;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.beans.PropertyEditorSupport;

/** 游客动画、书籍目录的只读入口。 */
@RestController
@RequestMapping("/web")
public class PublicCatalogController {
    private final PublicCatalogService catalogService;

    public PublicCatalogController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 目录 URL 中的数值必须为十进制整数，不接受小数或十六进制形式。 */
    @InitBinder
    public void bindCatalogNumbers(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(Long.valueOf(text));
            }
        });
    }

    /** 查询已公开的动画目录。 */
    @GetMapping("/anime/page")
    public Result<PageVO<PublicCatalogVO.Anime>> pageAnime(PublicCatalogPageDTO pageDTO) {
        return Result.success(catalogService.pageAnime(pageDTO));
    }

    /** 查询已公开的书籍目录。 */
    @GetMapping("/book/page")
    public Result<PageVO<PublicCatalogVO.Book>> pageBook(PublicCatalogPageDTO pageDTO) {
        return Result.success(catalogService.pageBook(pageDTO));
    }

    /** 获取动画目录可用的筛选选项。 */
    @GetMapping("/anime/filters")
    public Result<PublicCatalogVO.AnimeFilters> animeFilters() {
        return Result.success(catalogService.animeFilters());
    }

    /** 获取书籍目录可用的筛选选项。 */
    @GetMapping("/book/filters")
    public Result<PublicCatalogVO.BookFilters> bookFilters() {
        return Result.success(catalogService.bookFilters());
    }
}
