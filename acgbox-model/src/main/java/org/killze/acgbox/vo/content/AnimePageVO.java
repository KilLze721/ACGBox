package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 动画分页视图对象
 */
@Data
@Builder
public class AnimePageVO {

    /**
     * 动画 ID
     */
    private Long id;

    /**
     * 动画名称
     */
    private String name;

    /**
     * 动画别名列表
     */
    private List<String> aliasNames;

    /**
     * 动画标签列表
     */
    private List<TagVO> tags;

    /**
     * 动画总集数
     */
    private Long episodeCount;

    /**
     * 放送类型
     */
    private BroadcastTypeVO broadcastType;

    /**
     * 改编类型
     */
    private AdaptationTypeVO adaptationType;

    /**
     * 动画开播日期
     */
    private LocalDate airDate;

    /**
     * 封面图片 URL
     */
    private String coverImageUrl;

    /**
     * 动画状态
     */
    private Long status;

    /**
     * 动画所属地区
     */
    private RegionVO region;

    /**
     * 动画关联公司列表
     */
    private List<AnimeCompanyVO> companies;

    /**
     * 动画外部链接列表
     */
    private List<ExternalLinkVO> externalLinks;

    /**
     * 个人评分
     */
    private BigDecimal personalRating;

    /**
     * 所属系列
     */
    private SeriesVO series;
}
