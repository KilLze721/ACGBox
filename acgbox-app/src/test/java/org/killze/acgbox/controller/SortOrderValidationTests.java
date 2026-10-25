package org.killze.acgbox.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.dto.content.BroadcastTypeDTO;
import org.killze.acgbox.exception.GlobalExceptionHandler;
import org.killze.acgbox.service.AdaptationTypeService;
import org.killze.acgbox.service.AnimeService;
import org.killze.acgbox.service.BroadcastTypeService;
import org.killze.acgbox.vo.content.AdaptationTypeVO;
import org.killze.acgbox.vo.content.BroadcastTypeVO;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
