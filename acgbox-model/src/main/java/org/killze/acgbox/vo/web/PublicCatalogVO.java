package org.killze.acgbox.vo.web;

import org.killze.acgbox.vo.content.TagVO;

import java.time.LocalDate;
import java.util.List;

/** 用户端目录响应字段白名单，不继承管理端模型。 */
public final class PublicCatalogVO {
    private PublicCatalogVO() { }

    /** 筛选选项和目录字典摘要。 */
    public record Option(Long id, String name) { }
    /** 作品中公开展示的创作人员职责。 */
    public record Person(Long personId, String personName, String role) { }
    /** 作品中公开展示的参与公司职责。 */
    public record Company(Long companyId, String companyName, String role) { }

    /** 公开动画目录条目，日期与字典资料可为空。 */
    public record Anime(Long id, String name, List<String> aliasNames, String coverImageUrl, Long status,
                        LocalDate airDate, Option broadcastType, Option adaptationType, List<TagVO> tags,
                        List<Person> persons, List<Company> companies) { }

    /** 公开书籍目录条目，日期与字典资料可为空。 */
    public record Book(Long id, String name, List<String> aliasNames, String coverImageUrl, Long status,
                       LocalDate publishDate, Option bookType, Option adaptationType, List<TagVO> tags,
                       List<Person> persons, List<Company> companies) { }

    /** 动画目录筛选项。 */
    public record AnimeFilters(List<Option> broadcastTypes, List<Option> adaptationTypes, List<Option> statuses,
                               List<Integer> years, boolean hasUnknownYear, List<TagVO> tags) { }

    /** 书籍目录筛选项。 */
    public record BookFilters(List<Option> bookTypes, List<Option> adaptationTypes, List<Option> statuses,
                              List<Integer> years, boolean hasUnknownYear, List<TagVO> tags) { }
}
