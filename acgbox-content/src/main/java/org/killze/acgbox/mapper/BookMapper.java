package org.killze.acgbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.killze.acgbox.dto.content.BookPageDTO;
import org.killze.acgbox.entity.content.Book;
import java.time.LocalDate;

/**
 * 书籍资料及数据库分页查询。
 */
public interface BookMapper extends BaseMapper<Book> {
    /**
     * 在数据库中筛选、排序和分页，关联使用 EXISTS 避免一人多职造成重复。
     */
    IPage<Book> selectBookPage(Page<Book> page, @Param("pageDTO") BookPageDTO pageDTO,
                              @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
