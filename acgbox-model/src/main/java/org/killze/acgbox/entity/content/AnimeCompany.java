package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("anime_company")
public class AnimeCompany {

    /**
     * 动画 ID，关联 anime 表，也是联合主键的一部分。
     */
    @TableField("anime_id")
    private Integer animeId;

    /**
     * 公司 ID，关联 company 表，也是联合主键的一部分。
     */
    @TableField("company_id")
    private Integer companyId;

    /**
     * 公司在动画中的职责，例如制作、发行、企划。
     */
    private String role;
}
