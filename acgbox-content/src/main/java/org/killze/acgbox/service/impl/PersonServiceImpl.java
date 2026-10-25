package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.content.PersonDTO;
import org.killze.acgbox.dto.content.PersonPageDTO;
import org.killze.acgbox.entity.content.Person;
import org.killze.acgbox.entity.content.PersonRelation;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.PersonMapper;
import org.killze.acgbox.mapper.PersonRelationMapper;
import org.killze.acgbox.service.PersonService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.PersonVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人物资料服务实现类，人物同名不合并，删除保留作品引用保护。
 */
@Service
public class PersonServiceImpl implements PersonService {

    @Autowired
    private PersonMapper personMapper;

    @Autowired
    private PersonRelationMapper personRelationMapper;

    /**
     * 创建人物，忽略客户端传入的 ID。
     */
    @Override
    public PersonVO createPerson(PersonDTO personDTO) {
        LocalDateTime now = LocalDateTime.now();
        Person person = Person.builder()
                .name(buildPersonName(personDTO.getName()))
                .description(StringUtils.hasText(personDTO.getDescription()) ? personDTO.getDescription() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        personMapper.insert(person);
        return buildPersonVO(person);
    }

    /**
     * 修改人物，显式写入简介以支持清空。
     */
    @Override
    public PersonVO updatePerson(PersonDTO personDTO) {
        Person person = getPerson(personDTO.getId());
        person.setName(buildPersonName(personDTO.getName()));
        person.setDescription(StringUtils.hasText(personDTO.getDescription()) ? personDTO.getDescription() : null);
        person.setUpdatedAt(LocalDateTime.now());
        int updated = personMapper.update(null, new LambdaUpdateWrapper<Person>()
                .eq(Person::getId, person.getId())
                .set(Person::getName, person.getName())
                .set(Person::getDescription, person.getDescription())
                .set(Person::getUpdatedAt, person.getUpdatedAt()));
        if (updated == 0) {
            throw new BusinessException("此人物不存在");
        }
        return buildPersonVO(person);
    }

    /**
     * 检查整批目标及全部类型的作品引用，再执行事务删除。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePersons(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的人物");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException("人物 ID 必须大于 0");
        }
        List<Long> personIds = ids.stream().distinct().toList();
        if (personMapper.selectByIds(personIds).size() != personIds.size()) {
            throw new BusinessException("所选人物不存在，请刷新后重试");
        }
        String message = "所选人物已被作品引用，不能删除，请先解除关联";
        if (personRelationMapper.selectCount(new LambdaQueryWrapper<PersonRelation>()
                .in(PersonRelation::getPersonId, personIds)) > 0) {
            throw new BusinessException(message);
        }
        try {
            if (personMapper.deleteByIds(personIds) != personIds.size()) {
                throw new BusinessException("所选人物已发生变化，请刷新后重试");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(message);
        }
    }

    /**
     * 根据 ID 查询人物详情。
     */
    @Override
    public PersonVO getPersonById(Long id) {
        return buildPersonVO(getPerson(id));
    }

    /**
     * 人物专用分页参数校验，不修改公共分页规则。
     */
    @Override
    public PageVO<PersonVO> pagePerson(PersonPageDTO pageDTO) {
        if (pageDTO.getPageNum() == null) {
            pageDTO.setPageNum(1L);
        }
        if (pageDTO.getPageSize() == null) {
            pageDTO.setPageSize(20L);
        }
        if (pageDTO.getPageNum() < 1L) {
            throw new BusinessException("pageNum 必须大于等于 1");
        }
        if (pageDTO.getPageSize() < 1L || pageDTO.getPageSize() > 100L) {
            throw new BusinessException("pageSize 必须处于 1 到 100 之间");
        }
        LambdaQueryWrapper<Person> wrapper = new LambdaQueryWrapper<Person>()
                .apply(StringUtils.hasText(pageDTO.getName()), "name ILIKE '%' || {0} || '%'",
                        pageDTO.getName() == null ? null : pageDTO.getName().trim())
                .orderByDesc(Person::getUpdatedAt)
                .orderByDesc(Person::getId);
        Page<Person> result = personMapper.selectPage(new Page<>(pageDTO.getPageNum(), pageDTO.getPageSize()), wrapper);
        return PageVO.<PersonVO>builder()
                .pageNum(result.getCurrent())
                .pageSize(result.getSize())
                .total(result.getTotal())
                .pages(result.getPages())
                .rows(result.getRecords().stream().map(this::buildPersonVO).toList())
                .build();
    }

    /**
     * 校验现存人物 ID，不根据姓名自动匹配。
     */
    private Person getPerson(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("人物 ID 必须大于 0");
        }
        Person person = personMapper.selectById(id);
        if (person == null) {
            throw new BusinessException("此人物不存在");
        }
        return person;
    }

    /**
     * 去除姓名首尾空格后按字符数校验，与数据库长度限制保持一致。
     */
    private String buildPersonName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("人物姓名不能为空");
        }
        String result = name.trim();
        if (result.codePointCount(0, result.length()) > 100) {
            throw new BusinessException("人物姓名不能超过 100 字符");
        }
        return result;
    }

    /**
     * 构建包含只读时间的人物资料。
     */
    private PersonVO buildPersonVO(Person person) {
        return PersonVO.builder()
                .id(person.getId())
                .name(person.getName())
                .description(person.getDescription())
                .createdAt(person.getCreatedAt())
                .updatedAt(person.getUpdatedAt())
                .build();
    }
}
