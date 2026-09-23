package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 动画视图对象
 */
@Data
@Builder
public class AnimeVO {

    /**
     * 动画 ID
     */
    private Long id;

    /**
     * 动画名称
     */
    private String name;

    /**
     * 动画总集数
     */
    private Long episodeCount;

    /**
     * 放送类型 ID
     */
    private Long broadcastTypeId;

    /**
     * 改编类型 ID
     */
    private Long adaptationTypeId;

    /**
     * 地区 ID
     */
    private Long regionId;

    /**
     * 动画开播日期
     */
    private LocalDate airDate;

    /**
     * 封面图片 URL
     */
    private String coverImageUrl;

    /**
     * 放送状态
     */
    private Long status;

    /**
     * 动画简介
     */
    private String description;

    /**
     * 动画别名列表
     */
    private List<String> aliasNames;

    /**
     * 动画关联公司列表
     */
    private List<CompanyRelationVO> companies;

    /**
     * 动画外部链接列表
     */
    private List<ExternalLinkVO> externalLinks;

    /**
     * 动画标签 ID 列表
     */
    private List<Long> tagIds;

    /**
     * 动画所属系列 ID
     */
    private Long seriesId;

    /**
     * 动画在系列内的排序值
     */
    private Long seriesSortOrder;

    /**
     * 个人评分
     */
    private BigDecimal personalRatingScore;
}
