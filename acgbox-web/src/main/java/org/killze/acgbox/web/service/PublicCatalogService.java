package org.killze.acgbox.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.entity.content.Alias;
import org.killze.acgbox.entity.content.Tag;
import org.killze.acgbox.entity.content.TagRelation;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AliasMapper;
import org.killze.acgbox.mapper.TagMapper;
import org.killze.acgbox.mapper.TagRelationMapper;
import org.killze.acgbox.service.AdaptationTypeService;
import org.killze.acgbox.service.BookTypeService;
import org.killze.acgbox.service.BroadcastTypeService;
import org.killze.acgbox.service.impl.CatalogNameInitialPage;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.TagVO;
import org.killze.acgbox.web.dto.PublicCatalogPageDTO;
import org.killze.acgbox.web.mapper.PublicCatalogMapper;
import org.killze.acgbox.web.mapper.PublicCatalogMapper.RelationRow;
import org.killze.acgbox.web.mapper.PublicCatalogMapper.WorkRow;
import org.killze.acgbox.web.vo.PublicCatalogVO.Anime;
import org.killze.acgbox.web.vo.PublicCatalogVO.AnimeFilters;
import org.killze.acgbox.web.vo.PublicCatalogVO.Book;
import org.killze.acgbox.web.vo.PublicCatalogVO.BookFilters;
import org.killze.acgbox.web.vo.PublicCatalogVO.Company;
import org.killze.acgbox.web.vo.PublicCatalogVO.Option;
import org.killze.acgbox.web.vo.PublicCatalogVO.Person;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 用户端目录仅从已公开的分页结果组装展示字段。 */
@Service
public class PublicCatalogService {
    private static final List<Option> ANIME_STATUSES = List.of(
            new Option(1L, "未放送"), new Option(2L, "放送中"), new Option(3L, "已完结"), new Option(4L, "其他"));
    private static final List<Option> BOOK_STATUSES = List.of(new Option(0L, "其他"), new Option(1L, "未发表"),
            new Option(2L, "连载中"), new Option(3L, "已完结"), new Option(4L, "暂停中"), new Option(5L, "已终止"));

    private final PublicCatalogMapper catalogMapper;
    private final AliasMapper aliasMapper;
    private final TagRelationMapper tagRelationMapper;
    private final TagMapper tagMapper;
    private final AdaptationTypeService adaptationTypeService;
    private final BroadcastTypeService broadcastTypeService;
    private final BookTypeService bookTypeService;

    public PublicCatalogService(PublicCatalogMapper catalogMapper, AliasMapper aliasMapper, TagRelationMapper tagRelationMapper,
                                TagMapper tagMapper, AdaptationTypeService adaptationTypeService,
                                BroadcastTypeService broadcastTypeService, BookTypeService bookTypeService) {
        this.catalogMapper = catalogMapper;
        this.aliasMapper = aliasMapper;
        this.tagRelationMapper = tagRelationMapper;
        this.tagMapper = tagMapper;
        this.adaptationTypeService = adaptationTypeService;
        this.broadcastTypeService = broadcastTypeService;
        this.bookTypeService = bookTypeService;
    }

    /** 动画使用开播日期和放送类型，不返回管理字段。 */
    public PageVO<Anime> pageAnime(PublicCatalogPageDTO dto) {
        return page(dto, true, (w, r) -> new Anime(w.id(), w.name(), r.aliases(), w.coverImageUrl(), w.status(), w.workDate(),
                option(w.typeId(), w.typeName()), option(w.adaptationTypeId(), w.adaptationTypeName()), r.tags(), r.persons(), r.companies()));
    }

    /** 书籍使用发表日期和书籍类型，不返回管理字段。 */
    public PageVO<Book> pageBook(PublicCatalogPageDTO dto) {
        return page(dto, false, (w, r) -> new Book(w.id(), w.name(), r.aliases(), w.coverImageUrl(), w.status(), w.workDate(),
                option(w.typeId(), w.typeName()), option(w.adaptationTypeId(), w.adaptationTypeName()), r.tags(), r.persons(), r.companies()));
    }

    /** 复用字典排序，年份和标签限定为公开动画。 */
    public AnimeFilters animeFilters() {
        var types = broadcastTypeService.listBroadcastTypes().stream().map(v -> new Option(v.getId(), v.getName())).toList();
        var years = catalogMapper.selectYears(true);
        return new AnimeFilters(types, adaptations(), ANIME_STATUSES, years.stream().filter(Objects::nonNull).toList(),
                years.contains(null), CatalogNameInitialPage.tagViews(catalogMapper.selectFilterTags(true).stream()));
    }

    /** 复用字典排序，年份和标签限定为公开书籍。 */
    public BookFilters bookFilters() {
        var types = bookTypeService.listBookTypes().stream().map(v -> new Option(v.getId(), v.getName())).toList();
        var years = catalogMapper.selectYears(false);
        return new BookFilters(types, adaptations(), BOOK_STATUSES, years.stream().filter(Objects::nonNull).toList(),
                years.contains(null), CatalogNameInitialPage.tagViews(catalogMapper.selectFilterTags(false).stream()));
    }

    /** 先在数据库筛选、计数和分页，再按类型批量查询当前页的摘要。 */
    private <T> PageVO<T> page(PublicCatalogPageDTO dto, boolean anime, BiFunction<WorkRow, Relations, T> view) {
        Year year = validate(dto, anime);
        String keyword = dto.getKeyword() == null ? "" : dto.getKeyword().strip();
        String pattern = keyword.isEmpty() ? null : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var page = catalogMapper.selectPage(new Page<>(dto.getPageNum(), dto.getPageSize()), anime, dto, pattern,
                year == null ? null : year.atDay(1), year == null ? null : year.atMonth(12).atEndOfMonth());
        List<T> rows = page.getRecords().isEmpty() ? List.of()
                : buildRows(page.getRecords(), anime ? "ANIME" : "BOOK", view);
        return PageVO.<T>builder().pageNum(page.getCurrent()).pageSize(page.getSize()).total(page.getTotal())
                .pages(page.getPages()).rows(rows).build();
    }

    /** 多条关系只按当前页作品 ID 查询，不逐部读取详情。 */
    private <T> List<T> buildRows(List<WorkRow> works, String type, BiFunction<WorkRow, Relations, T> view) {
        var ids = works.stream().map(WorkRow::id).toList();
        var aliases = aliasMapper.selectList(new LambdaQueryWrapper<Alias>().eq(Alias::getTargetType, type)
                .in(Alias::getTargetId, ids).orderByAsc(Alias::getSortOrder).orderByAsc(Alias::getId)).stream()
                .collect(Collectors.groupingBy(Alias::getTargetId, Collectors.mapping(Alias::getAliasName, Collectors.toList())));
        var persons = catalogMapper.selectPersons(type, ids).stream().collect(Collectors.groupingBy(RelationRow::workId,
                Collectors.mapping(r -> new Person(r.id(), r.name(), r.role()), Collectors.toList())));
        var companies = catalogMapper.selectCompanies(type, ids).stream().collect(Collectors.groupingBy(RelationRow::workId,
                Collectors.mapping(r -> new Company(r.id(), r.name(), r.role()), Collectors.toList())));
        var tagRelations = tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelation>()
                .eq(TagRelation::getTargetType, type).in(TagRelation::getTargetId, ids));
        var tagIds = tagRelations.stream().map(TagRelation::getTagId).collect(Collectors.toSet());
        var tags = (tagIds.isEmpty() ? List.<Tag>of() : tagMapper.selectByIds(tagIds)).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        var tagsByWork = tagRelations.stream().collect(Collectors.groupingBy(TagRelation::getTargetId,
                Collectors.mapping(rel -> tags.get(rel.getTagId()), Collectors.toList())));
        return works.stream().map(w -> view.apply(w, new Relations(aliases.getOrDefault(w.id(), List.of()),
                CatalogNameInitialPage.tagViews(tagsByWork.getOrDefault(w.id(), List.of()).stream().filter(Objects::nonNull)),
                persons.getOrDefault(w.id(), List.of()), companies.getOrDefault(w.id(), List.of())))).toList();
    }

    /** 校验分页和字典 ID，解析年份并为排序与多标签补齐默认语义。 */
    private Year validate(PublicCatalogPageDTO dto, boolean anime) {
        if (dto.getPageNum() == null || dto.getPageNum() < 1) throw new BusinessException("pageNum 必须大于等于 1");
        if (dto.getPageSize() == null || dto.getPageSize() < 1 || dto.getPageSize() > 100) {
            throw new BusinessException("pageSize 必须在 1 到 100 之间");
        }
        validateId(dto.getAdaptationTypeId(), "adaptationTypeId");
        validateId(dto.getBroadcastTypeId(), "broadcastTypeId");
        validateId(dto.getBookTypeId(), "bookTypeId");
        if (dto.getStatus() != null && (anime ? ANIME_STATUSES : BOOK_STATUSES).stream().noneMatch(s -> s.id().equals(dto.getStatus()))) {
            throw new BusinessException("status 不支持此值");
        }
        if (dto.getTagIds() != null) {
            if (dto.getTagIds().stream().anyMatch(Objects::isNull)) throw new BusinessException("tagIds 不能为空项");
            dto.setTagIds(dto.getTagIds().stream().distinct().toList());
            dto.getTagIds().forEach(id -> validateId(id, "tagIds"));
        }
        if (dto.getSortBy() != null && !Set.of("DATE", "NAME").contains(dto.getSortBy())) {
            throw new BusinessException("sortBy 仅支持 DATE 或 NAME");
        }
        if (dto.getSortDirection() != null && !Set.of("ASC", "DESC").contains(dto.getSortDirection())) {
            throw new BusinessException("sortDirection 仅支持 ASC 或 DESC");
        }
        String year = dto.getYear();
        if (year == null || "UNKNOWN".equals(year)) return null;
        if (!year.matches("[0-9]{4}") || "0000".equals(year)) throw new BusinessException("year 必须为四位年份或 UNKNOWN");
        return Year.parse(year);
    }

    /** 字典与标签使用 PostgreSQL 正整数主键。 */
    private void validateId(Long id, String field) {
        if (id != null && (id < 1 || id > Integer.MAX_VALUE)) throw new BusinessException(field + " 必须为有效的正整数 ID");
    }

    /** 公开字典只输出选项 ID 和名称。 */
    private List<Option> adaptations() {
        return adaptationTypeService.listAdaptationTypes().stream().map(v -> new Option(v.getId(), v.getName())).toList();
    }

    /** 缺少字典记录时保留空值。 */
    private Option option(Long id, String name) {
        return id == null ? null : new Option(id, name);
    }

    /** 一部作品的展示关联，不带内部排序和后台字段。 */
    private record Relations(List<String> aliases, List<TagVO> tags, List<Person> persons, List<Company> companies) { }
}
