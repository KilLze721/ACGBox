package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用外部链接实体，对应 external_link 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("external_link")
public class ExternalLink {

    /**
     * 链接 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 外链所属对象类型，例如 ANIME、COMPANY、SERIES。
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 外链所属对象 ID。
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 链接标题，例如官网、Bangumi、维基百科。
     */
    private String title;

    /**
     * 链接 URL。
     */
    private String url;

    /**
     * 排序值，越小越靠前。
     */
    @TableField("sort_order")
    private Long sortOrder;
}
