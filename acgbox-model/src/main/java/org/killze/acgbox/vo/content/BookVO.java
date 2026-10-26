package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍详情及写入响应，保留资料 ID 和直接可编辑的关联内容。
 */
@Data
@Builder
public class BookVO {
    /**
     * 书籍 ID，由数据库生成。
     */
    private Long id;

    /**
     * 作品名称，允许不同作品同名。
     */
    private String name;

    /**
     * 书籍主类型 ID。
     */
    private Long bookTypeId;

    /**
     * 改编类型 ID。
     */
    private Long adaptationTypeId;

    /**
     * 原作所属地区 ID。
     */
    private Long regionId;

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
     * 作品简介，可空。
     */
    private String description;

    /**
     * 记录创建时间，只读。
     */
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间，由应用维护。
     */
    private LocalDateTime updatedAt;

    /**
     * 按有效数组顺序保存的别名。
     */
    private List<String> aliasNames;

    /**
     * 公司关联，按排序值和关联 ID 升序。
     */
    private List<CompanyRelationVO> companies;

    /**
     * 完整人员职责及当前姓名。
     */
    private List<PersonRelationVO> persons;

    /**
     * 外部链接，按排序值和关联 ID 升序。
     */
    private List<ExternalLinkVO> externalLinks;

    /**
     * 标签 ID 列表。
     */
    private List<Long> tagIds;

    /**
     * 所属系列 ID，无系列时为空。
     */
    private Long seriesId;

    /**
     * 系列内排序，无系列时为空。
     */
    private Long seriesSortOrder;

    /**
     * 站长个人评分，仅 0 到 10 的整数，未评分为空。
     */
    private Long personalRatingScore;
}
