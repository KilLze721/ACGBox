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
 * 动画别名实体，对应 anime_alias 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("anime_alias")
public class AnimeAlias {

    /**
     * 别名 ID。
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 动画 ID，关联 anime 表。
     */
    @TableField("anime_id")
    private Integer animeId;

    /**
     * 动画别名名称。
     */
    @TableField("alias_name")
    private String aliasName;
}
