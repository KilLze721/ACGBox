package org.killze.acgbox.entity.content;

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
public class AnimeAlias {

    /**
     * 别名 ID。
     */
    private Integer id;

    /**
     * 动画 ID，关联 anime 表。
     */
    private Integer animeId;

    /**
     * 动画别名名称。
     */
    private String aliasName;
}
