package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 书籍主类型实体，对应 book_type 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("book_type")
public class BookType {
    /**
     * 书籍类型 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 类型名称，按数据库约束唯一。
     */
    private String name;

    /**
     * 非负排序值，同值按 ID 升序。
     */
    @TableField("sort_order")
    private Long sortOrder;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
