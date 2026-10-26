package org.killze.acgbox.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.dto.content.BroadcastTypeDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.entity.content.Anime;
import org.killze.acgbox.mapper.AnimeMapper;
import org.killze.acgbox.service.impl.AnimeServiceImpl;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.exception.GlobalExceptionHandler;
import org.killze.acgbox.service.AdaptationTypeService;
import org.killze.acgbox.service.AnimeService;
import org.killze.acgbox.service.BroadcastTypeService;
import org.killze.acgbox.vo.content.AdaptationTypeVO;
import org.killze.acgbox.vo.content.BroadcastTypeVO;
import org.killze.acgbox.vo.content.AnimeVO;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 排序请求的反序列化、范围校验及响应字段测试，不启动应用、不加载私有配置。
 */
class SortOrderValidationTests {

    private MockMvc mockMvc;
    private BroadcastTypeService broadcastTypeService;
    private AdaptationTypeService adaptationTypeService;
    private AnimeService animeService;

    @BeforeEach
    void setUp() {
        broadcastTypeService = mock(BroadcastTypeService.class);
        adaptationTypeService = mock(AdaptationTypeService.class);
        animeService = mock(AnimeService.class);
        BroadcastTypeController broadcastTypeController = new BroadcastTypeController();
        AdaptationTypeController adaptationTypeController = new AdaptationTypeController();
        AnimeController animeController = new AnimeController();
        ReflectionTestUtils.setField(broadcastTypeController, "broadcastTypeService", broadcastTypeService);
        ReflectionTestUtils.setField(adaptationTypeController, "adaptationTypeService", adaptationTypeService);
        ReflectionTestUtils.setField(animeController, "animeService", animeService);
        mockMvc = MockMvcBuilders.standaloneSetup(broadcastTypeController, adaptationTypeController, animeController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"broadcast-type,-2147483648", "broadcast-type,2147483647", "broadcast-type,0", "broadcast-type,-1",
            "adaptation-type,-2147483648", "adaptation-type,2147483647", "adaptation-type,0", "adaptation-type,-1"})
    void dictionaryAcceptsIntegerBoundsAndReturnsSortOrder(String catalog, long sortOrder) throws Exception {
        when(broadcastTypeService.createBroadcastType(any())).thenAnswer(invocation -> {
            BroadcastTypeDTO dto = invocation.getArgument(0);
            return BroadcastTypeVO.builder().id(1L).name(dto.getName()).sortOrder(dto.getSortOrder()).build();
        });
        when(adaptationTypeService.createAdaptationType(any())).thenAnswer(invocation -> {
            AdaptationTypeDTO dto = invocation.getArgument(0);
            return AdaptationTypeVO.builder().id(1L).name(dto.getName()).sortOrder(dto.getSortOrder()).build();
        });
        mockMvc.perform(post("/" + catalog + "/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试\",\"sortOrder\":" + sortOrder + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sortOrder").value(sortOrder));
    }

    @ParameterizedTest
    @CsvSource({"broadcast-type,create,-2147483649", "broadcast-type,update,2147483648",
            "adaptation-type,create,2147483648", "adaptation-type,update,-2147483649",
            "anime,create,-2147483649", "anime,update,2147483648"})
    void rejectsOutOfRangeBeforeCallingServices(String catalog, String operation, long sortOrder) throws Exception {
        String body = catalog.equals("anime")
                ? """
                  {"id":1,"name":"测试","broadcastTypeId":1,"adaptationTypeId":1,"regionId":1,
                   "airDate":"2026-01-01","status":3,"companies":[{"companyId":1,"sortOrder":%d}]}
                  """.formatted(sortOrder)
                : "{\"id\":1,\"name\":\"测试\",\"sortOrder\":" + sortOrder + "}";
        mockMvc.perform(post("/" + catalog + "/" + operation).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(sortOrder < 0
                        ? "排序值不能小于 -2147483648" : "排序值不能大于 2147483647"));
        verifyNoInteractions(broadcastTypeService, adaptationTypeService, animeService);
    }

    @ParameterizedTest
    @CsvSource({"broadcast-type,create", "broadcast-type,update", "adaptation-type,create",
            "adaptation-type,update", "anime,create", "anime,update"})
    void rejectsFractionalSortOrderBeforeCallingServices(String catalog, String operation) throws Exception {
        String body = catalog.equals("anime")
                ? """
                  {"id":1,"name":"测试","broadcastTypeId":1,"adaptationTypeId":1,"regionId":1,
                   "airDate":"2026-01-01","status":3,"companies":[{"companyId":1,"sortOrder":1.5}]}
                  """
                : "{\"id\":1,\"name\":\"测试\",\"sortOrder\":1.5}";
        mockMvc.perform(post("/" + catalog + "/" + operation).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(broadcastTypeService, adaptationTypeService, animeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2147483648", "1.5", "1.0", "\"1\""})
    void personSortRejectsNegativeOverflowFractionAndStringBeforeService(String sortOrder) throws Exception {
        for (String operation : java.util.List.of("create", "update")) {
            String body = """
                    {"id":1,"name":"测试","broadcastTypeId":1,"adaptationTypeId":1,"regionId":1,
                     "airDate":"2026-01-01","status":3,"persons":[{"personId":1,"role":"导演","sortOrder":%s}]}
                    """.formatted(sortOrder);
            mockMvc.perform(post("/anime/" + operation).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        }
        verifyNoInteractions(animeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "0", "-1", "1.5", "0x1", "1 2", "2147483648", "9223372036854775808"})
    void associationQueryRejectsInvalidIdsInsteadOfIgnoringThem(String value) throws Exception {
        AnimeMapper mapper = mock(AnimeMapper.class);
        MockMvc mvc = associationPageMvc(mapper);
        for (String field : java.util.List.of("companyId", "personId", "seriesId")) {
            mvc.perform(get("/anime/page").param(field, value))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        }
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2147483647L})
    void validUnknownAssociationIdsReturnNormalEmptyPages(long id) throws Exception {
        AnimeMapper mapper = mock(AnimeMapper.class);
        MockMvc mvc = associationPageMvc(mapper);
        for (String field : java.util.List.of("companyId", "personId", "seriesId")) {
            mvc.perform(get("/anime/page").param(field, String.valueOf(id)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.pageNum").value(1))
                    .andExpect(jsonPath("$.data.pageSize").value(20))
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.pages").value(0))
                    .andExpect(jsonPath("$.data.rows").isEmpty());
        }
    }

    @org.junit.jupiter.api.Test
    void combinedAssociationParametersReachExistingPageQueryWithOriginalSorting() throws Exception {
        AnimeMapper mapper = mock(AnimeMapper.class);
        associationPageMvc(mapper).perform(get("/anime/page").param("personId", "12").param("seriesId", "8")
                        .param("companyId", "3").param("keyword", " abc ").param("pageNum", "2").param("pageSize", "10")
                        .param("sortBy", "PERSONAL_RATING").param("sortDirection", "ASC"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.pageNum").value(2));
        ArgumentCaptor<AnimePageDTO> dto = ArgumentCaptor.forClass(AnimePageDTO.class);
        verify(mapper).selectAnimePage(any(), dto.capture(), isNull(), isNull());
        assertEquals(12L, dto.getValue().getPersonId());
        assertEquals(8L, dto.getValue().getSeriesId());
        assertEquals(3L, dto.getValue().getCompanyId());
        assertEquals("abc", dto.getValue().getKeyword());
        assertEquals("PERSONAL_RATING", dto.getValue().getSortBy());
        assertEquals("ASC", dto.getValue().getSortDirection());
    }

    /**
     * 使用真实分页参数处理和模拟主查询，不配置或访问外部数据源。
     */
    private MockMvc associationPageMvc(AnimeMapper mapper) {
        AnimeServiceImpl service = new AnimeServiceImpl();
        ReflectionTestUtils.setField(service, "animeMapper", mapper);
        when(mapper.selectAnimePage(any(), any(), isNull(), isNull())).thenAnswer(invocation -> {
            Page<Anime> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of());
            return page;
        });
        AnimeController controller = new AnimeController();
        ReflectionTestUtils.setField(controller, "animeService", service);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"create,MISSING", "create,NULL", "create,EMPTY", "update,MISSING", "update,NULL", "update,EMPTY"})
    void animeWritesAllowMissingNullOrEmptyAirDate(String operation, String mode) throws Exception {
        when(animeService.createAnime(any())).thenAnswer(invocation -> {
            AnimeDTO dto = invocation.getArgument(0);
            return AnimeVO.builder().id(1L).airDate(dto.getAirDate()).build();
        });
        when(animeService.updateAnime(any())).thenAnswer(invocation -> {
            AnimeDTO dto = invocation.getArgument(0);
            return AnimeVO.builder().id(dto.getId()).airDate(dto.getAirDate()).build();
        });
        String date = switch (mode) {
            case "NULL" -> ",\"airDate\":null";
            case "EMPTY" -> ",\"airDate\":\"\"";
            default -> "";
        };
        mockMvc.perform(post("/anime/" + operation).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"日期待定\",\"broadcastTypeId\":1,\"adaptationTypeId\":1,"
                                + "\"regionId\":1,\"status\":1" + date + "}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.airDate").isEmpty());
        ArgumentCaptor<AnimeDTO> dto = ArgumentCaptor.forClass(AnimeDTO.class);
        if (operation.equals("create")) {
            verify(animeService).createAnime(dto.capture());
        } else {
            verify(animeService).updateAnime(dto.capture());
        }
        org.junit.jupiter.api.Assertions.assertNull(dto.getValue().getAirDate());
    }

    @ParameterizedTest
    @CsvSource({"create,2026-02-30", "update,2026-02-30", "create,invalid-date", "update,invalid-date"})
    void animeWritesStillRejectInvalidNonemptyAirDate(String operation, String date) throws Exception {
        mockMvc.perform(post("/anime/" + operation).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"无效日期\",\"broadcastTypeId\":1,\"adaptationTypeId\":1,"
                                + "\"regionId\":1,\"status\":1,\"airDate\":\"" + date + "\"}"))
                .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(animeService);
    }
}
