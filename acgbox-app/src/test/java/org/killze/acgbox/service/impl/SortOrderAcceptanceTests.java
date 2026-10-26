package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.controller.AdaptationTypeController;
import org.killze.acgbox.controller.AnimeController;
import org.killze.acgbox.controller.BroadcastTypeController;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.dto.content.CompanyRelationDTO;
import org.killze.acgbox.entity.content.*;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.exception.GlobalExceptionHandler;
import org.killze.acgbox.mapper.*;
import org.killze.acgbox.vo.content.AnimeVO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 排序契约独立验收：服务连接专用 SQLite 内存关联表，主记录分页和外部依赖使用模拟对象。
 * 不启动应用上下文，不读取应用数据源配置，不连接用户数据库。
 */
class SortOrderAcceptanceTests {

    private Connection connection;
    private SqlSession session;
    private AliasMapper aliasMapper;
    private CompanyRelationMapper relationMapper;
    private AnimeMapper animeMapper;
    private AnimeServiceImpl animeService;
    private MockMvc mvc;
    private Anime anime;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            for (String table : List.of("broadcast_type", "adaptation_type")) {
                statement.execute("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, "
                        + "sort_order INTEGER NOT NULL DEFAULT 0, created_at TEXT, updated_at TEXT)");
            }
            statement.execute("CREATE TABLE alias (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, target_id INTEGER, "
                    + "alias_name TEXT, sort_order INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE company_relation (id INTEGER PRIMARY KEY AUTOINCREMENT, target_type TEXT, target_id INTEGER, "
                    + "company_id INTEGER, role TEXT, sort_order INTEGER NOT NULL DEFAULT 0, UNIQUE(target_type,target_id,company_id))");
        }
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("acceptance-memory", new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        for (Class<?> mapper : List.of(BroadcastTypeMapper.class, AdaptationTypeMapper.class,
                AliasMapper.class, CompanyRelationMapper.class)) {
            config.addMapper(mapper);
        }
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        for (Class<?> entity : List.of(Anime.class, ExternalLink.class, TagRelation.class, SeriesItem.class, PersonalRating.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config, ""), entity);
        }
        aliasMapper = session.getMapper(AliasMapper.class);
        relationMapper = session.getMapper(CompanyRelationMapper.class);
        BroadcastTypeMapper broadcastMapper = session.getMapper(BroadcastTypeMapper.class);
        AdaptationTypeMapper adaptationMapper = session.getMapper(AdaptationTypeMapper.class);
        animeMapper = mock(AnimeMapper.class);
        anime = Anime.builder().id(101L).name("验收动画").broadcastTypeId(1L).adaptationTypeId(1L)
                .regionId(1L).airDate(LocalDate.of(2026, 1, 1)).status(3L).build();
        when(animeMapper.selectById(101L)).thenAnswer(invocation -> anime);
        when(animeMapper.insert(any(Anime.class))).thenAnswer(invocation -> {
            anime = invocation.getArgument(0);
            anime.setId(101L);
            return 1;
        });
        when(animeMapper.selectAnimePage(any(), any(), isNull(), isNull())).thenAnswer(invocation -> {
            Page<Anime> page = new Page<>(1, 20);
            page.setRecords(List.of(anime));
            page.setTotal(1);
            return page;
        });
        CompanyMapper companyMapper = mock(CompanyMapper.class);
        when(companyMapper.selectById(anyLong())).thenAnswer(invocation ->
                Company.builder().id(invocation.getArgument(0)).name("公司" + invocation.getArgument(0)).build());
        when(companyMapper.selectByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> Company.builder().id(id).name("公司" + id).build()).toList();
        });
        animeService = new AnimeServiceImpl();
        ReflectionTestUtils.setField(animeService, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(animeService, "aliasMapper", aliasMapper);
        ReflectionTestUtils.setField(animeService, "companyRelationMapper", relationMapper);
        ReflectionTestUtils.setField(animeService, "companyMapper", companyMapper);
        ReflectionTestUtils.setField(animeService, "personMapper", mock(PersonMapper.class));
        ReflectionTestUtils.setField(animeService, "personRelationMapper", mock(PersonRelationMapper.class));
        ReflectionTestUtils.setField(animeService, "broadcastTypeMapper", broadcastMapper);
        ReflectionTestUtils.setField(animeService, "adaptationTypeMapper", adaptationMapper);
        ReflectionTestUtils.setField(animeService, "regionMapper", mock(RegionMapper.class));
        ReflectionTestUtils.setField(animeService, "externalLinkMapper", mock(ExternalLinkMapper.class));
        ReflectionTestUtils.setField(animeService, "tagMapper", mock(TagMapper.class));
        ReflectionTestUtils.setField(animeService, "tagRelationMapper", mock(TagRelationMapper.class));
        ReflectionTestUtils.setField(animeService, "seriesMapper", mock(SeriesMapper.class));
        ReflectionTestUtils.setField(animeService, "seriesItemMapper", mock(SeriesItemMapper.class));
        ReflectionTestUtils.setField(animeService, "personalRatingMapper", mock(PersonalRatingMapper.class));
        BroadcastTypeServiceImpl broadcastService = new BroadcastTypeServiceImpl();
        ReflectionTestUtils.setField(broadcastService, "broadcastTypeMapper", broadcastMapper);
        ReflectionTestUtils.setField(broadcastService, "animeMapper", animeMapper);
        AdaptationTypeServiceImpl adaptationService = new AdaptationTypeServiceImpl();
        ReflectionTestUtils.setField(adaptationService, "adaptationTypeMapper", adaptationMapper);
        ReflectionTestUtils.setField(adaptationService, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(adaptationService, "bookMapper", mock(BookMapper.class));
        BroadcastTypeController broadcastController = new BroadcastTypeController();
        ReflectionTestUtils.setField(broadcastController, "broadcastTypeService", broadcastService);
        AdaptationTypeController adaptationController = new AdaptationTypeController();
        ReflectionTestUtils.setField(adaptationController, "adaptationTypeService", adaptationService);
        AnimeController animeController = new AnimeController();
        ReflectionTestUtils.setField(animeController, "animeService", animeService);
        mvc = MockMvcBuilders.standaloneSetup(broadcastController, adaptationController, animeController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) session.close();
        if (connection != null) connection.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"broadcast-type", "adaptation-type"})
    void dictionaryOldRequestsNullAndZeroRoundTripThroughRealServiceAndMapper(String catalog) throws Exception {
        String create = "/" + catalog + "/create";
        String update = "/" + catalog + "/update";
        mvc.perform(post(create).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"缺省\"}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.sortOrder").value(0));
        mvc.perform(post(create).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"空值\",\"sortOrder\":null}"))
                .andExpect(jsonPath("$.data.sortOrder").value(0));
        for (String suffix : List.of(",\"sortOrder\":9", "", ",\"sortOrder\":null", ",\"sortOrder\":0")) {
            mvc.perform(post(update).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"name\":\"改名\"" + suffix + "}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.sortOrder").value(suffix.endsWith(":0") ? 0 : 9));
        }
        mvc.perform(get("/" + catalog + "/1")).andExpect(jsonPath("$.data.sortOrder").value(0));
        mvc.perform(get("/" + catalog + "/list"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].sortOrder").value(0))
                .andExpect(jsonPath("$.data[2]").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"broadcast-type", "adaptation-type"})
    void dictionarySortsActualRowsByValueAndOwnIdAcrossIntegerBounds(String catalog) throws Exception {
        String table = catalog.replace('-', '_');
        try (var statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + table + " (id,name,sort_order) VALUES "
                    + "(40,'A',0),(20,'Z',-1),(10,'M',0),(30,'B',2147483647),(50,'C',-2147483648)");
        }
        var response = mvc.perform(get("/" + catalog + "/list")).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[5]").doesNotExist());
        List<Long> ids = List.of(50L, 20L, 10L, 40L, 30L);
        List<Long> orders = List.of(-2147483648L, -1L, 0L, 0L, 2147483647L);
        for (int index = 0; index < ids.size(); index++) {
            response.andExpect(jsonPath("$.data[" + index + "].id").value(ids.get(index)))
                    .andExpect(jsonPath("$.data[" + index + "].sortOrder").value(orders.get(index)));
        }
    }

    @ParameterizedTest
    @CsvSource({"broadcast-type,-2147483648", "adaptation-type,2147483647", "broadcast-type,2147483647", "adaptation-type,-2147483648"})
    void dictionaryBoundsSurviveCreateAndUpdate(String catalog, long value) throws Exception {
        mvc.perform(post("/" + catalog + "/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"边界\",\"sortOrder\":" + value + "}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.sortOrder").value(value));
        mvc.perform(post("/" + catalog + "/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"边界\",\"sortOrder\":" + value + "}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.sortOrder").value(value));
        mvc.perform(get("/" + catalog + "/1")).andExpect(jsonPath("$.data.sortOrder").value(value));
    }

    @ParameterizedTest
    @CsvSource({"broadcast-type,create,2147483648", "broadcast-type,update,-2147483649",
            "adaptation-type,create,-2147483649", "adaptation-type,update,2147483648"})
    void invalidDictionaryRangeNeverPersists(String catalog, String operation, String value) throws Exception {
        mvc.perform(post("/" + catalog + "/" + operation).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"越界\",\"sortOrder\":" + value + "}"))
                .andExpect(jsonPath("$.code").value(not(200)));
        mvc.perform(get("/" + catalog + "/list")).andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"broadcast-type,create", "adaptation-type,create", "anime,create",
            "broadcast-type,update", "adaptation-type,update", "anime,update"})
    void decimalSortOrderMustNotBecomeSuccessfulTruncatedInteger(String catalog, String operation) throws Exception {
        if (!catalog.equals("anime") && operation.equals("update")) {
            mvc.perform(post("/" + catalog + "/create").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"待编辑\"}")).andExpect(jsonPath("$.code").value(200));
        }
        String body = catalog.equals("anime")
                ? """
                  {"id":101,"name":"小数排序","broadcastTypeId":1,"adaptationTypeId":1,"regionId":1,"airDate":"2026-01-01",
                   "status":3,"companies":[{"companyId":20,"sortOrder":1.5}]}
                  """
                : "{\"id\":1,\"name\":\"小数排序\",\"sortOrder\":1.5}";
        mvc.perform(post("/" + catalog + "/" + operation).contentType(MediaType.APPLICATION_JSON).content(body))
                .andDo(result -> System.out.println("排序验收小数响应 /" + catalog + "/" + operation + "："
                        + result.getResponse().getContentAsString()))
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @ParameterizedTest
    @CsvSource({"create,-2147483649", "create,2147483648", "update,-2147483649", "update,2147483648"})
    void nestedCompanyRangeIsRejectedBeforeAnyWrites(String operation, long value) throws Exception {
        String body = """
                {"id":101,"name":"越界公司排序","broadcastTypeId":1,"adaptationTypeId":1,"regionId":1,
                 "airDate":"2026-01-01","status":3,"companies":[{"companyId":20,"sortOrder":%d}]}
                """.formatted(value);
        mvc.perform(post("/anime/" + operation).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(not(200)));
        verifyNoInteractions(animeMapper);
        assertEquals(0L, relationMapper.selectCount(null));
    }

    @Test
    void createCleansFirstOccurrencesAndResponseMatchesStoredDetailAndPage() {
        AnimeDTO dto = animeRequest();
        dto.setAliasNames(Arrays.asList(null, " B ", "", "A", "B", "  ", "C", "A"));
        dto.setCompanies(Arrays.asList(null, company(20, " 主制 ", 9L), company(10, "协制", -1L),
                company(20, "重复", -9L), company(30, " ", null)));
        AnimeVO result = animeService.createAnime(dto);
        assertEquals(List.of("B", "A", "C"), result.getAliasNames());
        assertEquals(List.of(0L, 1L, 2L), storedAliases().stream().map(Alias::getSortOrder).toList());
        assertEquals(List.of(10L, 30L, 20L), companyIds(result));
        assertEquals(List.of(-1L, 2L, 9L), result.getCompanies().stream().map(value -> value.getSortOrder()).toList());
        assertEquals("主制", result.getCompanies().getLast().getRole());
        assertNull(result.getCompanies().get(1).getRole());
        assertSameReads(result);
    }

    @Test
    void legacyUpdatePreservesZeroAndNegativeSortBeforeDeletingAndAddsCleanedPosition() {
        seedOtherTargets();
        relationMapper.insert(relation(7L, "ANIME", 101, 20, 7));
        relationMapper.insert(relation(8L, "ANIME", 101, 10, -2));
        relationMapper.insert(relation(9L, "ANIME", 101, 40, 0));
        AnimeDTO dto = animeRequest();
        dto.setId(101L);
        dto.setAliasNames(List.of(" C ", "A", "C"));
        dto.setCompanies(Arrays.asList(null, company(20, "新职责", null), company(20, "重复", -9L),
                company(30, "新增", null), company(10, "保留负数", null), company(40, "保留零", null)));
        AnimeVO result = animeService.updateAnime(dto);
        assertEquals(List.of(10L, 40L, 30L, 20L), companyIds(result));
        assertEquals(List.of(-2L, 0L, 1L, 7L), result.getCompanies().stream().map(value -> value.getSortOrder()).toList());
        assertEquals("新职责", result.getCompanies().getLast().getRole());
        assertEquals(List.of("C", "A"), result.getAliasNames());
        assertSameReads(result);
        assertOtherTargetsIntact();
    }

    @Test
    void mixedExplicitAndOmittedCompanySortKeepsFirstRecordAndWritesBounds() {
        relationMapper.insert(relation(1L, "ANIME", 101, 20, 9));
        relationMapper.insert(relation(2L, "ANIME", 101, 10, -1));
        AnimeDTO dto = animeRequest();
        dto.setId(101L);
        dto.setCompanies(List.of(company(20, "归零", 0L), company(10, "保留", null),
                company(30, "下界", -2147483648L), company(40, "上界", 2147483647L),
                company(50, "首条缺省", null), company(50, "忽略后条", -2147483648L)));
        AnimeVO result = animeService.updateAnime(dto);
        assertEquals(List.of(30L, 10L, 20L, 50L, 40L), companyIds(result));
        assertEquals(List.of(-2147483648L, -1L, 0L, 4L, 2147483647L),
                result.getCompanies().stream().map(value -> value.getSortOrder()).toList());
        assertEquals("首条缺省", result.getCompanies().get(3).getRole());
        assertSameReads(result);
    }

    @Test
    void storedTiesUseAssociationIdAndPageDoesNotLeakOtherTargets() {
        seedOtherTargets();
        relationMapper.insert(relation(30L, "ANIME", 101, 2, 0));
        relationMapper.insert(relation(10L, "ANIME", 101, 99, 0));
        relationMapper.insert(relation(20L, "ANIME", 101, 3, -1));
        aliasMapper.insert(Alias.builder().id(30L).targetType("ANIME").targetId(101L).aliasName("A").sortOrder(0L).build());
        aliasMapper.insert(Alias.builder().id(10L).targetType("ANIME").targetId(101L).aliasName("Z").sortOrder(0L).build());
        AnimeVO result = animeService.getAnimeById(101L);
        assertEquals(List.of(3L, 99L, 2L), companyIds(result));
        assertEquals(List.of("Z", "A"), result.getAliasNames());
        assertSameReads(result);
        assertOtherTargetsIntact();
    }

    @Test
    void cleaningToEmptyRemovesOnlyCurrentAnimeRelations() {
        seedOtherTargets();
        relationMapper.insert(relation(1L, "ANIME", 101, 20, 9));
        aliasMapper.insert(Alias.builder().targetType("ANIME").targetId(101L).aliasName("旧别名").sortOrder(0L).build());
        AnimeDTO dto = animeRequest();
        dto.setId(101L);
        dto.setAliasNames(Arrays.asList("", "  ", null));
        dto.setCompanies(List.of());
        AnimeVO result = animeService.updateAnime(dto);
        assertTrue(result.getAliasNames().isEmpty());
        assertTrue(result.getCompanies().isEmpty());
        assertSameReads(result);
        assertOtherTargetsIntact();
    }

    @ParameterizedTest
    @CsvSource({"adaptation,false", "adaptation,true", "region,false", "region,true"})
    void deletionRejectsAnimePrecheckOrForeignKeyWithAccurateMessage(String kind, boolean foreignKey) {
        when(animeMapper.selectCount(any())).thenReturn(foreignKey ? 0L : 1L);
        List<Long> ids = List.of(11L, 12L);
        String expected = kind.equals("adaptation") ? "所选改编类型已被作品引用，不能删除" : "所选地区已被作品引用，不能删除";
        if (kind.equals("adaptation")) {
            AdaptationTypeMapper mapper = mock(AdaptationTypeMapper.class);
            AdaptationTypeServiceImpl service = new AdaptationTypeServiceImpl();
            ReflectionTestUtils.setField(service, "adaptationTypeMapper", mapper);
            ReflectionTestUtils.setField(service, "animeMapper", animeMapper);
            ReflectionTestUtils.setField(service, "bookMapper", mock(BookMapper.class));
            if (foreignKey) doThrow(new DataIntegrityViolationException("模拟其他作品外键")).when(mapper).deleteByIds(ids);
            assertEquals(expected, assertThrows(BusinessException.class, () -> service.deleteAdaptationTypes(ids)).getMessage());
            if (!foreignKey) verify(mapper, never()).deleteByIds(anyCollection());
        } else {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Region.class);
            RegionMapper mapper = mock(RegionMapper.class);
            RegionServiceImpl service = new RegionServiceImpl();
            ReflectionTestUtils.setField(service, "regionMapper", mapper);
            ReflectionTestUtils.setField(service, "animeMapper", animeMapper);
            ReflectionTestUtils.setField(service, "bookMapper", mock(BookMapper.class));
            if (foreignKey) doThrow(new DataIntegrityViolationException("模拟其他作品外键")).when(mapper).deleteByIds(ids);
            assertEquals(expected, assertThrows(BusinessException.class, () -> service.deleteRegions(ids)).getMessage());
            if (!foreignKey) verify(mapper, never()).deleteByIds(anyCollection());
        }
    }

    private AnimeDTO animeRequest() {
        AnimeDTO dto = new AnimeDTO();
        dto.setName("验收动画");
        dto.setBroadcastTypeId(1L);
        dto.setAdaptationTypeId(1L);
        dto.setRegionId(1L);
        dto.setAirDate(LocalDate.of(2026, 1, 1));
        dto.setStatus(3L);
        return dto;
    }

    private CompanyRelationDTO company(long id, String role, Long order) {
        CompanyRelationDTO dto = new CompanyRelationDTO();
        dto.setCompanyId(id);
        dto.setRole(role);
        dto.setSortOrder(order);
        return dto;
    }

    private CompanyRelation relation(Long id, String type, long target, long company, long order) {
        return CompanyRelation.builder().id(id).targetType(type).targetId(target).companyId(company).sortOrder(order).build();
    }

    private List<Alias> storedAliases() {
        return aliasMapper.selectList(new LambdaQueryWrapper<Alias>().eq(Alias::getTargetType, "ANIME")
                .eq(Alias::getTargetId, 101L).orderByAsc(Alias::getSortOrder).orderByAsc(Alias::getId));
    }

    private List<Long> companyIds(AnimeVO value) {
        return value.getCompanies().stream().map(company -> company.getCompanyId()).toList();
    }

    private void assertSameReads(AnimeVO written) {
        AnimeVO detail = animeService.getAnimeById(101L);
        assertEquals(written.getAliasNames(), detail.getAliasNames());
        assertEquals(written.getCompanies(), detail.getCompanies());
        var page = animeService.pageAnime(new AnimePageDTO()).getRows().getFirst();
        assertEquals(written.getAliasNames(), page.getAliasNames());
        assertEquals(companyIds(written), page.getCompanies().stream().map(value -> value.getCompanyId()).toList());
        assertEquals(written.getCompanies().stream().map(value -> value.getSortOrder()).toList(),
                page.getCompanies().stream().map(value -> value.getSortOrder()).toList());
        assertEquals(written.getCompanies().stream().map(value -> value.getRole()).toList(),
                page.getCompanies().stream().map(value -> value.getRole()).toList());
        assertEquals(companyIds(written).stream().map(id -> "公司" + id).toList(),
                page.getCompanies().stream().map(value -> value.getCompanyName()).toList());
    }

    private void seedOtherTargets() {
        relationMapper.insert(relation(70L, "BOOK", 101, 60, -99));
        relationMapper.insert(relation(71L, "ANIME", 102, 70, -99));
        aliasMapper.insert(Alias.builder().id(70L).targetType("BOOK").targetId(101L).aliasName("其他类型").sortOrder(-99L).build());
        aliasMapper.insert(Alias.builder().id(71L).targetType("ANIME").targetId(102L).aliasName("其他动画").sortOrder(-99L).build());
    }

    private void assertOtherTargetsIntact() {
        assertEquals(60L, relationMapper.selectById(70L).getCompanyId());
        assertEquals(70L, relationMapper.selectById(71L).getCompanyId());
        assertEquals("其他类型", aliasMapper.selectById(70L).getAliasName());
        assertEquals("其他动画", aliasMapper.selectById(71L).getAliasName());
    }
}
