package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动画与标签关联实体，对应 anime_tag 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("anime_tag")
public class AnimeTag {

    /**
     * 动画 ID，关联 anime 表，也是联合主键的一部分。
     */
    @TableField("anime_id")
    private Long animeId;

    /**
     * 标签 ID，关联 tag 表，也是联合主键的一部分。
     */
    @TableField("tag_id")
    private Long tagId;
}
