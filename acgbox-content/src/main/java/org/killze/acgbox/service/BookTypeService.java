package org.killze.acgbox.service;

import org.killze.acgbox.dto.content.BookTypeDTO;
import org.killze.acgbox.vo.content.BookTypeVO;
import java.util.List;

/**
 * 书籍主类型服务，沿用现有字典接口形式。
 */
public interface BookTypeService {
    /** 创建类型，排序缺省为 0。 */
    BookTypeVO createBookType(BookTypeDTO dto);
    /** 修改类型，排序缺省保留旧值。 */
    BookTypeVO updateBookType(BookTypeDTO dto);
    /** 被书籍引用时整批拒绝删除。 */
    void deleteBookTypes(List<Long> ids);
    /** 查询类型详情。 */
    BookTypeVO getBookTypeById(Long id);
    /** 按排序值、ID 升序返回全部类型。 */
    List<BookTypeVO> listBookTypes();
}
