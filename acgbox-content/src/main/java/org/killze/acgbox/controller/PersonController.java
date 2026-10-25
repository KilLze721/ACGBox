package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.content.PersonDTO;
import org.killze.acgbox.dto.content.PersonPageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.PersonService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.PersonVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人物管理控制器，沿用内容管理的 ADMIN、EDITOR 访问权限。
 */
@RestController
@RequestMapping("/persons")
@Slf4j
public class PersonController {

    @Autowired
    private PersonService personService;

    /**
     * 创建人物，允许同名。
     */
    @PostMapping("/create")
    public Result<PersonVO> createPerson(@Valid @RequestBody PersonDTO personDTO) {
        log.info("创建人物：{}", personDTO.getName());
        return Result.success(personService.createPerson(personDTO));
    }

    /**
     * 修改已有的人物资料。
     */
    @PostMapping("/update")
    public Result<PersonVO> updatePerson(@Valid @RequestBody PersonDTO personDTO) {
        log.info("修改人物：{}", personDTO.getId());
        return Result.success(personService.updatePerson(personDTO));
    }

    /**
     * 单条或批量删除人物，被作品引用时整批拒绝。
     */
    @PostMapping("/delete")
    public Result<Void> deletePerson(@RequestBody List<Long> ids) {
        log.info("删除人物：{}", ids);
        personService.deletePersons(ids);
        return Result.success();
    }

    /**
     * 根据 ID 查询人物详情。
     */
    @GetMapping("/{id}")
    public Result<PersonVO> getPersonById(@PathVariable Long id) {
        log.info("根据 ID 获取人物：{}", id);
        return Result.success(personService.getPersonById(id));
    }

    /**
     * 分页搜索人物。
     */
    @GetMapping("/page")
    public Result<PageVO<PersonVO>> pagePerson(PersonPageDTO pageDTO) {
        log.info("分页查询人物：{}", pageDTO);
        return Result.success(personService.pagePerson(pageDTO));
    }
}
