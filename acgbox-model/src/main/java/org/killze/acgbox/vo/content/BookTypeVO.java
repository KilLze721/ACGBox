package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 书籍主类型视图对象。
 */
@Data
@Builder
public class BookTypeVO {
    /**
     * 类型 ID。
     */
    private Long id;

    /**
     * 类型名称。
     */
    private String name;

    /**
     * 非负排序值。
     */
    private Long sortOrder;
}
