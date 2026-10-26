package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.killze.acgbox.dto.content.BookTypeDTO;
import org.killze.acgbox.entity.content.Book;
import org.killze.acgbox.entity.content.BookType;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.BookMapper;
import org.killze.acgbox.mapper.BookTypeMapper;
import org.killze.acgbox.service.BookTypeService;
import org.killze.acgbox.vo.content.BookTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍主类型管理，不自动插入类型或假定固定类型 ID。
 */
@Service
public class BookTypeServiceImpl implements BookTypeService {
    @Autowired
    private BookTypeMapper bookTypeMapper;
    @Autowired
    private BookMapper bookMapper;

    @Override
    public BookTypeVO createBookType(BookTypeDTO dto) {
        String name = name(dto.getName());
        checkName(name, null);
        long sortOrder = dto.getSortOrder() == null ? 0L : dto.getSortOrder();
        checkSort(sortOrder);
        LocalDateTime now = LocalDateTime.now();
        BookType type = BookType.builder().name(name).sortOrder(sortOrder).createdAt(now).updatedAt(now).build();
        try {
            bookTypeMapper.insert(type);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("此书籍类型已存在");
        }
        return view(type);
    }

    @Override
    public BookTypeVO updateBookType(BookTypeDTO dto) {
        BookType type = getType(dto.getId());
        String name = name(dto.getName());
        checkName(name, type.getId());
        if (dto.getSortOrder() != null) {
            checkSort(dto.getSortOrder());
            type.setSortOrder(dto.getSortOrder());
        }
        type.setName(name);
        type.setUpdatedAt(LocalDateTime.now());
        try {
            if (bookTypeMapper.updateById(type) == 0) {
                throw new BusinessException("此书籍类型不存在");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("此书籍类型已存在");
        }
        return view(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBookTypes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的书籍类型");
        }
        if (ids.stream().anyMatch(id -> id == null || id < 1L || id > 2147483647L)) {
            throw new BusinessException("书籍类型 ID 必须处于 1 到 2147483647 之间");
        }
        List<Long> targets = ids.stream().distinct().toList();
        if (bookTypeMapper.selectByIds(targets).size() != targets.size()) {
            throw new BusinessException("所选书籍类型不存在");
        }
        String message = "所选书籍类型已被作品引用，不能删除";
        if (bookMapper.selectCount(new LambdaQueryWrapper<Book>().in(Book::getBookTypeId, targets)) > 0) {
            throw new BusinessException(message);
        }
        try {
            if (bookTypeMapper.deleteByIds(targets) != targets.size()) {
                throw new BusinessException("所选书籍类型已发生变化，请刷新重试");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(message);
        }
    }

    @Override
    public BookTypeVO getBookTypeById(Long id) {
        return view(getType(id));
    }

    @Override
    public List<BookTypeVO> listBookTypes() {
        return bookTypeMapper.selectList(new LambdaQueryWrapper<BookType>()
                        .orderByAsc(BookType::getSortOrder).orderByAsc(BookType::getId))
                .stream().map(this::view).toList();
    }

    private BookType getType(Long id) {
        if (id == null || id < 1L || id > 2147483647L) {
            throw new BusinessException("书籍类型 ID 必须处于 1 到 2147483647 之间");
        }
        BookType type = bookTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException("此书籍类型不存在");
        }
        return type;
    }

    private void checkName(String name, Long id) {
        if (bookTypeMapper.selectCount(new LambdaQueryWrapper<BookType>()
                .eq(BookType::getName, name).ne(id != null, BookType::getId, id)) > 0) {
            throw new BusinessException("此书籍类型已存在");
        }
    }

    private String name(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("书籍类型名称不能为空");
        }
        String value = name.trim();
        if (value.codePointCount(0, value.length()) > 50) {
            throw new BusinessException("书籍类型名称不能超过 50 字符");
        }
        return value;
    }

    private void checkSort(long value) {
        if (value < 0L || value > 2147483647L) {
            throw new BusinessException("书籍类型排序必须处于 0 到 2147483647 之间");
        }
    }

    private BookTypeVO view(BookType type) {
        return BookTypeVO.builder().id(type.getId()).name(type.getName()).sortOrder(type.getSortOrder()).build();
    }
}
