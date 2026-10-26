package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.*;
import org.killze.acgbox.service.BookService;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.vo.content.*;
import org.killze.acgbox.vo.common.PageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.WebDataBinder;
import java.beans.PropertyEditorSupport;

/**
 * 书籍管理接口，沿用内容管理的 ADMIN、EDITOR 权限。
 */
@RestController
@RequestMapping("/book")
@Slf4j
public class BookController {
    @Autowired
    private BookService bookService;

    /**
     * 只在本控制器严格解析数值查询参数，拒绝空串、小数和十六进制等隐式转换。
     */
    @InitBinder
    public void bindBookNumbers(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(Long.valueOf(text));
            }
        });
    }

    /** 创建书籍。 */
    @PostMapping("/create")
    public Result<BookVO> createBook(@Valid @RequestBody BookDTO dto) {
        log.info("创建书籍：{}", dto.getName());
        return Result.success(bookService.createBook(dto));
    }

    /** 修改已有书籍。 */
    @PostMapping("/update")
    public Result<BookVO> updateBook(@Valid @RequestBody BookDTO dto) {
        log.info("修改书籍：{}", dto.getId());
        return Result.success(bookService.updateBook(dto));
    }

    /** 单条或批量删除书籍，JSON 数组形式不变。 */
    @PostMapping("/delete")
    public Result<Void> deleteBook(@RequestBody BookIdsDTO ids) {
        log.info("删除书籍：{}", ids);
        bookService.deleteBooks(ids);
        return Result.success();
    }

    /** 根据 ID 查询书籍。 */
    @GetMapping("/{id}")
    public Result<BookVO> getBookById(@PathVariable Long id) {
        return Result.success(bookService.getBookById(id));
    }

    /** 数据库分页筛选并返回当前页完整关联资料。 */
    @GetMapping("/page")
    public Result<PageVO<BookPageVO>> pageBook(BookPageDTO dto) {
        return Result.success(bookService.pageBook(dto));
    }
}
