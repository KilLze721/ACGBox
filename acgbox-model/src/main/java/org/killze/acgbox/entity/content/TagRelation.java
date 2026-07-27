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
 * 通用标签关联实体，对应 tag_relation 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tag_relation")
public class TagRelation {

    /**
     * 标签关联 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联对象类型，例如 ANIME、MANGA、NOVEL。
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 关联对象 ID。
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 标签 ID。
     */
    @TableField("tag_id")
    private Long tagId;
}
