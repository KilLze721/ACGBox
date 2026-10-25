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
 * 通用别名实体，对应 alias 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alias")
public class Alias {

    /**
     * 别名 ID。
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
     * 别名名称。
     */
    @TableField("alias_name")
    private String aliasName;

    /**
     * 同一关联对象内的排序值，越小越靠前，同值时按别名 ID 升序。
     */
    @TableField("sort_order")
    private Long sortOrder;
}
