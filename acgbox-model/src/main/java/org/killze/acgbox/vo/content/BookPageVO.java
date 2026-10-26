package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍分页视图，批量展开资料名称，不包含长篇简介。
 */
@Data
@Builder
public class BookPageVO {
    /**
     * 书籍 ID，由数据库生成。
     */
    private Long id;

    /**
     * 作品名称，允许不同作品同名。
     */
    private String name;

    /**
     * 原作首次发表日期，具体日期未知时为空。
     */
    private LocalDate publishDate;

    /**
     * 作品封面 URL，可空。
     */
    private String coverImageUrl;

    /**
     * 作品进度：0 未知、1 未发表、2 连载中、3 已完结、4 暂停中、5 已终止。
     */
    private Long status;

    /**
     * 已知卷数，0 为有效值，未知或不适用时为空。
     */
    private Long volumeCount;

    /**
     * 已知章节数，0 为有效值，未知或不适用时为空。
     */
    private Long chapterCount;

    /**
     * 记录创建时间，只读。
     */
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间，由应用维护。
     */
    private LocalDateTime updatedAt;

    /**
     * 书籍主类型。
     */
    private BookTypeVO bookType;

    /**
     * 改编类型。
     */
    private AdaptationTypeVO adaptationType;

    /**
     * 原作所属地区。
     */
    private RegionVO region;

    /**
     * 有序别名。
     */
    private List<String> aliasNames;

    /**
     * 标签资料。
     */
    private List<TagVO> tags;

    /**
     * 完整公司及职责。
     */
    private List<BookCompanyVO> companies;

    /**
     * 完整人员及职责。
     */
    private List<PersonRelationVO> persons;

    /**
     * 有序外链。
     */
    private List<ExternalLinkVO> externalLinks;

    /**
     * 所属系列，无系列时为空。
     */
    private SeriesVO series;

    /**
     * 整数个人评分，未评分为空。
     */
    private Long personalRating;
}
