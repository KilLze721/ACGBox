package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.*;
import org.killze.acgbox.service.BookTypeService;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.vo.content.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.WebDataBinder;
import java.beans.PropertyEditorSupport;
import java.util.List;

/**
 * 书籍类型管理接口，沿用内容管理的 ADMIN、EDITOR 权限。
 */
@RestController
@RequestMapping("/book-type")
@Slf4j
public class BookTypeController {
    @Autowired
    private BookTypeService bookTypeService;

    /** 类型 ID 同样仅按十进制整数解析，不接受十六进制等隐式转换。 */
    @InitBinder
    public void bindBookTypeNumbers(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(Long.valueOf(text));
            }
        });
    }

    /** 创建书籍类型。 */
    @PostMapping("/create")
    public Result<BookTypeVO> createBookType(@Valid @RequestBody BookTypeDTO dto) {
        log.info("创建书籍类型：{}", dto.getName());
        return Result.success(bookTypeService.createBookType(dto));
    }

    /** 修改已有书籍类型。 */
    @PostMapping("/update")
    public Result<BookTypeVO> updateBookType(@Valid @RequestBody BookTypeDTO dto) {
        log.info("修改书籍类型：{}", dto.getId());
        return Result.success(bookTypeService.updateBookType(dto));
    }

    /** 单条或批量删除书籍类型，JSON 数组形式不变。 */
    @PostMapping("/delete")
    public Result<Void> deleteBookType(@RequestBody BookIdsDTO ids) {
        log.info("删除书籍类型：{}", ids);
        bookTypeService.deleteBookTypes(ids);
        return Result.success();
    }

    /** 根据 ID 查询书籍类型。 */
    @GetMapping("/{id}")
    public Result<BookTypeVO> getBookTypeById(@PathVariable Long id) {
        return Result.success(bookTypeService.getBookTypeById(id));
    }

    /** 按排序值、ID 升序查询全部书籍类型。 */
    @GetMapping("/list")
    public Result<List<BookTypeVO>> listBookTypes() {
        return Result.success(bookTypeService.listBookTypes());
    }
}
