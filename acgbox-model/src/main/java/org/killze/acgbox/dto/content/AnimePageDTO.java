package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 动画分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnimePageDTO extends PageDTO {

    /**
     * 动画名称、别名或系列名称关键词
     */
    private String keyword;

    /**
     * 标签 ID 列表
     */
    private List<Long> tagIds;

    /**
     * 标签匹配方式，支持 ALL 和 ANY
     */
    private String tagMatchMode;

    /**
     * 放送类型 ID
     */
    private Long broadcastTypeId;

    /**
     * 改编类型 ID
     */
    private Long adaptationTypeId;

    /**
     * 放送日期范围开始值，格式为 yyyy 或 yyyy-MM
     */
    private String broadcastStartDate;

    /**
     * 放送日期范围结束值，格式为 yyyy 或 yyyy-MM
     */
    private String broadcastEndDate;

    /**
     * 动画状态
     */
    private Long status;

    /**
     * 地区 ID
     */
    private Long regionId;

    /**
     * 制作公司 ID
     */
    private Long companyId;

    /**
     * 最低个人评分
     */
    @DecimalMin(value = "0.0", message = "ratingMin 不能小于 0")
    @DecimalMax(value = "10.0", message = "ratingMin 不能大于 10")
    @Digits(integer = 2, fraction = 1, message = "ratingMin 最多支持一位小数")
    private BigDecimal ratingMin;

    /**
     * 最高个人评分
     */
    @DecimalMin(value = "0.0", message = "ratingMax 不能小于 0")
    @DecimalMax(value = "10.0", message = "ratingMax 不能大于 10")
    @Digits(integer = 2, fraction = 1, message = "ratingMax 最多支持一位小数")
    private BigDecimal ratingMax;

    /**
     * 排序字段，支持 BROADCAST_DATE 和 PERSONAL_RATING
     */
    private String sortBy;

    /**
     * 排序方向，支持 ASC 和 DESC
     */
    private String sortDirection;

    /**
     * 获取当前页码，兼容 page 参数
     *
     * @return 当前页码
     */
    public Long getPage() {
        return getPageNum();
    }

    /**
     * 设置当前页码，兼容 page 参数
     *
     * @param page 当前页码
     */
    public void setPage(Long page) {
        setPageNum(page);
    }
}
