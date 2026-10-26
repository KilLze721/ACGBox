package org.killze.acgbox.dto.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.killze.acgbox.deserializer.IntegerValueDeserializer;
import org.killze.acgbox.deserializer.BookDateDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;
import java.util.List;

/**
 * 书籍完整表单请求，数值只接受整数 JSON，不改变动画绑定规则。
 */
@Data
public class BookDTO {
    /**
     * 书籍 ID，由数据库生成。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long id;

    /**
     * 作品名称，允许不同作品同名。
     */
    @NotBlank(message = "书名不能为空")
    private String name;

    /**
     * 书籍主类型 ID。
     */
    @NotNull(message = "bookTypeId 不能为空")
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long bookTypeId;

    /**
     * 改编类型 ID。
     */
    @NotNull(message = "adaptationTypeId 不能为空")
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long adaptationTypeId;

    /**
     * 原作所属地区 ID。
     */
    @NotNull(message = "regionId 不能为空")
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long regionId;

    /**
     * 原作首次发表日期，具体日期未知时为空。
     */
    @JsonDeserialize(using = BookDateDeserializer.class)
    private LocalDate publishDate;

    /**
     * 作品封面 URL，可空。
     */
    private String coverImageUrl;

    /**
     * 作品进度：0 未知、1 未发表、2 连载中、3 已完结、4 暂停中、5 已终止。
     */
    @NotNull(message = "status 不能为空")
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long status;

    /**
     * 已知卷数，0 为有效值，未知或不适用时为空。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long volumeCount;

    /**
     * 已知章节数，0 为有效值，未知或不适用时为空。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long chapterCount;

    /**
     * 作品简介，可空。
     */
    private String description;

    /**
     * 别名数组，去空去重并保留顺序。
     */
    private List<String> aliasNames;

    /**
     * 原作层面的公司及可空职责。
     */
    @Valid
    private List<@NotNull(message = "公司关联项不能为空") BookCompanyDTO> companies;

    /**
     * 人员职责，更新时未传或 null 保留，空数组清空。
     */
    @Valid
    private List<@NotNull(message = "人物关联项不能为空") BookPersonDTO> persons;

    /**
     * 外部链接，仅允许 http 和 https。
     */
    @Valid
    private List<@NotNull(message = "外链项不能为空") BookExternalLinkDTO> externalLinks;

    /**
     * 标签 ID，去重后校验存在。
     */
    @JsonDeserialize(contentUsing = IntegerValueDeserializer.class)
    private List<Long> tagIds;

    /**
     * 选定的系列 ID。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long seriesId;

    /**
     * 系列内排序，默认 0。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long seriesSortOrder;

    /**
     * 仅创建时生效，未选系列时按书名自动创建或复用。
     */
    private Boolean autoCreateSeries;

    /**
     * 0 到 10 的整数，未传或 null 为未评分。
     */
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    private Long personalRatingScore;
}
