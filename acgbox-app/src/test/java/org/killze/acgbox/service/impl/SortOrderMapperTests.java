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
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.entity.content.Alias;
import org.killze.acgbox.entity.content.BroadcastType;
import org.killze.acgbox.entity.content.CompanyRelation;
import org.killze.acgbox.entity.content.Person;
import org.killze.acgbox.entity.content.PersonRelation;
import org.killze.acgbox.dto.content.PersonDTO;
import org.killze.acgbox.dto.content.PersonPageDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.entity.content.Anime;
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
            statement.execute("CREATE TABLE personal_rating (target_type TEXT, target_id INTEGER, score NUMERIC, "
                    + "UNIQUE(target_type,target_id))");
            statement.execute("CREATE TABLE series (id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("CREATE TABLE series_item (id INTEGER PRIMARY KEY AUTOINCREMENT, series_id INTEGER, "
                    + "work_type TEXT, work_id INTEGER, sort_order INTEGER DEFAULT 0)");
            statement.execute("CREATE TABLE tag_relation (id INTEGER PRIMARY KEY, target_type TEXT, target_id INTEGER, tag_id INTEGER)");
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
        try (var mapper = Resources.getResourceAsStream("mapper/AnimeMapper.xml")) {
            new XMLMapperBuilder(mapper, configuration, "mapper/AnimeMapper.xml", configuration.getSqlFragments()).parse();
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
    void broadcastMapsWritesDefaultsUpdatesAndStableSort() {
        BroadcastTypeMapper mapper = session.getMapper(BroadcastTypeMapper.class);
        BroadcastType first = BroadcastType.builder().name("Z").build();
        BroadcastType second = BroadcastType.builder().name("A").sortOrder(0L).build();
        BroadcastType third = BroadcastType.builder().name("B").sortOrder(-1L).build();
        mapper.insert(first);
        mapper.insert(second);
        mapper.insert(third);
        assertEquals(0L, mapper.selectById(first.getId()).getSortOrder());
        var result = mapper.selectList(new LambdaQueryWrapper<BroadcastType>()
                .orderByAsc(BroadcastType::getSortOrder).orderByAsc(BroadcastType::getId));
        assertEquals(List.of(third.getId(), first.getId(), second.getId()), result.stream().map(BroadcastType::getId).toList());
        first.setSortOrder(-2147483648L);
        mapper.updateById(first);
        assertEquals(-2147483648L, mapper.selectById(first.getId()).getSortOrder());
    }

    @Test
    void adaptationMapsWritesDefaultsUpdatesAndStableSort() {
        AdaptationTypeMapper mapper = session.getMapper(AdaptationTypeMapper.class);
        AdaptationType first = AdaptationType.builder().name("Z").build();
        AdaptationType second = AdaptationType.builder().name("A").sortOrder(0L).build();
        AdaptationType third = AdaptationType.builder().name("B").sortOrder(-1L).build();
        mapper.insert(first);
        mapper.insert(second);
        mapper.insert(third);
        assertEquals(0L, mapper.selectById(first.getId()).getSortOrder());
        var result = mapper.selectList(new LambdaQueryWrapper<AdaptationType>()
                .orderByAsc(AdaptationType::getSortOrder).orderByAsc(AdaptationType::getId));
        assertEquals(List.of(third.getId(), first.getId(), second.getId()), result.stream().map(AdaptationType::getId).toList());
        first.setSortOrder(2147483647L);
        mapper.updateById(first);
        assertEquals(2147483647L, mapper.selectById(first.getId()).getSortOrder());
    }

    @Test
    void aliasMapsStoredSortAndFiltersOtherTargets() {
        AliasMapper mapper = session.getMapper(AliasMapper.class);
        Alias first = Alias.builder().targetType("ANIME").targetId(1L).aliasName("Z").build();
        Alias second = Alias.builder().targetType("ANIME").targetId(1L).aliasName("A").sortOrder(0L).build();
        Alias third = Alias.builder().targetType("ANIME").targetId(1L).aliasName("B").sortOrder(-1L).build();
        mapper.insert(first);
        mapper.insert(second);
        mapper.insert(third);
        mapper.insert(Alias.builder().targetType("BOOK").targetId(1L).aliasName("其他类型").sortOrder(-9L).build());
        mapper.insert(Alias.builder().targetType("ANIME").targetId(2L).aliasName("其他动画").sortOrder(-9L).build());
        var result = mapper.selectList(new LambdaQueryWrapper<Alias>().eq(Alias::getTargetType, "ANIME")
                .eq(Alias::getTargetId, 1L).orderByAsc(Alias::getSortOrder).orderByAsc(Alias::getId));
        assertEquals(List.of("B", "Z", "A"), result.stream().map(Alias::getAliasName).toList());
        assertEquals(List.of(-1L, 0L, 0L), result.stream().map(Alias::getSortOrder).toList());
    }

    @Test
    void companyRelationMapsSortAndUsesRelationIdNotCompanyId() {
        CompanyRelationMapper mapper = session.getMapper(CompanyRelationMapper.class);
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(1L).companyId(9L).role("制作").sortOrder(0L).build());
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(1L).companyId(2L).role("发行").build());
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(1L).companyId(3L).role("协力").sortOrder(-1L).build());
        mapper.insert(CompanyRelation.builder().targetType("BOOK").targetId(1L).companyId(4L).sortOrder(-9L).build());
        mapper.insert(CompanyRelation.builder().targetType("ANIME").targetId(2L).companyId(5L).sortOrder(-9L).build());
        var result = mapper.selectList(new LambdaQueryWrapper<CompanyRelation>().eq(CompanyRelation::getTargetType, "ANIME")
                .in(CompanyRelation::getTargetId, List.of(1L)).orderByAsc(CompanyRelation::getSortOrder).orderByAsc(CompanyRelation::getId));
        assertEquals(List.of(3L, 9L, 2L), result.stream().map(CompanyRelation::getCompanyId).toList());
        assertEquals(List.of(-1L, 0L, 0L), result.stream().map(CompanyRelation::getSortOrder).toList());
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
        assertEquals(List.of(2L, 5L, 1L, 4L, 3L), animeIds(queryAnime(dto)));
        dto.setSortDirection("ASC");
        assertEquals(List.of(5L, 1L, 2L, 4L, 3L), animeIds(queryAnime(dto)));
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
            statement.execute("INSERT INTO series_item (series_id,work_type,work_id,sort_order) VALUES "
                    + "(8,'anime',1,9),(8,'anime',2,1),(8,'anime',4,0),(9,'anime',3,0),(9,'anime',5,0),"
                    + "(8,'book',3,0),(8,'ANIME',5,0)");
            statement.execute("INSERT INTO personal_rating (target_type,target_id,score) VALUES "
                    + "('ANIME',1,8),('ANIME',2,9),('ANIME',5,8)");
        }
    }
}
