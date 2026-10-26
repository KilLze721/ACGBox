package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 书籍作品实体，对应 book 表，不代表单卷或出版版本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("book")
public class Book {
    /**
     * 书籍 ID，由数据库生成。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 作品名称，允许不同作品同名。
     */
    private String name;

    /**
     * 书籍主类型 ID。
     */
    @TableField("book_type_id")
    private Long bookTypeId;

    /**
     * 改编类型 ID。
     */
    @TableField("adaptation_type_id")
    private Long adaptationTypeId;

    /**
     * 原作所属地区 ID。
     */
    @TableField("region_id")
    private Long regionId;

    /**
     * 原作首次发表日期，具体日期未知时为空。
     */
    @TableField("publish_date")
    private LocalDate publishDate;

    /**
     * 作品封面 URL，可空。
     */
    @TableField("cover_image_url")
    private String coverImageUrl;

    /**
     * 作品进度：0 未知、1 未发表、2 连载中、3 已完结、4 暂停中、5 已终止。
     */
    private Long status;

    /**
     * 已知卷数，0 为有效值，未知或不适用时为空。
     */
    @TableField("volume_count")
    private Long volumeCount;

    /**
     * 已知章节数，0 为有效值，未知或不适用时为空。
     */
    @TableField("chapter_count")
    private Long chapterCount;

    /**
     * 作品简介，可空。
     */
    private String description;

    /**
     * 记录创建时间，只读。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间，由应用维护。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
