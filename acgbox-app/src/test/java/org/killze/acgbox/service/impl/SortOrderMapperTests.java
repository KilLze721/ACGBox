package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.entity.content.Alias;
import org.killze.acgbox.entity.content.CompanyRelation;
import org.killze.acgbox.entity.content.Person;
import org.killze.acgbox.entity.content.PersonRelation;
import org.killze.acgbox.dto.content.PersonDTO;
import org.killze.acgbox.dto.content.PersonPageDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.entity.content.Anime;
import org.killze.acgbox.entity.content.*;
import org.killze.acgbox.dto.content.*;
import org.killze.acgbox.mapper.*;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AdaptationTypeMapper;
import org.killze.acgbox.mapper.AliasMapper;
import org.killze.acgbox.mapper.BroadcastTypeMapper;
import org.killze.acgbox.mapper.CompanyRelationMapper;
import org.killze.acgbox.mapper.PersonMapper;
import org.killze.acgbox.mapper.PersonRelationMapper;
import org.killze.acgbox.mapper.AnimeMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 使用独立 SQLite 内存数据库验证真实 MyBatis-Plus 映射及排序 SQL，不读取应用数据源配置。
 */
class SortOrderMapperTests {

    private Connection connection;
    private SqlSession session;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            for (String table : List.of("broadcast_type", "adaptation_type")) {
                statement.execute("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, "
                        + "sort_order INTEGER NOT NULL DEFAULT 0, created_at TEXT, updated_at TEXT)");
            }
            statement.execute("CREATE TABLE alias (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, "
                    + "target_id INTEGER, alias_name TEXT, sort_order INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE company_relation (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, "
                    + "target_id INTEGER, company_id INTEGER, role TEXT, sort_order INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                    + "description TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE person_relation (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT NOT NULL, "
                    + "target_id INTEGER NOT NULL, person_id INTEGER NOT NULL REFERENCES person(id) ON DELETE RESTRICT, "
                    + "role TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0 CHECK(sort_order >= 0), "
                    + "UNIQUE(target_type,target_id,person_id,role))");
            statement.execute("CREATE TABLE anime (id INTEGER PRIMARY KEY, name TEXT, episode_count INTEGER, "
                    + "broadcast_type_id INTEGER, adaptation_type_id INTEGER, region_id INTEGER, air_date TEXT, "
                    + "cover_image_url TEXT, status INTEGER, description TEXT, created_at TEXT, updated_at TEXT)");
            statement.execute("CREATE TABLE personal_rating (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, target_id INTEGER, "
                    + "score NUMERIC, created_at TEXT, updated_at TEXT, "
                    + "UNIQUE(target_type,target_id))");
            statement.execute("CREATE TABLE series (id INTEGER PRIMARY KEY, name TEXT, description TEXT, created_at TEXT, updated_at TEXT)");
            statement.execute("CREATE TABLE series_item (id INTEGER PRIMARY KEY AUTOINCREMENT, series_id INTEGER, "
                    + "work_type TEXT, work_id INTEGER)");
            statement.execute("CREATE TABLE tag_relation (id INTEGER PRIMARY KEY, target_type TEXT, target_id INTEGER, tag_id INTEGER)");
            statement.execute("CREATE TABLE book_type (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, "
                    + "sort_order INTEGER NOT NULL DEFAULT 0 CHECK(sort_order >= 0), created_at TEXT, updated_at TEXT)");
            statement.execute("CREATE TABLE book (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                    + "book_type_id INTEGER NOT NULL REFERENCES book_type(id), adaptation_type_id INTEGER NOT NULL REFERENCES adaptation_type(id), "
                    + "region_id INTEGER NOT NULL REFERENCES region(id), publish_date TEXT, cover_image_url TEXT, status INTEGER NOT NULL, "
                    + "volume_count INTEGER, chapter_count INTEGER, description TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE region (id INTEGER PRIMARY KEY, name TEXT, created_at TEXT, updated_at TEXT)");
            statement.execute("CREATE TABLE company (id INTEGER PRIMARY KEY, name TEXT, description TEXT, created_at TEXT, updated_at TEXT)");
            statement.execute("CREATE TABLE tag (id INTEGER PRIMARY KEY, name TEXT, created_at TEXT, updated_at TEXT)");
            statement.execute("CREATE TABLE external_link (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, target_id INTEGER, "
                    + "title TEXT NOT NULL, url TEXT NOT NULL, sort_order INTEGER NOT NULL)");
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("sort-order-memory", new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        configuration.addMapper(BroadcastTypeMapper.class);
        configuration.addMapper(AdaptationTypeMapper.class);
        configuration.addMapper(AliasMapper.class);
        configuration.addMapper(CompanyRelationMapper.class);
        configuration.addMapper(PersonMapper.class);
        configuration.addMapper(PersonRelationMapper.class);
        configuration.addMapper(AnimeMapper.class);
        for (Class<?> mapper : List.of(BookMapper.class, BookTypeMapper.class, CompanyMapper.class, TagMapper.class,
                TagRelationMapper.class, ExternalLinkMapper.class, PersonalRatingMapper.class, SeriesMapper.class,
                SeriesItemMapper.class, RegionMapper.class)) {
            configuration.addMapper(mapper);
        }
        try (var mapper = Resources.getResourceAsStream("mapper/AnimeMapper.xml")) {
            new XMLMapperBuilder(mapper, configuration, "mapper/AnimeMapper.xml", configuration.getSqlFragments()).parse();
        }
        try (var mapper = Resources.getResourceAsStream("mapper/BookMapper.xml")) {
            new XMLMapperBuilder(mapper, configuration, "mapper/BookMapper.xml", configuration.getSqlFragments()).parse();
        }
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE));
        configuration.addInterceptor(interceptor);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.close();
        }
    }





    @Test
    void personCrudAllowsSameNamesAndClearsDescriptionInActualSql() {
        PersonServiceImpl service = personService();
        PersonDTO dto = new PersonDTO();
        dto.setId(999L);
        dto.setName(" 示例人物 ");
        dto.setDescription("原简介");
        var first = service.createPerson(dto);
        var second = service.createPerson(dto);
        assertNotEquals(999L, first.getId());
        assertNotEquals(first.getId(), second.getId());
        assertEquals("示例人物", first.getName());
        assertNotNull(first.getCreatedAt());
        assertEquals(first.getCreatedAt(), first.getUpdatedAt());
        dto.setId(first.getId());
        dto.setName(" " + "名".repeat(100) + " ");
        dto.setDescription(" \t ");
        service.updatePerson(dto);
        var detail = service.getPersonById(first.getId());
        assertEquals("名".repeat(100), detail.getName());
        assertNull(detail.getDescription());
        assertEquals(first.getCreatedAt(), detail.getCreatedAt());
        assertFalse(detail.getUpdatedAt().isBefore(first.getUpdatedAt()));
        dto.setName("名".repeat(101));
        assertThrows(BusinessException.class, () -> service.updatePerson(dto));
        dto.setName(" ");
        assertThrows(BusinessException.class, () -> service.createPerson(dto));
        dto.setName("正常");
        dto.setId(999L);
        assertThrows(BusinessException.class, () -> service.updatePerson(dto));
    }

    @Test
    void personDeletionChecksWholeBatchAcrossAllTargetTypes() {
        PersonServiceImpl service = personService();
        PersonDTO dto = new PersonDTO();
        dto.setName("同名");
        Long first = service.createPerson(dto).getId();
        Long second = service.createPerson(dto).getId();
        PersonRelationMapper relationMapper = session.getMapper(PersonRelationMapper.class);
        relationMapper.insert(PersonRelation.builder().targetType("BOOK").targetId(8L)
                .personId(first).role("作者").sortOrder(0L).build());
        String message = assertThrows(BusinessException.class,
                () -> service.deletePersons(List.of(first, second))).getMessage();
        assertEquals("所选人物已被作品引用，不能删除，请先解除关联", message);
        assertNotNull(service.getPersonById(second));
        assertThrows(BusinessException.class, () -> service.deletePersons(List.of(second, 999L)));
        assertNotNull(service.getPersonById(second));
        assertThrows(BusinessException.class, () -> service.deletePersons(List.of(0L)));
        assertThrows(BusinessException.class, () -> service.deletePersons(List.of()));
        service.deletePersons(List.of(second, second));
        assertThrows(BusinessException.class, () -> service.getPersonById(second));
        assertNotNull(service.getPersonById(first));
    }

    @Test
    void personDeleteConvertsConcurrentForeignKeyFailureToBusinessError() {
        PersonMapper personMapper = mock(PersonMapper.class);
        PersonRelationMapper relationMapper = mock(PersonRelationMapper.class);
        when(personMapper.selectByIds(List.of(1L))).thenReturn(List.of(Person.builder().id(1L).build()));
        when(personMapper.deleteByIds(List.of(1L))).thenThrow(new DataIntegrityViolationException("外键拒绝删除"));
        PersonServiceImpl service = new PersonServiceImpl();
        ReflectionTestUtils.setField(service, "personMapper", personMapper);
        ReflectionTestUtils.setField(service, "personRelationMapper", relationMapper);
        assertEquals("所选人物已被作品引用，不能删除，请先解除关联",
                assertThrows(BusinessException.class, () -> service.deletePersons(List.of(1L))).getMessage());
    }

    @Test
    void personPageUsesLocalDefaultsStableOrderAndParameterizedSearch() {
        PersonMapper mapper = mock(PersonMapper.class);
        PersonServiceImpl service = personService();
        ReflectionTestUtils.setField(service, "personMapper", mapper);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Person> page = invocation.getArgument(0);
            LambdaQueryWrapper<Person> query = invocation.getArgument(1);
            String sql = query.getSqlSegment();
            assertTrue(sql.endsWith("ORDER BY updated_at DESC,id DESC"));
            assertFalse(sql.contains("OR 1=1"));
            assertTrue(query.getParamNameValuePairs().containsValue("A' OR 1=1 --"));
            assertEquals(1L, page.getCurrent());
            assertEquals(20L, page.getSize());
            page.setRecords(List.of());
            return page;
        });
        PersonPageDTO dto = new PersonPageDTO();
        dto.setName(" A' OR 1=1 -- ");
        dto.setPageNum(null);
        dto.setPageSize(null);
        assertEquals(20L, service.pagePerson(dto).getPageSize());
        dto.setPageNum(0L);
        assertThrows(BusinessException.class, () -> service.pagePerson(dto));
        dto.setPageNum(1L);
        dto.setPageSize(101L);
        assertThrows(BusinessException.class, () -> service.pagePerson(dto));
        dto.setPageSize(0L);
        assertThrows(BusinessException.class, () -> service.pagePerson(dto));
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void personRelationMapsMultipleRolesAndStableOrder() {
        PersonDTO dto = new PersonDTO();
        dto.setName("示例人物");
        Long personId = personService().createPerson(dto).getId();
        PersonRelationMapper mapper = session.getMapper(PersonRelationMapper.class);
        mapper.insert(PersonRelation.builder().targetType("ANIME").targetId(1L).personId(personId)
                .role("导演").sortOrder(1L).build());
        mapper.insert(PersonRelation.builder().targetType("ANIME").targetId(1L).personId(personId)
                .role("脚本").sortOrder(1L).build());
        mapper.insert(PersonRelation.builder().targetType("BOOK").targetId(1L).personId(personId)
                .role("作者").sortOrder(0L).build());
        var rows = mapper.selectList(new LambdaQueryWrapper<PersonRelation>()
                .eq(PersonRelation::getTargetType, "ANIME").in(PersonRelation::getTargetId, List.of(1L))
                .orderByAsc(PersonRelation::getSortOrder).orderByAsc(PersonRelation::getId));
        assertEquals(List.of("导演", "脚本"), rows.stream().map(PersonRelation::getRole).toList());
        assertEquals(List.of(1L, 1L), rows.stream().map(PersonRelation::getSortOrder).toList());
    }

    private PersonServiceImpl personService() {
        PersonServiceImpl service = new PersonServiceImpl();
        ReflectionTestUtils.setField(service, "personMapper", session.getMapper(PersonMapper.class));
        ReflectionTestUtils.setField(service, "personRelationMapper", session.getMapper(PersonRelationMapper.class));
        return service;
    }

    @Test
    void animeAssociationFiltersUseIdsAndPreserveUnfilteredResults() throws Exception {
        seedAnimeAssociations();
        AnimePageDTO dto = new AnimePageDTO();
        assertEquals(List.of(2L, 5L, 1L, 3L, 4L), animeIds(queryAnime(dto)));
        dto.setCompanyId(3L);
        assertEquals(List.of(2L, 1L), animeIds(queryAnime(dto)));
        dto.setCompanyId(null);
        dto.setPersonId(12L);
        assertEquals(List.of(2L, 1L, 4L), animeIds(queryAnime(dto)));
        dto.setPersonId(13L);
        assertEquals(List.of(5L, 3L), animeIds(queryAnime(dto)));
        dto.setPersonId(null);
        dto.setSeriesId(8L);
        assertEquals(List.of(2L, 1L, 4L), animeIds(queryAnime(dto)));
        dto.setSeriesId(9L);
        assertEquals(List.of(5L, 3L), animeIds(queryAnime(dto)));
    }

    @Test
    void multiplePersonRolesDoNotDuplicateRowsOrInflatePagination() throws Exception {
        seedAnimeAssociations();
        AnimePageDTO dto = new AnimePageDTO();
        dto.setPersonId(12L);
        dto.setPageSize(1L);
        for (int i = 0; i < 4; i++) {
            dto.setPageNum((long) i + 1L);
            Page<Anime> page = queryAnime(dto);
            assertEquals(3L, page.getTotal());
            assertEquals(3L, page.getPages());
            assertEquals(i == 3 ? List.of() : List.of(List.of(2L, 1L, 4L).get(i)), animeIds(page));
        }
    }

    @Test
    void associationFiltersCombineWithKeywordAndOtherFiltersUsingAnd() throws Exception {
        seedAnimeAssociations();
        AnimePageDTO dto = new AnimePageDTO();
        dto.setPersonId(12L);
        dto.setSeriesId(8L);
        dto.setCompanyId(3L);
        dto.setKeyword("aBc");
        assertEquals(List.of(2L, 1L), animeIds(queryAnime(dto)));
        dto.setPersonId(13L);
        assertEquals(0L, queryAnime(dto).getTotal());
        dto.setPersonId(12L);
        dto.setKeyword("不存在的名称");
        assertTrue(queryAnime(dto).getRecords().isEmpty());
        dto.setKeyword(null);
        dto.setSeriesId(2147483647L);
        assertEquals(0L, queryAnime(dto).getTotal());
        dto.setSeriesId(null);
        dto.setPersonId(2147483647L);
        assertEquals(0L, queryAnime(dto).getTotal());
        dto.setPersonId(null);
        dto.setCompanyId(2147483647L);
        assertEquals(0L, queryAnime(dto).getTotal());
    }

    @Test
    void associationQueriesKeepDateRatingNullAndStableIdOrdering() throws Exception {
        seedAnimeAssociations();
        AnimePageDTO dto = new AnimePageDTO();
        dto.setPersonId(12L);
        dto.setSeriesId(8L);
        dto.setSortDirection("ASC");
        assertEquals(List.of(1L, 2L, 4L), animeIds(queryAnime(dto)));
        dto.setSortBy("PERSONAL_RATING");
        assertEquals(List.of(1L, 2L, 4L), animeIds(queryAnime(dto)));
        dto.setSortDirection("DESC");
        assertEquals(List.of(2L, 1L, 4L), animeIds(queryAnime(dto)));
        dto.setPersonId(null);
        dto.setSeriesId(null);
        assertEquals(List.of(2L, 5L, 1L, 3L, 4L), animeIds(queryAnime(dto)));
        dto.setSortDirection("ASC");
        assertEquals(List.of(5L, 1L, 2L, 3L, 4L), animeIds(queryAnime(dto)));
    }

    @Test
    void animeAndBookPagesUseDateAndRatingAsMutualSecondarySorts() {
        seedBookCatalogs();
        List<LocalDate> dates = java.util.Arrays.asList(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1),
                null, LocalDate.of(2024, 1, 1), null, LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));
        List<Integer> scores = java.util.Arrays.asList(8, 9, 8, 8, null, null, 8, 0, 0);
        for (int index = 0; index < dates.size(); index++) {
            Long id = (long) index + 1;
            session.getMapper(AnimeMapper.class).insert(Anime.builder().id(id).name("排序动画" + id)
                    .airDate(dates.get(index)).build());
            session.getMapper(BookMapper.class).insert(Book.builder().id(id).name("排序书籍" + id)
                    .bookTypeId(1L).adaptationTypeId(1L).regionId(1L).status(0L).publishDate(dates.get(index))
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
            if (scores.get(index) != null) {
                for (String targetType : List.of("ANIME", "BOOK")) {
                    session.getMapper(PersonalRatingMapper.class).insert(PersonalRating.builder()
                            .targetType(targetType).targetId(id)
                            .score(java.math.BigDecimal.valueOf(scores.get(index))).build());
                }
            }
        }
        List<List<Long>> expectedOrders = List.of(
                List.of(3L, 7L, 1L, 2L, 5L, 9L, 8L, 4L, 6L),
                List.of(9L, 8L, 2L, 7L, 1L, 5L, 3L, 4L, 6L),
                List.of(9L, 8L, 3L, 7L, 1L, 4L, 2L, 5L, 6L),
                List.of(2L, 7L, 1L, 3L, 4L, 9L, 8L, 5L, 6L));
        BookServiceImpl service = bookService();
        int orderIndex = 0;
        for (boolean byRating : List.of(false, true)) {
            for (String direction : List.of("ASC", "DESC")) {
                AnimePageDTO animeQuery = new AnimePageDTO();
                animeQuery.setSortBy(byRating ? "PERSONAL_RATING" : "BROADCAST_DATE");
                animeQuery.setSortDirection(direction);
                BookPageDTO bookQuery = new BookPageDTO();
                bookQuery.setSortBy(byRating ? "PERSONAL_RATING" : "PUBLISH_DATE");
                bookQuery.setSortDirection(direction);
                List<Long> expected = expectedOrders.get(orderIndex++);
                assertEquals(expected, animeIds(queryAnime(animeQuery)));
                assertEquals(expected, bookIds(service.pageBook(bookQuery)));
                animeQuery.setPageSize(3L);
                bookQuery.setPageSize(3L);
                for (long pageNum = 1; pageNum <= 3; pageNum++) {
                    animeQuery.setPageNum(pageNum);
                    bookQuery.setPageNum(pageNum);
                    List<Long> pageIds = expected.subList((int) (pageNum - 1) * 3, (int) pageNum * 3);
                    Page<Anime> animePage = queryAnime(animeQuery);
                    var bookPage = service.pageBook(bookQuery);
                    assertEquals(9L, animePage.getTotal());
                    assertEquals(9L, bookPage.getTotal());
                    assertEquals(pageIds, animeIds(animePage));
                    assertEquals(pageIds, bookIds(bookPage));
                }
            }
        }
    }

    private Page<Anime> queryAnime(AnimePageDTO dto) {
        Page<Anime> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        session.getMapper(AnimeMapper.class).selectAnimePage(page, dto, null, null);
        return page;
    }

    @Test
    void animeAirDateCanBeCreatedEmptyAndClearedToNull() {
        AnimeMapper mapper = session.getMapper(AnimeMapper.class);
        Anime anime = Anime.builder().name("日期清空验证").build();
        mapper.insert(anime);
        assertNull(mapper.selectById(anime.getId()).getAirDate());
        mapper.update(null, new LambdaUpdateWrapper<Anime>().eq(Anime::getId, anime.getId())
                .set(Anime::getAirDate, LocalDate.of(2026, 1, 1)));
        assertEquals(LocalDate.of(2026, 1, 1), mapper.selectById(anime.getId()).getAirDate());
        mapper.update(null, new LambdaUpdateWrapper<Anime>().eq(Anime::getId, anime.getId())
                .set(Anime::getAirDate, null));
        assertNull(mapper.selectById(anime.getId()).getAirDate());
        AnimePageDTO dto = new AnimePageDTO();
        dto.setKeyword("日期清空验证");
        assertNull(queryAnime(dto).getRecords().getFirst().getAirDate());
        Page<Anime> ranged = new Page<>(1, 20);
        mapper.selectAnimePage(ranged, dto, LocalDate.of(2020, 1, 1), null);
        assertEquals(0L, ranged.getTotal());
    }

    private List<Long> animeIds(Page<Anime> page) {
        return page.getRecords().stream().map(Anime::getId).toList();
    }

    @Test
    void booksRoundTripAllRelationsPreserveZeroAndSupportSameNames() {
        seedBookCatalogs();
        BookServiceImpl service = bookService();
        BookDTO dto = bookDTO();
        dto.setAliasNames(java.util.Arrays.asList(" 别名 B ", "", "别名 A", "别名 B", null));
        CompanyRelationDTO company = new CompanyRelationDTO();
        company.setCompanyId(1L);
        company.setRole(" 原版出版 ");
        company.setSortOrder(-2L);
        dto.setCompanies(List.of(company));
        dto.setPersons(List.of(bookPerson(1L, " 作者 ", 1L), bookPerson(1L, "插画", 0L)));
        ExternalLinkDTO link = new ExternalLinkDTO();
        link.setUrl("https://example.com/book");
        dto.setExternalLinks(List.of(link));
        dto.setTagIds(List.of(1L, 1L));
        dto.setAutoCreateSeries(true);
        dto.setPersonalRatingScore(new java.math.BigDecimal("0"));
        var first = service.createBook(dto);
        var second = service.createBook(dto);
        assertNotEquals(first.getId(), second.getId());
        assertEquals("同名书籍", first.getName());
        assertEquals(0L, first.getStatus());
        assertEquals(0L, first.getVolumeCount());
        assertEquals(0L, first.getPersonalRatingScore());
        assertNull(first.getChapterCount());
        assertEquals(List.of("别名 B", "别名 A"), first.getAliasNames());
        assertEquals(List.of("插画", "作者"), first.getPersons().stream().map(value -> value.getRole()).toList());
        assertEquals(-2L, first.getCompanies().getFirst().getSortOrder());
        assertEquals(first.getSeriesId(), second.getSeriesId());
        assertNotNull(first.getSeriesId());
        assertEquals(first, service.getBookById(first.getId()));
        BookPageDTO query = new BookPageDTO();
        query.setPersonId(1L);
        query.setCompanyId(1L);
        query.setSeriesId(first.getSeriesId());
        query.setStatus(0L);
        var page = service.pageBook(query);
        assertEquals(2L, page.getTotal());
        assertEquals(2, page.getRows().getFirst().getPersons().size());
        assertEquals("出版机构", page.getRows().getFirst().getCompanies().getFirst().getCompanyName());
        assertEquals("轻小说", page.getRows().getFirst().getBookType().getName());
        assertEquals(0L, page.getRows().getFirst().getPersonalRating());
    }

    @Test
    void bookUpdateClearsNullableFieldsAndPreservesOmittedPersonsAndCompanySort() {
        seedBookCatalogs();
        BookServiceImpl service = bookService();
        BookDTO dto = bookDTO();
        dto.setPublishDate(LocalDate.of(2026, 1, 1));
        dto.setDescription("简介");
        dto.setCoverImageUrl("https://example.com/cover.jpg");
        dto.setPersonalRatingScore(new java.math.BigDecimal("10"));
        dto.setPersons(List.of(bookPerson(1L, "作者", 0L)));
        CompanyRelationDTO company = new CompanyRelationDTO();
        company.setCompanyId(1L);
        company.setSortOrder(40L);
        dto.setCompanies(List.of(company));
        var created = service.createBook(dto);
        BookDTO update = bookDTO();
        update.setId(created.getId());
        update.setVolumeCount(null);
        company.setSortOrder(null);
        update.setCompanies(List.of(company));
        var changed = service.updateBook(update);
        assertNull(changed.getPublishDate());
        assertNull(changed.getVolumeCount());
        assertNull(changed.getCoverImageUrl());
        assertNull(changed.getDescription());
        assertNull(changed.getPersonalRatingScore());
        assertEquals(1, changed.getPersons().size());
        assertEquals(40L, changed.getCompanies().getFirst().getSortOrder());
        update.setPersons(List.of());
        update.setPersonalRatingScore(new java.math.BigDecimal("0"));
        var cleared = service.updateBook(update);
        assertTrue(cleared.getPersons().isEmpty());
        assertEquals(0L, cleared.getPersonalRatingScore());
    }

    @Test
    void bookTypeDefaultsSortAndProtectsReferencedTypesAndOtherCatalogs() {
        seedBookCatalogs();
        BookTypeServiceImpl types = bookTypeService();
        BookTypeDTO dto = new BookTypeDTO();
        dto.setName(" 漫画 ");
        assertEquals(0L, types.createBookType(dto).getSortOrder());
        dto.setId(2L);
        dto.setSortOrder(5L);
        assertEquals(5L, types.updateBookType(dto).getSortOrder());
        dto.setSortOrder(null);
        assertEquals(5L, types.updateBookType(dto).getSortOrder());
        dto.setSortOrder(0L);
        assertEquals(0L, types.updateBookType(dto).getSortOrder());
        dto.setSortOrder(-1L);
        assertThrows(BusinessException.class, () -> types.updateBookType(dto));
        assertThrows(BusinessException.class, () -> types.createBookType(dto));
        bookService().createBook(bookDTO());
        assertThrows(BusinessException.class, () -> types.deleteBookTypes(List.of(1L, 2L)));
        assertNotNull(types.getBookTypeById(2L));
        AdaptationTypeServiceImpl adaptations = new AdaptationTypeServiceImpl();
        ReflectionTestUtils.setField(adaptations, "adaptationTypeMapper", session.getMapper(AdaptationTypeMapper.class));
        ReflectionTestUtils.setField(adaptations, "animeMapper", session.getMapper(AnimeMapper.class));
        ReflectionTestUtils.setField(adaptations, "bookMapper", session.getMapper(BookMapper.class));
        assertEquals("所选改编类型已被作品引用，不能删除", assertThrows(BusinessException.class,
                () -> adaptations.deleteAdaptationTypes(List.of(1L))).getMessage());
        RegionServiceImpl regions = new RegionServiceImpl();
        ReflectionTestUtils.setField(regions, "regionMapper", session.getMapper(RegionMapper.class));
        ReflectionTestUtils.setField(regions, "animeMapper", session.getMapper(AnimeMapper.class));
        ReflectionTestUtils.setField(regions, "bookMapper", session.getMapper(BookMapper.class));
        assertEquals("所选地区已被作品引用，不能删除", assertThrows(BusinessException.class,
                () -> regions.deleteRegions(List.of(1L))).getMessage());
        types.deleteBookTypes(List.of(2L));
    }

    @Test
    void booksRejectInvalidRelationsBeforeWritesAndAmbiguousAutoSeries() {
        seedBookCatalogs();
        BookServiceImpl service = bookService();
        BookDTO dto = bookDTO();
        dto.setPersons(List.of(bookPerson(1L, "作者", 0L), bookPerson(1L, " 作者 ", 1L)));
        assertThrows(BusinessException.class, () -> service.createBook(dto));
        dto.setPersons(List.of(bookPerson(999L, "作者", 0L)));
        assertThrows(BusinessException.class, () -> service.createBook(dto));
        dto.setPersons(null);
        dto.setPersonalRatingScore(new java.math.BigDecimal("11"));
        assertThrows(BusinessException.class, () -> service.createBook(dto));
        dto.setPersonalRatingScore(new java.math.BigDecimal("8.5"));
        assertThrows(BusinessException.class, () -> service.createBook(dto));
        dto.setPersonalRatingScore(null);
        ExternalLinkDTO link = new ExternalLinkDTO();
        link.setUrl("javascript:alert(1)");
        dto.setExternalLinks(List.of(link));
        assertThrows(BusinessException.class, () -> service.createBook(dto));
        dto.setExternalLinks(null);
        dto.setAutoCreateSeries(true);
        LocalDateTime now = LocalDateTime.now();
        session.getMapper(SeriesMapper.class).insert(Series.builder().name("同名书籍").createdAt(now).updatedAt(now).build());
        session.getMapper(SeriesMapper.class).insert(Series.builder().name("同名书籍").createdAt(now).updatedAt(now).build());
        assertEquals("存在多个同名系列，请按 ID 手动选择系列",
                assertThrows(BusinessException.class, () -> service.createBook(dto)).getMessage());
        assertEquals(0L, session.getMapper(BookMapper.class).selectCount(null));
        dto.setSeriesId(1L);
        assertEquals(1L, service.createBook(dto).getSeriesId());
    }

    private BookDTO bookDTO() {
        BookDTO dto = new BookDTO();
        dto.setName(" 同名书籍 ");
        dto.setBookTypeId(1L);
        dto.setAdaptationTypeId(1L);
        dto.setRegionId(1L);
        dto.setStatus(0L);
        dto.setVolumeCount(0L);
        return dto;
    }

    @Test
    void bookPagingUsesExistsAndKeepsCountsSortingPrecisionAndFullPersons() {
        seedBookCatalogs();
        BookServiceImpl service = bookService();
        BookDTO first = bookDTO();
        first.setName("ABC 原作");
        first.setPublishDate(LocalDate.of(2023, 1, 1));
        first.setPersonalRatingScore(new java.math.BigDecimal("8.0"));
        first.setAliasNames(List.of("Alternate"));
        first.setPersons(List.of(bookPerson(1L, "作者", 0L), bookPerson(1L, "插画", 1L)));
        first.setAutoCreateSeries(true);
        CompanyRelationDTO company = new CompanyRelationDTO();
        company.setCompanyId(1L);
        first.setCompanies(List.of(company));
        first.setTagIds(List.of(1L));
        Long seriesId = service.createBook(first).getSeriesId();
        BookDTO second = bookDTO();
        second.setName("其他书名");
        second.setStatus(2L);
        second.setPublishDate(LocalDate.of(2024, 2, 29));
        second.setPersonalRatingScore(new java.math.BigDecimal("10"));
        second.setPersons(List.of(bookPerson(1L, "作者", 0L)));
        second.setSeriesId(seriesId);
        second.setCompanies(List.of(company));
        second.setTagIds(List.of(1L));
        service.createBook(second);
        Series duplicate = Series.builder().name("ABC 原作").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        session.getMapper(SeriesMapper.class).insert(duplicate);
        BookDTO third = bookDTO();
        third.setPersons(List.of(bookPerson(2L, "作者", 0L)));
        third.setSeriesId(duplicate.getId());
        service.createBook(third);
        BookDTO fourth = bookDTO();
        fourth.setPublishDate(LocalDate.of(2023, 1, 1));
        fourth.setPersonalRatingScore(new java.math.BigDecimal("8"));
        fourth.setSeriesId(seriesId);
        service.createBook(fourth);
        session.getMapper(PersonRelationMapper.class).insert(PersonRelation.builder().targetType("ANIME").targetId(3L)
                .personId(1L).role("导演").sortOrder(0L).build());
        session.getMapper(SeriesItemMapper.class).insert(SeriesItem.builder().workType("anime").workId(3L).seriesId(seriesId).build());
        session.getMapper(AliasMapper.class).insert(Alias.builder().targetType("ANIME").targetId(3L).aliasName("Alternate").sortOrder(0L).build());
        BookPageDTO query = new BookPageDTO();
        query.setPersonId(1L);
        query.setPageSize(1L);
        var page = service.pageBook(query);
        assertEquals(2L, page.getTotal());
        assertEquals(2L, page.getPages());
        assertEquals(2L, page.getRows().getFirst().getId());
        query.setPageNum(2L);
        assertEquals(2, service.pageBook(query).getRows().getFirst().getPersons().size());
        query.setPageNum(1L);
        query.setPageSize(20L);
        query.setCompanyId(1L);
        query.setSeriesId(seriesId);
        query.setKeyword("aBc");
        assertEquals(2L, service.pageBook(query).getTotal());
        query.setPersonId(2L);
        assertEquals(0L, service.pageBook(query).getTotal());
        query = new BookPageDTO();
        query.setKeyword("alternate");
        assertEquals(List.of(1L), bookIds(service.pageBook(query)));
        query.setKeyword(null);
        query.setPublishStartDate("2024");
        query.setPublishEndDate("2024-02");
        assertEquals(List.of(2L), bookIds(service.pageBook(query)));
        query.setPublishStartDate("2024-02-29");
        assertEquals(List.of(2L), bookIds(service.pageBook(query)));
        query = new BookPageDTO();
        query.setStatus(0L);
        assertEquals(List.of(4L, 1L, 3L), bookIds(service.pageBook(query)));
        query.setStatus(null);
        query.setSortBy("PERSONAL_RATING");
        assertEquals(List.of(2L, 4L, 1L, 3L), bookIds(service.pageBook(query)));
        query.setSortDirection("ASC");
        assertEquals(List.of(4L, 1L, 2L, 3L), bookIds(service.pageBook(query)));
        query.setTagIds(List.of(1L, 999L));
        assertEquals(0L, service.pageBook(query).getTotal());
        query.setTagMatchMode("ANY");
        assertEquals(2L, service.pageBook(query).getTotal());
        query.setRatingMin(new java.math.BigDecimal("9"));
        assertEquals(List.of(2L), bookIds(service.pageBook(query)));
        query.setBookTypeId(2147483647L);
        assertEquals(0L, service.pageBook(query).getTotal());
    }

    @Test
    void bookDeleteCleansOnlyBookTargetsAndRejectsMissingBatchMembers() {
        seedBookCatalogs();
        BookServiceImpl service = bookService();
        BookDTO dto = bookDTO();
        dto.setAliasNames(List.of("别名"));
        dto.setPersons(List.of(bookPerson(1L, "作者", 0L)));
        CompanyRelationDTO company = new CompanyRelationDTO();
        company.setCompanyId(1L);
        dto.setCompanies(List.of(company));
        dto.setTagIds(List.of(1L));
        dto.setAutoCreateSeries(true);
        dto.setPersonalRatingScore(new java.math.BigDecimal("0"));
        ExternalLinkDTO link = new ExternalLinkDTO();
        link.setUrl("https://example.com");
        dto.setExternalLinks(List.of(link));
        var book = service.createBook(dto);
        Long id = book.getId();
        session.getMapper(AliasMapper.class).insert(Alias.builder().targetType("ANIME").targetId(id).aliasName("动画别名").sortOrder(0L).build());
        session.getMapper(CompanyRelationMapper.class).insert(CompanyRelation.builder().targetType("ANIME").targetId(id).companyId(1L).sortOrder(0L).build());
        session.getMapper(PersonRelationMapper.class).insert(PersonRelation.builder().targetType("ANIME").targetId(id).personId(1L).role("导演").sortOrder(0L).build());
        session.getMapper(TagRelationMapper.class).insert(TagRelation.builder().targetType("ANIME").targetId(id).tagId(1L).build());
        session.getMapper(ExternalLinkMapper.class).insert(ExternalLink.builder().targetType("ANIME").targetId(id).title("动画").url("https://example.com/anime").sortOrder(0L).build());
        session.getMapper(PersonalRatingMapper.class).insert(PersonalRating.builder().targetType("ANIME").targetId(id).score(java.math.BigDecimal.TEN).build());
        session.getMapper(SeriesItemMapper.class).insert(SeriesItem.builder().workType("anime").workId(id).seriesId(book.getSeriesId()).build());
        assertThrows(BusinessException.class, () -> service.deleteBooks(List.of(id, 999L)));
        assertNotNull(service.getBookById(id));
        service.deleteBooks(List.of(id, id));
        assertThrows(BusinessException.class, () -> service.getBookById(id));
        for (Class<?> mapper : List.of(AliasMapper.class, CompanyRelationMapper.class, PersonRelationMapper.class,
                TagRelationMapper.class, ExternalLinkMapper.class, PersonalRatingMapper.class, SeriesItemMapper.class)) {
            com.baomidou.mybatisplus.core.mapper.BaseMapper<?> relations = (com.baomidou.mybatisplus.core.mapper.BaseMapper<?>) session.getMapper(mapper);
            assertEquals(1L, relations.selectCount(null));
        }
        assertEquals(2L, session.getMapper(PersonMapper.class).selectCount(null));
        assertNotNull(session.getMapper(SeriesMapper.class).selectById(book.getSeriesId()));
    }

    @Test
    void bookWriteFailureRollsBackBookAutomaticSeriesAndAllRelations() throws Exception {
        seedBookCatalogs();
        var config = session.getConfiguration();
        var dataSource = config.getEnvironment().getDataSource();
        config.setEnvironment(new Environment("book-transaction-memory", new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), dataSource));
        var factory = new MybatisSqlSessionFactoryBuilder().build(config);
        var template = new org.mybatis.spring.SqlSessionTemplate(factory);
        BookServiceImpl service = bookService(template);
        ExternalLinkMapper failedLinks = mock(ExternalLinkMapper.class);
        when(failedLinks.insert(any(ExternalLink.class))).thenThrow(new IllegalStateException("模拟外链写入失败"));
        ReflectionTestUtils.setField(service, "externalLinkMapper", failedLinks);
        BookDTO dto = bookDTO();
        dto.setAutoCreateSeries(true);
        dto.setAliasNames(List.of("别名"));
        dto.setPersons(List.of(bookPerson(1L, "作者", 0L)));
        ExternalLinkDTO link = new ExternalLinkDTO();
        link.setUrl("https://example.com");
        dto.setExternalLinks(List.of(link));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> service.createBook(dto)));
        try (var statement = connection.createStatement()) {
            for (String table : List.of("book", "series", "alias", "person_relation", "company_relation", "external_link")) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(result.next());
                    assertEquals(0L, result.getLong(1), table);
                }
            }
        }
        // 同一事务环境继续验证修改与删除中途失败时，基础资料和已清理的关联都能恢复。
        ReflectionTestUtils.setField(service, "externalLinkMapper", template.getMapper(ExternalLinkMapper.class));
        Long id = service.createBook(dto).getId();
        ReflectionTestUtils.setField(service, "externalLinkMapper", failedLinks);
        dto.setId(id);
        dto.setName("修改失败不应残留");
        dto.setAliasNames(List.of("新别名"));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> service.updateBook(dto)));
        assertEquals("同名书籍", template.getMapper(BookMapper.class).selectById(id).getName());
        assertEquals("别名", template.getMapper(AliasMapper.class).selectList(null).getFirst().getAliasName());
        ReflectionTestUtils.setField(service, "externalLinkMapper", template.getMapper(ExternalLinkMapper.class));
        BookMapper failedDelete = mock(BookMapper.class);
        when(failedDelete.selectByIds(anyCollection())).thenAnswer(invocation ->
                template.getMapper(BookMapper.class).selectByIds(invocation.getArgument(0)));
        when(failedDelete.deleteByIds(anyCollection())).thenThrow(new IllegalStateException("模拟删除失败"));
        ReflectionTestUtils.setField(service, "bookMapper", failedDelete);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            service.deleteBooks(List.of(id));
            return null;
        }));
        assertNotNull(template.getMapper(BookMapper.class).selectById(id));
        assertEquals(1L, template.getMapper(AliasMapper.class).selectCount(null));
        assertEquals(1L, template.getMapper(PersonRelationMapper.class).selectCount(null));
        assertEquals(1L, template.getMapper(ExternalLinkMapper.class).selectCount(null));
        assertEquals(1L, template.getMapper(SeriesItemMapper.class).selectCount(null));
    }

    @Test
    void sharedPreviewPreparationDoesNotCreateWorksOrAutomaticSeries() {
        seedBookCatalogs();
        BookDTO input = bookDTO();
        input.setAutoCreateSeries(true);
        input.setAliasNames(List.of(" 别名 ", "别名"));
        var prepared = bookService().prepareBook(input);
        assertEquals(List.of("别名"), prepared.getAliasNames());
        AnimeDTO anime = animeRatingDTO("预览动画");
        anime.setAutoCreateSeries(true);
        anime.setAliasNames(List.of(" 别名 ", "别名"));
        assertEquals(List.of("别名"), animeRatingService().prepareAnime(anime).getAliasNames());
        assertEquals(0L, session.getMapper(BookMapper.class).selectCount(null));
        assertEquals(0L, session.getMapper(AnimeMapper.class).selectCount(null));
        assertEquals(0L, session.getMapper(SeriesMapper.class).selectCount(null));
        assertEquals(0L, session.getMapper(AliasMapper.class).selectCount(null));
    }

    @Test
    void batchSavepointCanUndoAllWorksAndStillPersistFailureOutcome() throws Exception {
        seedBookCatalogs();
        var config = session.getConfiguration();
        var source = config.getEnvironment().getDataSource();
        config.setEnvironment(new Environment("transfer-savepoint-memory", new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), source));
        var template = new org.mybatis.spring.SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        var advice = new org.springframework.transaction.interceptor.TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        var proxy = new org.springframework.aop.framework.ProxyFactory(bookService(template));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(advice);
        var service = (BookServiceImpl) proxy.getProxy();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE TABLE batch_outcome (status TEXT)");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
        transaction.execute(status -> {
            Object point = status.createSavepoint();
            BookDTO first = bookDTO();
            first.setAliasNames(List.of("不应残留的别名"));
            service.createBookInTransaction(first);
            BookDTO second = bookDTO();
            second.setRegionId(999L);
            assertThrows(BusinessException.class, () -> service.createBookInTransaction(second));
            status.rollbackToSavepoint(point);
            status.releaseSavepoint(point);
            template.clearCache();
            jdbc.update("INSERT INTO batch_outcome (status) VALUES ('INVALID')");
            return null;
        });
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM book", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM alias", Long.class));
        assertEquals("INVALID", jdbc.queryForObject("SELECT status FROM batch_outcome", String.class));
    }

    @Test
    void exportedWorksShowCatalogNamesAndKeepHiddenIds() throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        for (String type : List.of("ANIME", "BOOK")) {
            var files = new TransferWorkbook(json);
            var service = new DataTransferServiceImpl();
            var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains("count(*)") ? 1L : 0L);
            when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                return List.of(sql.contains("series") ? "测试系列" : sql.contains("adaptation_type") ? "漫画改编" : sql.contains("region") ? "日本"
                        : sql.contains("broadcast_type") ? "TV" : "漫画");
            });
            var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
            var anime = mock(AnimeServiceImpl.class);
            when(anime.getAnimeById(1L)).thenReturn(org.killze.acgbox.vo.content.AnimeVO.builder()
                    .id(1L).name("导出动画").adaptationTypeId(2L).regionId(1L).broadcastTypeId(1L).status(3L).seriesId(7L).build());
            var book = mock(BookServiceImpl.class);
            when(book.getBookById(1L)).thenReturn(org.killze.acgbox.vo.content.BookVO.builder()
                    .id(1L).name("导出书籍").adaptationTypeId(2L).regionId(1L).bookTypeId(1L).status(0L).build());
            ReflectionTestUtils.setField(service, "json", json);
            ReflectionTestUtils.setField(service, "workbook", files);
            ReflectionTestUtils.setField(service, "jdbc", jdbc);
            ReflectionTestUtils.setField(service, "transactionManager", manager);
            ReflectionTestUtils.setField(service, "animeService", anime);
            ReflectionTestUtils.setField(service, "bookService", book);
            var request = json.createObjectNode().put("targetType", type).put("scope", "SELECTED");
            request.putArray("ids").add(1L);
            var download = service.export(type, request);
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(download.bytes()))) {
                var sheet = workbook.getSheet("works");
                var row = sheet.getRow(1);
                boolean isAnime = type.equals("ANIME");
                int offset = isAnime ? 0 : 1;
                assertEquals("漫画改编", row.getCell(9 + offset).getStringCellValue());
                assertEquals("日本", row.getCell(12 + offset).getStringCellValue());
                assertEquals(isAnime ? "已完结" : "其他", row.getCell(11 + offset).getStringCellValue());
                assertEquals(isAnime ? "TV" : "漫画", row.getCell(8 + offset).getStringCellValue());
                var expected = isAnime
                        ? List.of("编号", "作品名称", "系列名称", "简介", "标签", "公司与职责", "人物与职责", "总集数", "放送类型", "改编类型", "开播日期", "状态名称", "地区", "个人评分", "封面 URL", "别名", "外部链接")
                        : List.of("编号", "作品名称", "系列名称", "简介", "标签", "公司与职责", "人物与职责", "卷数", "章节数", "书籍类型", "改编类型", "发表日期", "状态名称", "地区", "个人评分", "封面 URL", "别名", "外部链接");
                var widths = isAnime ? new int[]{6,70,20,30,25,20,20,10,10,10,15,10,10,10,20,20,20} : new int[]{6,70,20,30,25,20,20,10,10,10,10,15,10,10,10,20,20,20};
                assertEquals(expected.size(), sheet.getRow(0).getLastCellNum());
                for (int i = 0; i < expected.size(); i++) {
                    assertEquals(expected.get(i), sheet.getRow(0).getCell(i).getStringCellValue());
                    assertEquals(widths[i] * 256, sheet.getColumnWidth(i));
                    assertFalse(sheet.isColumnHidden(i));
                }
                for (var tab : workbook) {
                    for (var dataRow : tab) assertEquals(16f, dataRow.getHeightInPoints());
                    if (!tab.getSheetName().equals("meta")) {
                        assertEquals("编号", tab.getRow(0).getCell(0).getStringCellValue());
                        assertEquals(6 * 256, tab.getColumnWidth(0));
                    }
                }
                if (type.equals("ANIME")) {
                    assertEquals("测试系列", row.getCell(2).getStringCellValue());
                } else {
                    assertEquals(null, row.getCell(2));
                }
            }
        }
    }

    @Test
    void completedImportCanBeRetriedWithoutRedisAndCannotBeReadByAnotherAccountOrType() throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var service = new DataTransferServiceImpl();
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("模拟缓存不可用"));
        ReflectionTestUtils.setField(service, "json", json);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "transactionManager", new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                session.getConfiguration().getEnvironment().getDataSource()));
        String token = "0ae613ad-d2e4-4c75-a319-25cff4a686ea";
        var row = mock(java.sql.ResultSet.class);
        when(row.getString(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "import_token" -> token;
            case "target_type" -> "BOOK";
            case "status" -> "SUCCEEDED";
            case "payload_hash" -> "test-only-hash";
            case "result" -> "{\"status\":\"SUCCEEDED\",\"createdCount\":1,\"mappings\":{\"W1\":42}}";
            default -> null;
        });
        when(row.getLong("account_id")).thenReturn(1L);
        when(row.getLong("work_count")).thenReturn(1L);
        when(row.getObject("expires_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now().minusHours(1));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(), eq(token)))
                .thenAnswer(invocation -> List.of(((org.springframework.jdbc.core.RowMapper<?>) invocation.getArgument(1)).mapRow(row, 0)));
        var request = json.createObjectNode().put("targetType", "BOOK").put("importToken", token).put("acknowledged", true);
        assertEquals(42, service.commit("BOOK", request, 1L).path("mappings").path("W1").asInt());
        assertEquals(42, service.commit("BOOK", request, 1L).path("mappings").path("W1").asInt());
        assertThrows(BusinessException.class, () -> service.commit("BOOK", request, 2L));
        request.put("targetType", "ANIME");
        assertThrows(BusinessException.class, () -> service.commit("ANIME", request, 1L));
    }

    @Test
    void animeExternalLinkTitleIsRequiredBeforeCreatingOrUpdating() {
        var service = animeRatingService();
        var dto = animeRatingDTO("标题测试");
        var link = new ExternalLinkDTO();
        link.setUrl("https://example.com/anime");
        dto.setExternalLinks(List.of(link));
        for (String title : java.util.Arrays.asList(null, "", "  ")) {
            link.setTitle(title);
            assertEquals("动画外链标题不能为空", assertThrows(BusinessException.class, () -> service.createAnime(dto)).getMessage());
            assertEquals(0L, session.getMapper(AnimeMapper.class).selectCount(null));
            assertEquals(0L, session.getMapper(ExternalLinkMapper.class).selectCount(null));
        }
        link.setTitle("  官网  ");
        Long id = service.createAnime(dto).getId();
        assertEquals("官网", service.getAnimeById(id).getExternalLinks().getFirst().getTitle());
        dto.setId(id); dto.setName("不应保存的新名称"); link.setTitle(" ");
        assertThrows(BusinessException.class, () -> service.updateAnime(dto));
        assertEquals("标题测试", service.getAnimeById(id).getName());
        assertEquals("官网", service.getAnimeById(id).getExternalLinks().getFirst().getTitle());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "true,false", "false,true"})
    void namedPreviewListsFourMissingCatalogsAndCommitRequiresSeparateConsent(boolean failSecondWork, boolean cancelPreview) throws Exception {
        seedBookCatalogs();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var files = new TransferWorkbook(json);
        var service = new DataTransferServiceImpl();
        var source = session.getConfiguration().getEnvironment().getDataSource();
        var configuration = session.getConfiguration();
        configuration.setEnvironment(new Environment("catalog-import-memory",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), source));
        var sessions = new org.mybatis.spring.SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        var realJdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class, org.mockito.AdditionalAnswers.delegatesTo(realJdbc));
        org.mockito.Mockito.doAnswer(invocation -> {
            Object[] args = invocation.getRawArguments();
            String sql = (String) args[0];
            if (sql.endsWith(" FOR KEY SHARE")) return realJdbc.queryForList(sql.replace(" FOR KEY SHARE", ""), Long.class, (Object[]) args[2]);
            return ((Object[]) args[2])[0].equals("新资料") ? List.of() : List.of(1L);
        }).when(jdbc).queryForList(anyString(), eq(Long.class), any(Object[].class));
        org.mockito.Mockito.doReturn(1L).when(jdbc).queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT 1 FROM pg_advisory"), eq(Long.class), any(Object[].class));
        org.mockito.Mockito.doReturn(0L).when(jdbc).queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT count(*) FROM data_import_batch"), eq(Long.class), any(Object[].class));
        org.mockito.Mockito.doReturn(1).when(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO data_import_batch"), any(Object[].class));
        org.mockito.Mockito.doReturn(1).when(jdbc).update(org.mockito.ArgumentMatchers.startsWith("UPDATE data_import_batch SET status"), any(Object[].class));
        org.mockito.Mockito.doNothing().when(jdbc).execute(org.mockito.ArgumentMatchers.startsWith("LOCK TABLE "));
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> cache = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(cache);
        var stored = new java.util.concurrent.atomic.AtomicReference<String>();
        org.mockito.Mockito.doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(cache).set(anyString(), anyString(), any(java.time.Duration.class));
        when(cache.get(anyString())).thenAnswer(invocation -> stored.get());
        ReflectionTestUtils.setField(service, "json", json);
        ReflectionTestUtils.setField(service, "workbook", files);
        ReflectionTestUtils.setField(service, "bookService", bookService(sessions));
        ReflectionTestUtils.setField(service, "bookMapper", sessions.getMapper(BookMapper.class));
        ReflectionTestUtils.setField(service, "sessions", sessions);
        var companies = new CompanyServiceImpl();
        var persons = new PersonServiceImpl();
        var series = new SeriesServiceImpl();
        var tags = new TagServiceImpl();
        ReflectionTestUtils.setField(companies, "companyMapper", sessions.getMapper(CompanyMapper.class));
        ReflectionTestUtils.setField(persons, "personMapper", sessions.getMapper(PersonMapper.class));
        ReflectionTestUtils.setField(series, "seriesMapper", sessions.getMapper(SeriesMapper.class));
        ReflectionTestUtils.setField(tags, "tagMapper", sessions.getMapper(TagMapper.class));
        ReflectionTestUtils.setField(service, "companyService", companies);
        ReflectionTestUtils.setField(service, "personService", persons);
        ReflectionTestUtils.setField(service, "seriesService", series);
        ReflectionTestUtils.setField(service, "tagService", tags);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "transactionManager", new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            ReflectionTestUtils.setField(service, "validator", validation.getValidator());
            var first = json.createObjectNode().put("rowKey", "W1").put("name", "预览书籍")
                    .put("bookTypeName", "漫画").put("adaptationTypeName", "原创").put("regionName", "日本").put("status", 0)
                    .put("seriesName", "新资料");
            var second = first.deepCopy().put("rowKey", "W2").put("name", "另一本");
            var relation = json.createObjectNode().put("rowKey", "W1").put("companyName", "新资料")
                    .put("personName", "新资料").put("tagName", "新资料").put("role", "作者");
            var secondPerson = relation.deepCopy().put("rowKey", "W2");
            byte[] bytes = files.writeNamed("BOOK", "TEMPLATE", java.util.Map.of("works", List.of(first, second),
                    "companies", List.of(relation), "persons", failSecondWork ? List.of(relation, secondPerson, secondPerson) : List.of(relation),
                    "tags", List.of(relation)));
            var preview = service.preview("BOOK", "BOOK", 1L, new org.springframework.mock.web.MockMultipartFile("file", "books.xlsx", null, bytes));
            assertEquals(0, preview.path("errorCount").asInt());
            assertEquals(4, preview.path("pendingCatalogs").size());
            assertEquals(2, preview.path("pendingCatalogs").get(0).path("rowKeys").size());
            String token = preview.path("importToken").asText();
            assertFalse(token.isBlank());
            var row = mock(java.sql.ResultSet.class);
            when(row.getString("import_token")).thenReturn(token);
            when(row.getString("target_type")).thenReturn("BOOK");
            when(row.getString("status")).thenReturn("READY");
            when(row.getString("payload_hash")).thenReturn(json.readTree(stored.get()).path("payloadHash").asText());
            when(row.getLong("account_id")).thenReturn(1L);
            when(row.getLong("work_count")).thenReturn(2L);
            when(row.getObject("expires_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.parse(preview.path("expiresAt").asText()));
            org.mockito.Mockito.doAnswer(invocation -> List.of(((org.springframework.jdbc.core.RowMapper<?>) invocation.getArgument(1)).mapRow(row, 0)))
                    .when(jdbc).query(org.mockito.ArgumentMatchers.startsWith("SELECT * FROM data_import_batch"),
                            org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(), eq(token));
            assertThrows(BusinessException.class, () -> service.batch("BOOK", "BOOK", token, 2L, false));
            var recovered = service.batch("BOOK", "BOOK", token, 1L, false);
            assertEquals("READY", recovered.path("status").asText());
            assertEquals(json.readTree(preview.toString()), json.readTree(recovered.path("preview").toString()));
            if (cancelPreview) {
                var cancelled = service.batch("BOOK", "BOOK", token, 1L, true);
                assertEquals("CANCELLED", cancelled.path("status").asText());
                assertFalse(cancelled.has("preview"));
                assertEquals(0L, realJdbc.queryForObject("SELECT count(*) FROM book", Long.class));
                return;
            }
            var request = json.createObjectNode().put("targetType", "BOOK").put("importToken", token).put("acknowledged", true);
            assertEquals("请先确认创建本批缺失资料", assertThrows(BusinessException.class,
                    () -> service.commit("BOOK", request, 1L)).getMessage());
            assertEquals(0L, realJdbc.queryForObject("SELECT count(*) FROM book", Long.class));
            for (String table : List.of("series", "tag", "company", "person")) {
                assertEquals(0L, realJdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE name=?", Long.class, "新资料"));
            }
            var result = service.commit("BOOK", request.put("createMissingCatalogs", true), 1L);
            assertEquals(failSecondWork ? "INVALID" : "SUCCEEDED", result.path("status").asText(), result.toString());
            if (failSecondWork) assertEquals("W2", result.path("rowKey").asText());
        }
        assertEquals(failSecondWork ? 0L : 2L, realJdbc.queryForObject("SELECT count(*) FROM book", Long.class));
        for (String table : List.of("series", "tag", "company", "person")) {
            assertEquals(failSecondWork ? 0L : 1L, realJdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE name=?", Long.class, "新资料"));
        }
    }

    private void installDataQualityTextFunctions() throws Exception {
        // 内存库提供 PostgreSQL 文本函数的等价行为；实际筛选、连接、分组和分页由 SQL 执行。
        org.sqlite.Collation.create(connection, "C", new org.sqlite.Collation() {
            @Override
            protected int xCompare(String left, String right) {
                return left.compareTo(right);
            }
        });
        org.sqlite.Function.create(connection, "btrim", new org.sqlite.Function() {
            @Override
            protected void xFunc() throws java.sql.SQLException {
                String value = value_text(0);
                if (value == null) {
                    result();
                    return;
                }
                String trim = value_text(1);
                int start = 0, end = value.length();
                while (start < end && trim.indexOf(value.charAt(start)) >= 0) start++;
                while (end > start && trim.indexOf(value.charAt(end - 1)) >= 0) end--;
                result(value.substring(start, end));
            }
        });
        org.sqlite.Function.create(connection, "translate", new org.sqlite.Function() {
            @Override
            protected void xFunc() throws java.sql.SQLException {
                String from = value_text(1), to = value_text(2);
                var value = new StringBuilder(value_text(0));
                for (int i = 0; i < value.length(); i++) {
                    int index = from.indexOf(value.charAt(i));
                    if (index >= 0) value.setCharAt(i, to.charAt(index));
                }
                result(value.toString());
            }
        });
        org.sqlite.Function.create(connection, "strpos", new org.sqlite.Function() {
            @Override
            protected void xFunc() throws java.sql.SQLException {
                result(value_text(0).indexOf(value_text(1)) + 1);
            }
        });
    }

    @Test
    void dataQualityChecksScopeCountsPagingAndTypesThroughTheReadEndpoint() throws Exception {
        installDataQualityTextFunctions();
        seedBookCatalogs();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(session.getConfiguration().getEnvironment().getDataSource());
        jdbc.update("INSERT INTO broadcast_type (id,name) VALUES (1,'TV')");
        jdbc.update("INSERT INTO book_type (id,name) VALUES (2,'漫画')");
        jdbc.update("""
                INSERT INTO anime (id,name,cover_image_url,description,region_id,broadcast_type_id,updated_at)
                VALUES (1,' Hero ',?,?,1,1,'2026-11-06 12:00:00.000'),
                       (2,'hERO','https://example.com/cover.jpg','短',1,1,'2026-11-06 12:00:00.000'),
                       (3,'Hero!',NULL,'简介',1,1,'2026-11-05 12:00:00.000')
                """, " \t", "\r\n");
        jdbc.update("""
                INSERT INTO book (id,name,book_type_id,adaptation_type_id,region_id,status,created_at,updated_at)
                VALUES (1,' Title ',1,1,1,0,'2026-11-06','2026-11-06 12:00:00.000'),
                       (2,'TITLE',1,1,1,0,'2026-11-06','2026-11-06 12:00:00.000'),
                       (3,'title',2,1,1,0,'2026-11-06','2026-11-06 12:00:00.000'),
                       (4,'Hero',1,1,1,0,'2026-11-06','2026-11-06 12:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO person_relation (target_type,target_id,person_id,role)
                VALUES ('BOOK',1,1,'作者'),('ANIME',2,1,'导演'),('ANIME',2,1,'脚本')
                """);
        jdbc.update("INSERT INTO company_relation (target_type,target_id,company_id) VALUES ('BOOK',1,1),('ANIME',2,1),('ANIME',3,1)");
        jdbc.update("INSERT INTO tag_relation (target_type,target_id,tag_id) VALUES ('BOOK',1,1),('ANIME',2,1)");
        jdbc.update("UPDATE anime SET air_date='2026-01-01' WHERE id IN (2,3)");
        jdbc.update("UPDATE book SET publish_date='2025-12-31' WHERE id=1");

        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new org.killze.acgbox.controller.DataQualityController(new DataQualityServiceImpl(jdbc)))
                .setControllerAdvice(new org.killze.acgbox.exception.GlobalExceptionHandler()).build();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", "ANIME").param("keyword", " \tHeRo\n").param("ruleCodes", "MISSING_PERSON,MISSING_COVER,MISSING_PERSON")
                .param("matchMode", "ALL");
        var response = json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsByteArray());
        assertEquals(200, response.path("code").asInt(), response.toString());
        var result = response.path("data");
        assertEquals(2, result.path("total").asInt());
        assertEquals(3, result.path("summary").path("scopeTotal").asInt());
        assertEquals(1, result.path("summary").path("ruleCounts").path("MISSING_DESCRIPTION").asInt());
        assertEquals(2, result.path("summary").path("ruleCounts").path("SAME_NAME_REVIEW").asInt());
        assertEquals(1, result.path("rows").get(0).path("targetId").asInt());
        assertEquals("MISSING_COVER", result.path("rows").get(0).path("issues").get(0).path("code").asText());
        assertEquals("MISSING_PERSON", result.path("rows").get(0).path("issues").get(1).path("code").asText());
        assertTrue(result.path("rows").get(0).path("duplicateCount").isNull());
        assertEquals("TV", result.path("rows").get(0).path("contentType").path("name").asText());

        var anyRequest = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", "ANIME").param("keyword", "hero").param("ruleCodes", "MISSING_COVER,SAME_NAME_REVIEW");
        var anyResult = json.readTree(mvc.perform(anyRequest).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(3, anyResult.path("total").asInt());
        assertEquals(result.path("summary"), anyResult.path("summary"));
        assertEquals(2, anyResult.path("rows").get(0).path("targetId").asInt());
        assertEquals(2, anyResult.path("rows").get(0).path("duplicateCount").asInt());

        var extraRequest = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", "ANIME").param("ruleCodes", "MISSING_COMPANY,MISSING_TAG,MISSING_DATE");
        var extra = json.readTree(mvc.perform(extraRequest).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(2, extra.path("total").asInt());
        assertEquals(result.path("summary"), extra.path("summary"));
        assertEquals(1, extra.path("rows").get(0).path("targetId").asInt());
        assertEquals(json.readTree("""
                [{"code":"MISSING_COMPANY","message":"缺公司"},{"code":"MISSING_TAG","message":"缺标签"},{"code":"MISSING_DATE","message":"缺日期"}]
                """), extra.path("rows").get(0).path("issues"));
        assertEquals(3, extra.path("rows").get(1).path("targetId").asInt());
        assertEquals(1, extra.path("rows").get(1).path("issues").size());
        assertEquals("MISSING_TAG", extra.path("rows").get(1).path("issues").get(0).path("code").asText());
        var extraAll = json.readTree(mvc.perform(extraRequest.param("matchMode", "ALL")).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(1, extraAll.path("total").asInt());
        var bookExtra = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", "BOOK").param("keyword", "title").param("matchMode", "ALL")
                .param("ruleCodes", "MISSING_COMPANY,MISSING_TAG,MISSING_DATE");
        var bookExtraResult = json.readTree(mvc.perform(bookExtra).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(2, bookExtraResult.path("total").asInt());
        assertEquals(2, bookExtraResult.path("rows").get(1).path("targetId").asInt());
        var extraCounts = extra.path("summary").path("ruleCounts");
        assertEquals(1, extraCounts.path("MISSING_COMPANY").asInt());
        assertEquals(2, extraCounts.path("MISSING_TAG").asInt());
        assertEquals(1, extraCounts.path("MISSING_DATE").asInt());

        var pageRequest = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", "BOOK").param("keyword", "title").param("ruleCodes", "SAME_NAME_REVIEW").param("pageSize", "1");
        var page = json.readTree(mvc.perform(pageRequest).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(2, page.path("pages").asInt());
        assertEquals(3, page.path("summary").path("scopeTotal").asInt());
        assertEquals(2, page.path("rows").get(0).path("targetId").asInt());
        assertEquals(2, page.path("rows").get(0).path("duplicateCount").asInt());
        assertEquals(1, page.path("rows").get(0).path("issues").size());
        var beyond = json.readTree(mvc.perform(pageRequest.param("pageNum", "3")).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(2, beyond.path("total").asInt());
        assertTrue(beyond.path("rows").isEmpty());

        var emptyRequest = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", "BOOK").param("keyword", "Hero").param("ruleCodes", "SAME_NAME_REVIEW");
        var empty = json.readTree(mvc.perform(emptyRequest).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(1, empty.path("summary").path("scopeTotal").asInt());
        assertEquals(0, empty.path("total").asInt());
        assertEquals(0, empty.path("pages").asInt());
        assertTrue(empty.path("rows").isEmpty());
        var invalidRequest = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", "BOOK").param("ruleCodes", "UNKNOWN");
        assertEquals(500, json.readTree(mvc.perform(invalidRequest).andReturn().getResponse().getContentAsByteArray()).path("code").asInt());
        assertEquals(4L, jdbc.queryForObject("SELECT count(*) FROM book", Long.class));

        var unavailable = mock(javax.sql.DataSource.class);
        when(unavailable.getConnection()).thenThrow(new java.sql.SQLException("模拟读取失败"));
        var failedMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new org.killze.acgbox.controller.DataQualityController(new DataQualityServiceImpl(
                        new org.springframework.jdbc.core.JdbcTemplate(unavailable))))
                .setControllerAdvice(new org.killze.acgbox.exception.GlobalExceptionHandler()).build();
        var failure = json.readTree(failedMvc.perform(request).andReturn().getResponse().getContentAsByteArray());
        assertEquals(500, failure.path("code").asInt());
        assertFalse(failure.hasNonNull("data"));
    }

    @ParameterizedTest
    @CsvSource({"PERSON,person", "COMPANY,company", "SERIES,series"})
    void catalogQualityReturnsIndividualProfilesWithTheFullDuplicateGroupSize(String targetType, String table) throws Exception {
        installDataQualityTextFunctions();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(session.getConfiguration().getEnvironment().getDataSource());
        jdbc.update("""
                INSERT INTO %s (id,name,description,created_at,updated_at)
                VALUES (1,?,NULL,'2026-11-11','2026-11-11 12:00:00.000'),
                       (2,'ALICE','摄影','2026-11-11','2026-11-11 12:00:00.000'),
                       (3,'alice','编剧','2026-11-10','2026-11-10 12:00:00.000'),
                       (4,'Alice!',NULL,'2026-11-11','2026-11-11 13:00:00.000'),
                       (5,'其他人物',NULL,'2026-11-11','2026-11-11 13:00:00.000')
                """.formatted(table), "\t Alice " + (char) 0x3000);
        for (String other : List.of("person", "company", "series")) {
            if (!other.equals(table)) {
                jdbc.update("INSERT INTO " + other + " (id,name,created_at,updated_at) VALUES (2,'ALICE',?,?)",
                        "2026-11-11", "2026-11-11 12:00:00.000");
            }
        }
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new org.killze.acgbox.controller.DataQualityController(new DataQualityServiceImpl(jdbc)))
                .setControllerAdvice(new org.killze.acgbox.exception.GlobalExceptionHandler()).build();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", targetType).param("ruleCodes", "SAME_NAME_REVIEW")
                .param("keyword", "  aLiCe\t").param("pageSize", "2");
        var response = json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsByteArray());
        assertEquals(200, response.path("code").asInt(), response.toString());
        var result = response.path("data");
        assertEquals(3, result.path("total").asInt());
        assertEquals(2, result.path("pages").asInt());
        assertEquals(4, result.path("summary").path("scopeTotal").asInt());
        assertEquals(json.readTree("{\"SAME_NAME_REVIEW\":3}"), result.path("summary").path("ruleCounts"));
        var rows = result.path("rows");
        assertEquals(2, rows.size());
        assertEquals(targetType, rows.get(0).path("targetType").asText());
        assertEquals(2L, rows.get(0).path("targetId").asLong());
        assertEquals("ALICE", rows.get(0).path("name").asText());
        assertEquals("摄影", rows.get(0).path("description").asText());
        assertTrue(rows.get(0).hasNonNull("updatedAt"));
        assertTrue(rows.get(0).path("coverImageUrl").isNull());
        assertTrue(rows.get(0).path("contentType").isNull());
        assertTrue(rows.get(0).path("region").isNull());
        assertEquals("SAME_NAME_REVIEW", rows.get(0).path("issues").get(0).path("code").asText());
        assertEquals(3L, rows.get(0).path("duplicateCount").asLong());
        assertEquals(1L, rows.get(1).path("targetId").asLong());
        assertTrue(rows.get(1).path("description").isNull());
        var next = json.readTree(mvc.perform(request.param("pageNum", "2").param("matchMode", "ALL"))
                .andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals(1, next.path("rows").size());
        assertEquals(3L, next.path("rows").get(0).path("targetId").asLong());
        assertEquals(3L, next.path("rows").get(0).path("duplicateCount").asLong());

        var invalid = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/data-quality/page")
                .param("targetType", targetType).param("ruleCodes", "SAME_NAME_REVIEW,MISSING_COVER");
        var error = json.readTree(mvc.perform(invalid).andReturn().getResponse().getContentAsByteArray());
        assertEquals(500, error.path("code").asInt());
        assertEquals(targetType + " 仅支持 SAME_NAME_REVIEW 检查规则", error.path("message").asText());
    }

    @Test
    void catalogPagesSortByChineseAndEnglishInitialBeforePagination() throws Exception {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(session.getConfiguration().getEnvironment().getDataSource());
        for (String table : List.of("company", "person", "series")) {
            jdbc.update("""
                    INSERT INTO %s (id,name,created_at,updated_at)
                    VALUES (1,'zeta','2026-11-09T08:00:00','2026-11-11T12:00:00'),
                           (2,'  北京','2026-11-09T08:00:00','2026-11-11T13:00:00'),
                           (3,'a studio','2026-11-09T08:00:00','2026-11-10T12:00:00'),
                           (4,'白马','2026-11-09T08:00:00','2026-11-11T13:00:00'),
                           (5,'8号','2026-11-09T08:00:00','2026-11-11T15:00:00'),
                           (6,'★','2026-11-09T08:00:00','2026-11-11T14:00:00'),
                           (7,'Beta','2026-11-09T08:00:00','2026-11-09T12:00:00')
                    """.formatted(table));
        }
        var companies = new org.killze.acgbox.controller.CompanyController();
        var companyService = new CompanyServiceImpl();
        ReflectionTestUtils.setField(companyService, "companyMapper", session.getMapper(CompanyMapper.class));
        ReflectionTestUtils.setField(companies, "companyService", companyService);
        var persons = new org.killze.acgbox.controller.PersonController();
        ReflectionTestUtils.setField(persons, "personService", personService());
        var series = new org.killze.acgbox.controller.SeriesController();
        var seriesService = new SeriesServiceImpl();
        ReflectionTestUtils.setField(seriesService, "seriesMapper", session.getMapper(SeriesMapper.class));
        ReflectionTestUtils.setField(series, "seriesService", seriesService);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(companies, persons, series).build();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        for (String path : List.of("/companies/page", "/persons/page", "/series/page")) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
            var defaultResult = json.readTree(mvc.perform(request.param("pageSize", "2")).andReturn().getResponse().getContentAsByteArray());
            assertEquals(200, defaultResult.path("code").asInt(), defaultResult.toString());
            assertEquals(5L, defaultResult.path("data").path("rows").get(0).path("id").asLong());
            assertEquals(6L, defaultResult.path("data").path("rows").get(1).path("id").asLong());

            var expectedPages = List.of(List.of(3L, 4L, 2L), List.of(7L, 1L, 5L), List.of(6L));
            for (int index = 0; index < expectedPages.size(); index++) {
                var sorted = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                        .param("sortBy", "NAME_INITIAL").param("pageSize", "3").param("pageNum", String.valueOf(index + 1));
                var response = json.readTree(mvc.perform(sorted).andReturn().getResponse().getContentAsByteArray());
                assertEquals(200, response.path("code").asInt(), response.toString());
                var data = response.path("data");
                assertEquals(7L, data.path("total").asLong());
                assertEquals(3L, data.path("pages").asLong());
                var ids = new java.util.ArrayList<Long>();
                for (var row : data.path("rows")) ids.add(row.path("id").asLong());
                assertEquals(expectedPages.get(index), ids);
            }
        }
    }

    @Test
    void previewReportsCellErrorAndCacheOutageWithoutCreatingWorks() {
        seedBookCatalogs();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var files = new TransferWorkbook(json);
        var service = new DataTransferServiceImpl();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(session.getConfiguration().getEnvironment().getDataSource());
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("模拟缓存不可用"));
        ReflectionTestUtils.setField(service, "json", json);
        ReflectionTestUtils.setField(service, "workbook", files);
        ReflectionTestUtils.setField(service, "bookService", bookService());
        ReflectionTestUtils.setField(service, "bookMapper", session.getMapper(BookMapper.class));
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "redis", redis);
        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            ReflectionTestUtils.setField(service, "validator", validation.getValidator());
            var row = json.createObjectNode().put("rowKey", "W1").put("name", "预览书籍")
                    .put("bookTypeId", 1).put("adaptationTypeId", 1).put("regionId", 1).put("status", 0)
                    .put("personalRatingScore", new java.math.BigDecimal("8.5"));
            byte[] invalid = files.write("BOOK", "TEMPLATE", java.util.Map.of("works", List.of(row)));
            var result = service.preview("BOOK", "BOOK", 1L,
                    new org.springframework.mock.web.MockMultipartFile("file", "books.xlsx", null, invalid));
            assertEquals(1, result.path("errorCount").asInt());
            assertFalse(result.hasNonNull("importToken"));
            var error = result.path("errors").get(0);
            assertEquals("works", error.path("sheet").asText());
            assertEquals(2, error.path("row").asInt());
            assertEquals("personalRatingScore", error.path("field").asText());
            row.put("personalRatingScore", new java.math.BigDecimal("8.0"));
            byte[] valid = files.write("BOOK", "TEMPLATE", java.util.Map.of("works", List.of(row)));
            assertEquals("导入预览缓存暂不可用，请稍后重试", assertThrows(BusinessException.class, () -> service.preview("BOOK", "BOOK", 1L,
                    new org.springframework.mock.web.MockMultipartFile("file", "books.xlsx", null, valid))).getMessage());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM book", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM series", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM alias", Long.class));
    }

    private List<Long> bookIds(org.killze.acgbox.vo.common.PageVO<org.killze.acgbox.vo.content.BookPageVO> page) {
        return page.getRows().stream().map(value -> value.getId()).toList();
    }

    private PersonRelationDTO bookPerson(Long id, String role, Long sort) {
        PersonRelationDTO dto = new PersonRelationDTO();
        dto.setPersonId(id);
        dto.setRole(role);
        dto.setSortOrder(sort);
        return dto;
    }

    @Test
    void animeRatingSavesClearsAndFiltersWithoutChangingBookScore() {
        AnimeServiceImpl service = animeRatingService();
        Long zeroId = null;
        for (String value : List.of("null", "0", "1", "8", "10", "8.0")) {
            AnimeDTO dto = animeRatingDTO("评分" + value);
            dto.setPersonalRatingScore(value.equals("null") ? null : new java.math.BigDecimal(value));
            var created = service.createAnime(dto);
            dto.setId(created.getId());
            var updated = service.updateAnime(dto);
            var detail = service.getAnimeById(created.getId());
            if (value.equals("null")) {
                assertNull(created.getPersonalRatingScore());
                assertNull(updated.getPersonalRatingScore());
                assertNull(detail.getPersonalRatingScore());
            } else {
                for (var result : List.of(created, updated, detail)) {
                    assertEquals(0, dto.getPersonalRatingScore().compareTo(result.getPersonalRatingScore()));
                }
            }
            if (value.equals("0")) {
                zeroId = created.getId();
            }
        }
        var all = service.pageAnime(new AnimePageDTO());
        assertEquals(6L, all.getTotal());
        assertEquals(1L, all.getRows().stream().filter(row -> row.getPersonalRating() == null).count());
        for (String[] range : List.of(new String[]{"0", "0", "1"}, new String[]{"0", "10", "5"},
                new String[]{"10", "10", "1"}, new String[]{"8.0", "8.0", "2"})) {
            var query = new AnimePageDTO();
            query.setRatingMin(new java.math.BigDecimal(range[0]));
            query.setRatingMax(new java.math.BigDecimal(range[1]));
            var page = service.pageAnime(query);
            assertEquals(Long.parseLong(range[2]), page.getTotal());
            page.getRows().forEach(row -> {
                assertNotNull(row.getPersonalRating());
                assertTrue(row.getPersonalRating().compareTo(query.getRatingMin()) >= 0);
                assertTrue(row.getPersonalRating().compareTo(query.getRatingMax()) <= 0);
            });
        }
        var ratings = session.getMapper(PersonalRatingMapper.class);
        ratings.insert(PersonalRating.builder().targetType("BOOK").targetId(zeroId).score(java.math.BigDecimal.TEN).build());
        AnimeDTO update = animeRatingDTO("评分0");
        update.setId(zeroId);
        assertNull(service.updateAnime(update).getPersonalRatingScore());
        assertNull(service.getAnimeById(zeroId).getPersonalRatingScore());
        assertEquals(0, java.math.BigDecimal.TEN.compareTo(ratings.selectOne(new LambdaQueryWrapper<PersonalRating>()
                .eq(PersonalRating::getTargetType, "BOOK").eq(PersonalRating::getTargetId, zeroId)).getScore()));
        update.setPersonalRatingScore(java.math.BigDecimal.ZERO);
        assertEquals(0, service.updateAnime(update).getPersonalRatingScore().compareTo(java.math.BigDecimal.ZERO));
    }

    @Test
    void invalidAnimeRatingDoesNotCreateOrModifyWorkOrAssociations() {
        AnimeServiceImpl service = animeRatingService();
        AnimeDTO original = animeRatingDTO("原动画");
        original.setAliasNames(List.of("原别名"));
        original.setPersonalRatingScore(java.math.BigDecimal.ZERO);
        Long id = service.createAnime(original).getId();
        for (String value : List.of("8.1", "8.5", "-1", "10.1", "11")) {
            AnimeDTO invalid = animeRatingDTO("不应保存");
            invalid.setAliasNames(List.of("不应保存的别名"));
            invalid.setPersonalRatingScore(new java.math.BigDecimal(value));
            assertThrows(BusinessException.class, () -> service.createAnime(invalid));
            invalid.setId(id);
            assertThrows(BusinessException.class, () -> service.updateAnime(invalid));
            for (boolean minimum : List.of(true, false)) {
                var query = new AnimePageDTO();
                if (minimum) {
                    query.setRatingMin(new java.math.BigDecimal(value));
                } else {
                    query.setRatingMax(new java.math.BigDecimal(value));
                }
                assertThrows(BusinessException.class, () -> service.pageAnime(query));
            }
            var unchanged = service.getAnimeById(id);
            assertEquals("原动画", unchanged.getName());
            assertEquals(List.of("原别名"), unchanged.getAliasNames());
            assertEquals(0, unchanged.getPersonalRatingScore().compareTo(java.math.BigDecimal.ZERO));
            assertEquals(1L, session.getMapper(AnimeMapper.class).selectCount(null));
            assertEquals(1L, session.getMapper(AliasMapper.class).selectCount(null));
        }
    }

    private AnimeDTO animeRatingDTO(String name) {
        AnimeDTO dto = new AnimeDTO();
        dto.setName(name);
        dto.setBroadcastTypeId(1L);
        dto.setAdaptationTypeId(1L);
        dto.setRegionId(1L);
        dto.setStatus(3L);
        return dto;
    }

    private AnimeServiceImpl animeRatingService() {
        AnimeServiceImpl service = new AnimeServiceImpl();
        java.util.Map<String, Class<?>> mappers = java.util.Map.ofEntries(
                java.util.Map.entry("animeMapper", AnimeMapper.class), java.util.Map.entry("broadcastTypeMapper", BroadcastTypeMapper.class),
                java.util.Map.entry("adaptationTypeMapper", AdaptationTypeMapper.class), java.util.Map.entry("regionMapper", RegionMapper.class),
                java.util.Map.entry("companyMapper", CompanyMapper.class), java.util.Map.entry("companyRelationMapper", CompanyRelationMapper.class),
                java.util.Map.entry("personMapper", PersonMapper.class), java.util.Map.entry("personRelationMapper", PersonRelationMapper.class),
                java.util.Map.entry("aliasMapper", AliasMapper.class), java.util.Map.entry("externalLinkMapper", ExternalLinkMapper.class),
                java.util.Map.entry("tagMapper", TagMapper.class), java.util.Map.entry("tagRelationMapper", TagRelationMapper.class),
                java.util.Map.entry("seriesMapper", SeriesMapper.class), java.util.Map.entry("seriesItemMapper", SeriesItemMapper.class),
                java.util.Map.entry("personalRatingMapper", PersonalRatingMapper.class));
        mappers.forEach((field, mapper) -> ReflectionTestUtils.setField(service, field, session.getMapper(mapper)));
        return service;
    }

    private void seedBookCatalogs() {
        BookTypeDTO type = new BookTypeDTO();
        type.setName("轻小说");
        bookTypeService().createBookType(type);
        session.getMapper(AdaptationTypeMapper.class).insert(AdaptationType.builder().name("原创").sortOrder(0L).build());
        session.getMapper(RegionMapper.class).insert(Region.builder().name("日本").build());
        session.getMapper(CompanyMapper.class).insert(Company.builder().name("出版机构").build());
        session.getMapper(TagMapper.class).insert(Tag.builder().name("奇幻").build());
        PersonDTO person = new PersonDTO();
        person.setName("同名人物");
        personService().createPerson(person);
        personService().createPerson(person);
    }

    private BookTypeServiceImpl bookTypeService() {
        BookTypeServiceImpl service = new BookTypeServiceImpl();
        ReflectionTestUtils.setField(service, "bookTypeMapper", session.getMapper(BookTypeMapper.class));
        ReflectionTestUtils.setField(service, "bookMapper", session.getMapper(BookMapper.class));
        return service;
    }

    private BookServiceImpl bookService() {
        return bookService(session);
    }

    private BookServiceImpl bookService(SqlSession source) {
        BookServiceImpl service = new BookServiceImpl();
        java.util.Map<String, Class<?>> mappers = java.util.Map.ofEntries(
                java.util.Map.entry("bookMapper", BookMapper.class), java.util.Map.entry("bookTypeMapper", BookTypeMapper.class),
                java.util.Map.entry("adaptationTypeMapper", AdaptationTypeMapper.class), java.util.Map.entry("regionMapper", RegionMapper.class),
                java.util.Map.entry("companyMapper", CompanyMapper.class), java.util.Map.entry("companyRelationMapper", CompanyRelationMapper.class),
                java.util.Map.entry("personMapper", PersonMapper.class), java.util.Map.entry("personRelationMapper", PersonRelationMapper.class),
                java.util.Map.entry("aliasMapper", AliasMapper.class), java.util.Map.entry("externalLinkMapper", ExternalLinkMapper.class),
                java.util.Map.entry("tagMapper", TagMapper.class), java.util.Map.entry("tagRelationMapper", TagRelationMapper.class),
                java.util.Map.entry("seriesMapper", SeriesMapper.class), java.util.Map.entry("seriesItemMapper", SeriesItemMapper.class),
                java.util.Map.entry("personalRatingMapper", PersonalRatingMapper.class));
        mappers.forEach((field, mapper) -> ReflectionTestUtils.setField(service, field, source.getMapper(mapper)));
        return service;
    }

    /**
     * 仅在 SQLite 内存库准备样本，包含同名实体、多职责、错误目标类型及日期评分空值。
     */
    private void seedAnimeAssociations() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("INSERT INTO person (id,name,created_at,updated_at) VALUES "
                    + "(12,'同名人物','2026-10-26T10:00:00','2026-10-26T10:00:00'),"
                    + "(13,'同名人物','2026-10-26T10:00:00','2026-10-26T10:00:00')");
            statement.execute("INSERT INTO anime (id,name,air_date) VALUES "
                    + "(1,'Abc','2024-01-01'),(2,'ABC 第二部','2025-01-01'),(3,'Abc外部','2023-01-01'),"
                    + "(4,'Other',NULL),(5,'ABc同名不同关联','2024-01-01')");
            statement.execute("INSERT INTO person_relation (target_type,target_id,person_id,role,sort_order) VALUES "
                    + "('ANIME',1,12,'导演',0),('ANIME',1,12,'脚本',1),('ANIME',2,12,'导演',0),"
                    + "('ANIME',4,12,'音乐',0),('BOOK',3,12,'作者',0),('ANIME',3,13,'导演',0),('ANIME',5,13,'导演',0)");
            statement.execute("INSERT INTO company_relation (target_type,target_id,company_id,role,sort_order) VALUES "
                    + "('ANIME',1,3,'制作',0),('ANIME',2,3,'制作',0),('ANIME',4,4,'制作',0),"
                    + "('BOOK',3,3,'出版',0),('ANIME',3,4,'制作',0)");
            statement.execute("INSERT INTO series (id,name) VALUES (8,'同名系列'),(9,'同名系列')");
            statement.execute("INSERT INTO series_item (series_id,work_type,work_id) VALUES "
                    + "(8,'anime',1),(8,'anime',2),(8,'anime',4),(9,'anime',3),(9,'anime',5),"
                    + "(8,'book',3),(8,'ANIME',5)");
            statement.execute("INSERT INTO personal_rating (target_type,target_id,score) VALUES "
                    + "('ANIME',1,8),('ANIME',2,9),('ANIME',5,8)");
        }
    }
}
