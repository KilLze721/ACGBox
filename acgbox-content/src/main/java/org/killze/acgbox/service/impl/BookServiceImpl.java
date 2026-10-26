package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.content.*;
import org.killze.acgbox.entity.content.*;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.*;
import org.killze.acgbox.service.BookService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 书籍完整资料及关联管理，所有通用关联限定 BOOK，系列条目限定 book。
 */
@Service
public class BookServiceImpl implements BookService {
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private BookTypeMapper bookTypeMapper;
    @Autowired
    private AdaptationTypeMapper adaptationTypeMapper;
    @Autowired
    private RegionMapper regionMapper;
    @Autowired
    private AliasMapper aliasMapper;
    @Autowired
    private CompanyMapper companyMapper;
    @Autowired
    private CompanyRelationMapper companyRelationMapper;
    @Autowired
    private PersonMapper personMapper;
    @Autowired
    private PersonRelationMapper personRelationMapper;
    @Autowired
    private ExternalLinkMapper externalLinkMapper;
    @Autowired
    private TagMapper tagMapper;
    @Autowired
    private TagRelationMapper tagRelationMapper;
    @Autowired
    private SeriesMapper seriesMapper;
    @Autowired
    private SeriesItemMapper seriesItemMapper;
    @Autowired
    private PersonalRatingMapper personalRatingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookVO createBook(BookDTO dto) {
        PreparedBook prepared = prepare(dto, null);
        Long seriesId = resolveSeries(dto, prepared.book().getName(), true);
        bookMapper.insert(prepared.book());
        saveRelations(prepared.book().getId(), prepared);
        saveSeries(prepared.book().getId(), seriesId, seriesId == null || dto.getSeriesId() == null
                ? 0L : order(dto.getSeriesSortOrder(), 0L));
        saveRating(prepared.book().getId(), dto.getPersonalRatingScore());
        return getBookById(prepared.book().getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookVO updateBook(BookDTO dto) {
        Book existing = getBook(dto.getId());
        PreparedBook prepared = prepare(dto, existing.getId());
        Book book = prepared.book();
        book.setId(existing.getId());
        Long seriesId = resolveSeries(dto, book.getName(), false);
        // 显式设置可空基础字段，不能使用默认忽略 null 的更新策略。
        int changed = bookMapper.update(null, new LambdaUpdateWrapper<Book>().eq(Book::getId, book.getId())
                .set(Book::getName, book.getName()).set(Book::getBookTypeId, book.getBookTypeId())
                .set(Book::getAdaptationTypeId, book.getAdaptationTypeId()).set(Book::getRegionId, book.getRegionId())
                .set(Book::getPublishDate, book.getPublishDate()).set(Book::getCoverImageUrl, book.getCoverImageUrl())
                .set(Book::getStatus, book.getStatus()).set(Book::getVolumeCount, book.getVolumeCount())
                .set(Book::getChapterCount, book.getChapterCount()).set(Book::getDescription, book.getDescription())
                .set(Book::getUpdatedAt, book.getUpdatedAt()));
        if (changed == 0) {
            throw new BusinessException("此书籍不存在");
        }
        deleteRelations(List.of(book.getId()), prepared.persons() != null);
        saveRelations(book.getId(), prepared);
        saveSeries(book.getId(), seriesId, order(dto.getSeriesSortOrder(), 0L));
        saveRating(book.getId(), dto.getPersonalRatingScore());
        return getBookById(book.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBooks(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的书籍");
        }
        ids.forEach(id -> range(id, "书籍 ID", 1L, 2147483647L, true));
        List<Long> targets = ids.stream().distinct().toList();
        if (bookMapper.selectByIds(targets).size() != targets.size()) {
            throw new BusinessException("所选书籍不存在，请刷新后重试");
        }
        deleteRelations(targets, true);
        personalRatingMapper.delete(new LambdaQueryWrapper<PersonalRating>()
                .eq(PersonalRating::getTargetType, "BOOK").in(PersonalRating::getTargetId, targets));
        seriesItemMapper.delete(new LambdaQueryWrapper<SeriesItem>()
                .eq(SeriesItem::getWorkType, "book").in(SeriesItem::getWorkId, targets));
        if (bookMapper.deleteByIds(targets) != targets.size()) {
            throw new BusinessException("所选书籍已发生变化，请刷新后重试");
        }
    }

    @Override
    public BookVO getBookById(Long id) {
        return loadViews(List.of(getBook(id))).getFirst();
    }

    @Override
    public PageVO<BookPageVO> pageBook(BookPageDTO dto) {
        normalizePage(dto);
        LocalDate start = dateBound(dto.getPublishStartDate(), false);
        LocalDate end = dateBound(dto.getPublishEndDate(), true);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException("publishStartDate 不能晚于 publishEndDate");
        }
        var page = bookMapper.selectBookPage(new Page<>(dto.getPageNum(), dto.getPageSize()), dto, start, end);
        return PageVO.<BookPageVO>builder().pageNum(page.getCurrent()).pageSize(page.getSize())
                .total(page.getTotal()).pages(page.getPages()).rows(pageViews(page.getRecords())).build();
    }

    /**
     * 校验和清洗全部输入后才开始写入，persons 的 null 专门表示更新时保留。
     */
    private PreparedBook prepare(BookDTO dto, Long bookId) {
        range(dto.getId(), "id", 1L, 2147483647L, false);
        range(dto.getBookTypeId(), "bookTypeId", 1L, 2147483647L, true);
        range(dto.getAdaptationTypeId(), "adaptationTypeId", 1L, 2147483647L, true);
        range(dto.getRegionId(), "regionId", 1L, 2147483647L, true);
        if (bookTypeMapper.selectById(dto.getBookTypeId()) == null
                || adaptationTypeMapper.selectById(dto.getAdaptationTypeId()) == null
                || regionMapper.selectById(dto.getRegionId()) == null) {
            throw new BusinessException("所选书籍类型、改编类型或地区不存在");
        }
        range(dto.getStatus(), "status", 0L, 5L, true);
        range(dto.getVolumeCount(), "volumeCount", 0L, 2147483647L, false);
        range(dto.getChapterCount(), "chapterCount", 0L, 2147483647L, false);
        range(dto.getPersonalRatingScore(), "personalRatingScore", 0L, 10L, false);
        range(dto.getSeriesId(), "seriesId", 1L, 2147483647L, false);
        range(dto.getSeriesSortOrder(), "seriesSortOrder", -2147483648L, 2147483647L, false);
        if (dto.getPublishDate() != null && (dto.getPublishDate().getYear() < 1 || dto.getPublishDate().getYear() > 9999)) {
            throw new BusinessException("发表日期必须是有效完整日期 yyyy-MM-dd");
        }
        LocalDateTime now = LocalDateTime.now();
        Book book = Book.builder().name(text(dto.getName(), "书名", 200, true))
                .bookTypeId(dto.getBookTypeId()).adaptationTypeId(dto.getAdaptationTypeId()).regionId(dto.getRegionId())
                .publishDate(dto.getPublishDate()).coverImageUrl(text(dto.getCoverImageUrl(), "封面 URL", 500, false))
                .status(dto.getStatus()).volumeCount(dto.getVolumeCount()).chapterCount(dto.getChapterCount())
                .description(StringUtils.hasText(dto.getDescription()) ? dto.getDescription() : null)
                .createdAt(now).updatedAt(now).build();
        List<String> aliases = dto.getAliasNames() == null ? List.of() : dto.getAliasNames().stream()
                .filter(StringUtils::hasText).map(value -> text(value, "别名", 200, true)).distinct().toList();
        List<Long> tags = dto.getTagIds() == null ? List.of() : dto.getTagIds().stream().distinct().toList();
        tags.forEach(id -> range(id, "tagId", 1L, 2147483647L, true));
        if (!tags.isEmpty() && tagMapper.selectByIds(tags).size() != tags.size()) {
            throw new BusinessException("所选标签不存在");
        }
        List<PersonRelationDTO> persons = bookId != null && dto.getPersons() == null ? null : cleanPersons(dto.getPersons());
        return new PreparedBook(book, aliases, cleanCompanies(dto.getCompanies(), bookId), persons,
                cleanLinks(dto.getExternalLinks()), tags);
    }

    private List<CompanyRelationDTO> cleanCompanies(List<BookCompanyDTO> input, Long bookId) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> previous = bookId == null ? Map.of() : companyRelationMapper.selectList(
                new LambdaQueryWrapper<CompanyRelation>().eq(CompanyRelation::getTargetType, "BOOK")
                        .eq(CompanyRelation::getTargetId, bookId)).stream()
                .collect(Collectors.toMap(CompanyRelation::getCompanyId, CompanyRelation::getSortOrder));
        Map<Long, CompanyRelationDTO> result = new LinkedHashMap<>();
        for (BookCompanyDTO company : input) {
            if (company == null) {
                throw new BusinessException("公司关联项不能为空");
            }
            range(company.getCompanyId(), "companyId", 1L, 2147483647L, true);
            if (result.containsKey(company.getCompanyId())) {
                continue;
            }
            CompanyRelationDTO value = new CompanyRelationDTO();
            value.setCompanyId(company.getCompanyId());
            value.setRole(text(company.getRole(), "公司职责", 50, false));
            value.setSortOrder(order(company.getSortOrder(), previous.getOrDefault(company.getCompanyId(), (long) result.size())));
            result.put(value.getCompanyId(), value);
        }
        if (companyMapper.selectByIds(result.keySet()).size() != result.size()) {
            throw new BusinessException("所选公司不存在");
        }
        return new ArrayList<>(result.values());
    }

    private List<PersonRelationDTO> cleanPersons(List<BookPersonDTO> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<PersonRelationDTO> result = new ArrayList<>();
        Set<PersonRole> roles = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (BookPersonDTO person : input) {
            if (person == null) {
                throw new BusinessException("人物关联项不能为空");
            }
            range(person.getPersonId(), "personId", 1L, 2147483647L, true);
            String role = text(person.getRole(), "人物职责", 50, true);
            if (!roles.add(new PersonRole(person.getPersonId(), role))) {
                throw new BusinessException("同一人物的相同职责不能重复添加");
            }
            long sort = order(person.getSortOrder(), (long) result.size());
            range(sort, "人物排序", 0L, 2147483647L, true);
            PersonRelationDTO value = new PersonRelationDTO();
            value.setPersonId(person.getPersonId());
            value.setRole(role);
            value.setSortOrder(sort);
            result.add(value);
            ids.add(person.getPersonId());
        }
        if (personMapper.selectByIds(ids).size() != ids.size()) {
            throw new BusinessException("所选人物不存在");
        }
        return result;
    }

    private List<ExternalLinkDTO> cleanLinks(List<BookExternalLinkDTO> input) {
        Map<String, ExternalLinkDTO> result = new LinkedHashMap<>();
        if (input == null) {
            return List.of();
        }
        for (BookExternalLinkDTO link : input) {
            if (link == null) {
                throw new BusinessException("外链项不能为空");
            }
            String url = text(link.getUrl(), "外链 URL", 500, true);
            try {
                URI uri = URI.create(url);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("外链必须是有效的 http 或 https 地址");
            }
            if (result.containsKey(url)) {
                continue;
            }
            ExternalLinkDTO value = new ExternalLinkDTO();
            String title = text(link.getTitle(), "外链标题", 100, false);
            value.setTitle(title == null ? "" : title);
            value.setUrl(url);
            value.setSortOrder(order(link.getSortOrder(), (long) result.size()));
            result.put(url, value);
        }
        return new ArrayList<>(result.values());
    }

    private Long resolveSeries(BookDTO dto, String name, boolean create) {
        if (dto.getSeriesId() != null) {
            if (seriesMapper.selectById(dto.getSeriesId()) == null) {
                throw new BusinessException("所选系列不存在");
            }
            return dto.getSeriesId();
        }
        if (!create || !Boolean.TRUE.equals(dto.getAutoCreateSeries())) {
            return null;
        }
        List<Series> matches = seriesMapper.selectList(new LambdaQueryWrapper<Series>().eq(Series::getName, name));
        if (matches.size() > 1) {
            throw new BusinessException("存在多个同名系列，请按 ID 手动选择系列");
        }
        if (!matches.isEmpty()) {
            return matches.getFirst().getId();
        }
        LocalDateTime now = LocalDateTime.now();
        Series series = Series.builder().name(name).createdAt(now).updatedAt(now).build();
        seriesMapper.insert(series);
        return series.getId();
    }

    private void saveRelations(Long id, PreparedBook value) {
        for (int i = 0; i < value.aliases().size(); i++) {
            aliasMapper.insert(Alias.builder().targetType("BOOK").targetId(id)
                    .aliasName(value.aliases().get(i)).sortOrder((long) i).build());
        }
        value.companies().forEach(company -> companyRelationMapper.insert(CompanyRelation.builder()
                .targetType("BOOK").targetId(id).companyId(company.getCompanyId())
                .role(company.getRole()).sortOrder(company.getSortOrder()).build()));
        if (value.persons() != null) {
            value.persons().forEach(person -> personRelationMapper.insert(PersonRelation.builder()
                    .targetType("BOOK").targetId(id).personId(person.getPersonId())
                    .role(person.getRole()).sortOrder(person.getSortOrder()).build()));
        }
        value.links().forEach(link -> externalLinkMapper.insert(ExternalLink.builder()
                .targetType("BOOK").targetId(id).title(link.getTitle()).url(link.getUrl()).sortOrder(link.getSortOrder()).build()));
        value.tags().forEach(tag -> tagRelationMapper.insert(TagRelation.builder().targetType("BOOK").targetId(id).tagId(tag).build()));
    }

    private void deleteRelations(List<Long> ids, boolean persons) {
        aliasMapper.delete(new LambdaQueryWrapper<Alias>().eq(Alias::getTargetType, "BOOK").in(Alias::getTargetId, ids));
        companyRelationMapper.delete(new LambdaQueryWrapper<CompanyRelation>()
                .eq(CompanyRelation::getTargetType, "BOOK").in(CompanyRelation::getTargetId, ids));
        externalLinkMapper.delete(new LambdaQueryWrapper<ExternalLink>()
                .eq(ExternalLink::getTargetType, "BOOK").in(ExternalLink::getTargetId, ids));
        tagRelationMapper.delete(new LambdaQueryWrapper<TagRelation>()
                .eq(TagRelation::getTargetType, "BOOK").in(TagRelation::getTargetId, ids));
        if (persons) {
            personRelationMapper.delete(new LambdaQueryWrapper<PersonRelation>()
                    .eq(PersonRelation::getTargetType, "BOOK").in(PersonRelation::getTargetId, ids));
        }
    }

    private void saveSeries(Long id, Long seriesId, Long sortOrder) {
        seriesItemMapper.delete(new LambdaQueryWrapper<SeriesItem>().eq(SeriesItem::getWorkType, "book").eq(SeriesItem::getWorkId, id));
        if (seriesId != null) {
            seriesItemMapper.insert(SeriesItem.builder().workType("book").workId(id).seriesId(seriesId).sortOrder(sortOrder).build());
        }
    }

    private void saveRating(Long id, Long score) {
        if (score == null) {
            personalRatingMapper.delete(new LambdaQueryWrapper<PersonalRating>()
                    .eq(PersonalRating::getTargetType, "BOOK").eq(PersonalRating::getTargetId, id));
            return;
        }
        PersonalRating value = personalRatingMapper.selectOne(new LambdaQueryWrapper<PersonalRating>()
                .eq(PersonalRating::getTargetType, "BOOK").eq(PersonalRating::getTargetId, id));
        LocalDateTime now = LocalDateTime.now();
        if (value == null) {
            personalRatingMapper.insert(PersonalRating.builder().targetType("BOOK").targetId(id)
                    .score(BigDecimal.valueOf(score)).createdAt(now).updatedAt(now).build());
        } else {
            value.setScore(BigDecimal.valueOf(score));
            value.setUpdatedAt(now);
            personalRatingMapper.updateById(value);
        }
    }

    /**
     * 详情与写入响应共用批量聚合，所有查询都同时限制类型和目标 ID。
     */
    private List<BookVO> loadViews(List<Book> books) {
        if (books.isEmpty()) {
            return List.of();
        }
        List<Long> ids = books.stream().map(Book::getId).toList();
        var aliases = group(aliasMapper.selectList(new LambdaQueryWrapper<Alias>().eq(Alias::getTargetType, "BOOK")
                .in(Alias::getTargetId, ids).orderByAsc(Alias::getSortOrder).orderByAsc(Alias::getId)), Alias::getTargetId);
        var companies = group(companyRelationMapper.selectList(new LambdaQueryWrapper<CompanyRelation>()
                .eq(CompanyRelation::getTargetType, "BOOK").in(CompanyRelation::getTargetId, ids)
                .orderByAsc(CompanyRelation::getSortOrder).orderByAsc(CompanyRelation::getId)), CompanyRelation::getTargetId);
        List<PersonRelation> personRelations = personRelationMapper.selectList(new LambdaQueryWrapper<PersonRelation>()
                .eq(PersonRelation::getTargetType, "BOOK").in(PersonRelation::getTargetId, ids)
                .orderByAsc(PersonRelation::getSortOrder).orderByAsc(PersonRelation::getId));
        Set<Long> personIds = personRelations.stream().map(PersonRelation::getPersonId).collect(Collectors.toSet());
        var people = entityMap(personIds.isEmpty() ? List.<Person>of() : personMapper.selectByIds(personIds), Person::getId);
        var persons = group(personRelations, PersonRelation::getTargetId);
        var links = group(externalLinkMapper.selectList(new LambdaQueryWrapper<ExternalLink>()
                .eq(ExternalLink::getTargetType, "BOOK").in(ExternalLink::getTargetId, ids)
                .orderByAsc(ExternalLink::getSortOrder).orderByAsc(ExternalLink::getId)), ExternalLink::getTargetId);
        var tags = group(tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelation>()
                .eq(TagRelation::getTargetType, "BOOK").in(TagRelation::getTargetId, ids).orderByAsc(TagRelation::getId)), TagRelation::getTargetId);
        var series = group(seriesItemMapper.selectList(new LambdaQueryWrapper<SeriesItem>()
                .eq(SeriesItem::getWorkType, "book").in(SeriesItem::getWorkId, ids)
                .orderByAsc(SeriesItem::getSortOrder).orderByAsc(SeriesItem::getId)), SeriesItem::getWorkId);
        var ratings = entityMap(personalRatingMapper.selectList(new LambdaQueryWrapper<PersonalRating>()
                .eq(PersonalRating::getTargetType, "BOOK").in(PersonalRating::getTargetId, ids)), PersonalRating::getTargetId);
        return books.stream().map(book -> {
            Long id = book.getId();
            List<SeriesItem> items = series.getOrDefault(id, List.of());
            PersonalRating rating = ratings.get(id);
            return BookVO.builder().id(id).name(book.getName()).bookTypeId(book.getBookTypeId())
                    .adaptationTypeId(book.getAdaptationTypeId()).regionId(book.getRegionId()).publishDate(book.getPublishDate())
                    .coverImageUrl(book.getCoverImageUrl()).status(book.getStatus()).volumeCount(book.getVolumeCount())
                    .chapterCount(book.getChapterCount()).description(book.getDescription()).createdAt(book.getCreatedAt()).updatedAt(book.getUpdatedAt())
                    .aliasNames(aliases.getOrDefault(id, List.of()).stream().map(Alias::getAliasName).toList())
                    .companies(companies.getOrDefault(id, List.of()).stream().map(value -> CompanyRelationVO.builder()
                            .companyId(value.getCompanyId()).role(value.getRole()).sortOrder(value.getSortOrder()).build()).toList())
                    .persons(persons.getOrDefault(id, List.of()).stream().filter(value -> people.containsKey(value.getPersonId()))
                            .map(value -> PersonRelationVO.builder().personId(value.getPersonId()).personName(people.get(value.getPersonId()).getName())
                                    .role(value.getRole()).sortOrder(value.getSortOrder()).build()).toList())
                    .externalLinks(links.getOrDefault(id, List.of()).stream().map(value -> ExternalLinkVO.builder()
                            .title(value.getTitle()).url(value.getUrl()).sortOrder(value.getSortOrder()).build()).toList())
                    .tagIds(tags.getOrDefault(id, List.of()).stream().map(TagRelation::getTagId).toList())
                    .seriesId(items.isEmpty() ? null : items.getFirst().getSeriesId())
                    .seriesSortOrder(items.isEmpty() ? null : items.getFirst().getSortOrder())
                    .personalRatingScore(rating == null ? null : integerScore(rating.getScore())).build();
        }).toList();
    }

    private List<BookPageVO> pageViews(List<Book> books) {
        List<BookVO> views = loadViews(books);
        if (views.isEmpty()) {
            return List.of();
        }
        Set<Long> typeIds = books.stream().map(Book::getBookTypeId).collect(Collectors.toSet());
        Set<Long> adaptationIds = books.stream().map(Book::getAdaptationTypeId).collect(Collectors.toSet());
        Set<Long> regionIds = books.stream().map(Book::getRegionId).collect(Collectors.toSet());
        Set<Long> tagIds = views.stream().flatMap(value -> value.getTagIds().stream()).collect(Collectors.toSet());
        Set<Long> companyIds = views.stream().flatMap(value -> value.getCompanies().stream()).map(CompanyRelationVO::getCompanyId).collect(Collectors.toSet());
        Set<Long> seriesIds = views.stream().map(BookVO::getSeriesId).filter(Objects::nonNull).collect(Collectors.toSet());
        var types = entityMap(bookTypeMapper.selectByIds(typeIds), BookType::getId);
        var adaptations = entityMap(adaptationTypeMapper.selectByIds(adaptationIds), AdaptationType::getId);
        var regions = entityMap(regionMapper.selectByIds(regionIds), Region::getId);
        var tags = entityMap(tagIds.isEmpty() ? List.<Tag>of() : tagMapper.selectByIds(tagIds), Tag::getId);
        var companies = entityMap(companyIds.isEmpty() ? List.<Company>of() : companyMapper.selectByIds(companyIds), Company::getId);
        var series = entityMap(seriesIds.isEmpty() ? List.<Series>of() : seriesMapper.selectByIds(seriesIds), Series::getId);
        return views.stream().map(value -> {
            BookType type = types.get(value.getBookTypeId());
            AdaptationType adaptation = adaptations.get(value.getAdaptationTypeId());
            Region region = regions.get(value.getRegionId());
            Series item = series.get(value.getSeriesId());
            return BookPageVO.builder().id(value.getId()).name(value.getName()).status(value.getStatus())
                    .publishDate(value.getPublishDate()).coverImageUrl(value.getCoverImageUrl()).volumeCount(value.getVolumeCount())
                    .chapterCount(value.getChapterCount()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt())
                    .bookType(type == null ? null : BookTypeVO.builder().id(type.getId()).name(type.getName()).sortOrder(type.getSortOrder()).build())
                    .adaptationType(adaptation == null ? null : AdaptationTypeVO.builder().id(adaptation.getId()).name(adaptation.getName()).sortOrder(adaptation.getSortOrder()).build())
                    .region(region == null ? null : RegionVO.builder().id(region.getId()).name(region.getName()).build())
                    .aliasNames(value.getAliasNames()).persons(value.getPersons()).externalLinks(value.getExternalLinks())
                    .tags(value.getTagIds().stream().filter(tags::containsKey).map(id -> TagVO.builder().id(id).name(tags.get(id).getName()).build()).toList())
                    .companies(value.getCompanies().stream().filter(company -> companies.containsKey(company.getCompanyId()))
                            .map(company -> BookCompanyVO.builder().companyId(company.getCompanyId()).companyName(companies.get(company.getCompanyId()).getName())
                                    .role(company.getRole()).sortOrder(company.getSortOrder()).build()).toList())
                    .series(item == null ? null : SeriesVO.builder().id(item.getId()).name(item.getName()).description(item.getDescription()).build())
                    .personalRating(value.getPersonalRatingScore()).build();
        }).toList();
    }

    private void normalizePage(BookPageDTO dto) {
        if (dto.getPageNum() == null) {
            dto.setPageNum(1L);
        }
        if (dto.getPageSize() == null) {
            dto.setPageSize(20L);
        }
        range(dto.getPageNum(), "pageNum", 1L, Long.MAX_VALUE, true);
        range(dto.getPageSize(), "pageSize", 1L, 100L, true);
        range(dto.getBookTypeId(), "bookTypeId", 1L, 2147483647L, false);
        range(dto.getAdaptationTypeId(), "adaptationTypeId", 1L, 2147483647L, false);
        range(dto.getRegionId(), "regionId", 1L, 2147483647L, false);
        range(dto.getCompanyId(), "companyId", 1L, 2147483647L, false);
        range(dto.getPersonId(), "personId", 1L, 2147483647L, false);
        range(dto.getSeriesId(), "seriesId", 1L, 2147483647L, false);
        range(dto.getStatus(), "status", 0L, 5L, false);
        range(dto.getRatingMin(), "ratingMin", 0L, 10L, false);
        range(dto.getRatingMax(), "ratingMax", 0L, 10L, false);
        if (dto.getRatingMin() != null && dto.getRatingMax() != null && dto.getRatingMin() > dto.getRatingMax()) {
            throw new BusinessException("ratingMin 不能大于 ratingMax");
        }
        dto.setKeyword(StringUtils.hasText(dto.getKeyword()) ? dto.getKeyword().trim() : null);
        dto.setTagIds(dto.getTagIds() == null ? List.of() : dto.getTagIds().stream().distinct().toList());
        dto.getTagIds().forEach(id -> range(id, "tagId", 1L, 2147483647L, true));
        dto.setTagMatchMode(option(dto.getTagMatchMode(), "ALL", Set.of("ALL", "ANY"), "tagMatchMode"));
        dto.setSortBy(option(dto.getSortBy(), "PUBLISH_DATE", Set.of("PUBLISH_DATE", "PERSONAL_RATING"), "sortBy"));
        dto.setSortDirection(option(dto.getSortDirection(), "DESC", Set.of("ASC", "DESC"), "sortDirection"));
    }

    private String option(String value, String fallback, Set<String> allowed, String label) {
        String result = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!allowed.contains(result)) {
            throw new BusinessException(label + " 不支持此值");
        }
        return result;
    }

    private LocalDate dateBound(String input, boolean end) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String value = input.trim();
        try {
            if (value.matches("[0-9]{4}")) {
                Year year = Year.parse(value);
                if (year.getValue() > 0) {
                    return end ? year.atMonth(12).atEndOfMonth() : year.atDay(1);
                }
            } else if (value.matches("[0-9]{4}-[0-9]{2}")) {
                YearMonth month = YearMonth.parse(value);
                if (month.getYear() > 0) {
                    return end ? month.atEndOfMonth() : month.atDay(1);
                }
            } else if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                LocalDate date = LocalDate.parse(value);
                if (date.getYear() > 0) {
                    return date;
                }
            }
        } catch (DateTimeException exception) {
            throw new BusinessException("发表日期筛选格式必须为有效的 yyyy、yyyy-MM 或 yyyy-MM-dd");
        }
        throw new BusinessException("发表日期筛选格式必须为有效的 yyyy、yyyy-MM 或 yyyy-MM-dd");
    }

    private Book getBook(Long id) {
        range(id, "书籍 ID", 1L, 2147483647L, true);
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BusinessException("此书籍不存在");
        }
        return book;
    }

    private Long integerScore(BigDecimal score) {
        if (score.stripTrailingZeros().scale() > 0 || score.signum() < 0 || score.compareTo(BigDecimal.TEN) > 0) {
            throw new BusinessException("书籍个人评分数据必须为 0 到 10 的整数");
        }
        return score.longValueExact();
    }

    private long order(Long value, long fallback) {
        range(value, "排序值", -2147483648L, 2147483647L, false);
        return value == null ? fallback : value;
    }

    private void range(Long value, String label, long min, long max, boolean required) {
        if ((value == null && required) || (value != null && (value < min || value > max))) {
            throw new BusinessException(label + " 必须处于 " + min + " 到 " + max + " 之间");
        }
    }

    private String text(String input, String label, int max, boolean required) {
        if (!StringUtils.hasText(input)) {
            if (required) {
                throw new BusinessException(label + "不能为空");
            }
            return null;
        }
        String value = input.trim();
        if (value.codePointCount(0, value.length()) > max) {
            throw new BusinessException(label + "不能超过 " + max + " 字符");
        }
        return value;
    }

    private <T> Map<Long, T> entityMap(List<T> values, Function<T, Long> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private <T> Map<Long, List<T>> group(List<T> values, Function<T, Long> id) {
        return values.stream().collect(Collectors.groupingBy(id, LinkedHashMap::new, Collectors.toList()));
    }

    /** 保存前已清洗的关联快照，避免写入阶段再进行业务校验。 */
    private record PreparedBook(Book book, List<String> aliases, List<CompanyRelationDTO> companies,
                                List<PersonRelationDTO> persons, List<ExternalLinkDTO> links, List<Long> tags) { }
    /** 人物与职责共同判重，同一人员可承担不同职责。 */
    private record PersonRole(Long personId, String role) { }
}
