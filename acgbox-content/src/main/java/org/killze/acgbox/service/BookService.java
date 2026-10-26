package org.killze.acgbox.service;

import org.killze.acgbox.dto.content.BookDTO;
import org.killze.acgbox.dto.content.BookPageDTO;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.BookVO;
import org.killze.acgbox.vo.content.BookPageVO;
import java.util.List;

/**
 * 书籍作品服务，同名作品按 ID 独立管理。
 */
public interface BookService {
    /** 创建书籍及全部关联。 */
    BookVO createBook(BookDTO dto);
    /** 完整编辑书籍，可空基础字段显式清空。 */
    BookVO updateBook(BookDTO dto);
    /** 仅清理当前 BOOK 及 book 关联，再删除书籍本体。 */
    void deleteBooks(List<Long> ids);
    /** 根据现存 ID 查询完整书籍详情。 */
    BookVO getBookById(Long id);
    /** 数据库分页筛选并批量组装当页关联。 */
    PageVO<BookPageVO> pageBook(BookPageDTO dto);
}
