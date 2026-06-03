package org.killze.acgbox.entity.content;

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
public class AnimeTag {

    /**
     * 动画 ID，关联 anime 表，也是联合主键的一部分。
     */
    private Integer animeId;

    /**
     * 标签 ID，关联 tag 表，也是联合主键的一部分。
     */
    private Integer tagId;
}
