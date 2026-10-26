package org.killze.acgbox.dto.content;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;
import java.util.List;

/**
 * 书籍分页筛选参数，各条件按 AND 组合。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookPageDTO extends PageDTO {
    /**
     * 书名、BOOK 别名或所属系列名，忽略字母大小写。
     */
    private String keyword;

    /**
     * 标签 ID 列表。
     */
    private List<Long> tagIds;

    /**
     * ALL 或 ANY，默认 ALL。
     */
    private String tagMatchMode;

    /**
     * bookTypeId 精确筛选，合法但不存在时返回空分页。
     */
    private Long bookTypeId;

    /**
     * adaptationTypeId 精确筛选，合法但不存在时返回空分页。
     */
    private Long adaptationTypeId;

    /**
     * regionId 精确筛选，合法但不存在时返回空分页。
     */
    private Long regionId;

    /**
     * 书籍状态，0 是有效筛选值。
     */
    private Long status;

    /**
     * companyId 精确筛选，合法但不存在时返回空分页。
     */
    private Long companyId;

    /**
     * personId 精确筛选，合法但不存在时返回空分页。
     */
    private Long personId;

    /**
     * seriesId 精确筛选，合法但不存在时返回空分页。
     */
    private Long seriesId;

    /**
     * 日期范围起点，支持年、年月、完整日期。
     */
    private String publishStartDate;

    /**
     * 日期范围终点，支持年、年月、完整日期。
     */
    private String publishEndDate;

    /**
     * 最低整数个人评分。
     */
    private Long ratingMin;

    /**
     * 最高整数个人评分。
     */
    private Long ratingMax;

    /**
     * PUBLISH_DATE 或 PERSONAL_RATING，默认 PUBLISH_DATE。
     */
    private String sortBy;

    /**
     * ASC 或 DESC，默认 DESC。
     */
    private String sortDirection;
}
