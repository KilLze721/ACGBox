package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 动画数据传输对象
 */
@Data
public class AnimeDTO {

    /**
     * 动画 ID
     */
    private Long id;

    /**
     * 动画名称
     */
    @NotBlank(message = "动画名称不能为空")
    private String name;

    /**
     * 动画总集数
     */
    private Long episodeCount;

    /**
     * 放送类型 ID
     */
    @NotNull(message = "放送类型不能为空")
    private Long broadcastTypeId;

    /**
     * 改编类型 ID
     */
    @NotNull(message = "改编类型不能为空")
    private Long adaptationTypeId;

    /**
     * 地区 ID
     */
    @NotNull(message = "地区不能为空")
    private Long regionId;

    /**
     * 动画开播日期
     */
    @NotNull(message = "开播日期不能为空")
    private LocalDate airDate;

    /**
     * 封面图片 URL
     */
    private String coverImageUrl;

    /**
     * 放送状态，1：未放送，2：放送中，3：已完结，4：其他
     */
    @NotNull(message = "放送状态不能为空")
    private Long status;

    /**
     * 动画别名列表
     */
    private List<String> aliasNames;

    /**
     * 动画关联公司列表
     */
    @Valid
    private List<CompanyRelationDTO> companies;

    /**
     * 动画外部链接列表
     */
    @Valid
    private List<ExternalLinkDTO> externalLinks;

    /**
     * 动画标签 ID 列表
     */
    private List<Long> tagIds;

    /**
     * 已有系列 ID，传入时表示加入已有系列
     */
    private Long seriesId;

    /**
     * 加入已有系列时的系列内排序值
     */
    private Long seriesSortOrder;

    /**
     * 未选择系列时，是否使用动画名称自动创建系列
     */
    private Boolean autoCreateSeries;

    /**
     * 个人评分，范围 0 到 10，支持一位小数
     */
    @DecimalMin(value = "0.0", message = "个人评分不能小于 0")
    @DecimalMax(value = "10.0", message = "个人评分不能大于 10")
    @Digits(integer = 2, fraction = 1, message = "个人评分最多支持一位小数")
    private BigDecimal personalRatingScore;
}
