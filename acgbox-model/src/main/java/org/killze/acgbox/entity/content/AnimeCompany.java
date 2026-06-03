package org.killze.acgbox.entity.content;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动画与公司关联实体，对应 anime_company 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnimeCompany {

    /**
     * 动画 ID，关联 anime 表，也是联合主键的一部分。
     */
    private Integer animeId;

    /**
     * 公司 ID，关联 company 表，也是联合主键的一部分。
     */
    private Integer companyId;

    /**
     * 公司在动画中的职责，例如制作、发行、企划。
     */
    private String role;
}
