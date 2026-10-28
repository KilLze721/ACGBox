package org.killze.acgbox.controller;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import org.killze.acgbox.config.CosStorageProperties;
import org.killze.acgbox.service.impl.ImageUploadServiceImpl;
import org.killze.acgbox.exception.BusinessException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.zip.CRC32;
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
import org.killze.acgbox.dto.content.BookDTO;
import org.killze.acgbox.vo.content.BookVO;
import org.killze.acgbox.service.BookService;
import org.killze.acgbox.service.BookTypeService;
import org.killze.acgbox.service.impl.BookServiceImpl;
import org.killze.acgbox.dto.content.BookPageDTO;
import org.killze.acgbox.entity.content.Book;
import org.killze.acgbox.mapper.BookMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内容请求的反序列化、范围校验及图片上传测试，不启动应用、不加载私有配置。
 */
class SortOrderValidationTests {

    private MockMvc mockMvc;
    private BroadcastTypeService broadcastTypeService;
    private AdaptationTypeService adaptationTypeService;
    private AnimeService animeService;
    private BookService bookService;
    private BookTypeService bookTypeService;

    @BeforeEach
    void setUp() {
        broadcastTypeService = mock(BroadcastTypeService.class);
        adaptationTypeService = mock(AdaptationTypeService.class);
        animeService = mock(AnimeService.class);
        BroadcastTypeController broadcastTypeController = new BroadcastTypeController();
        AdaptationTypeController adaptationTypeController = new AdaptationTypeController();
        AnimeController animeController = new AnimeController();
        bookService = mock(BookService.class);
        bookTypeService = mock(BookTypeService.class);
        BookController bookController = new BookController();
        BookTypeController bookTypeController = new BookTypeController();
        ReflectionTestUtils.setField(bookController, "bookService", bookService);
        ReflectionTestUtils.setField(bookTypeController, "bookTypeService", bookTypeService);
        ReflectionTestUtils.setField(broadcastTypeController, "broadcastTypeService", broadcastTypeService);
        ReflectionTestUtils.setField(adaptationTypeController, "adaptationTypeService", adaptationTypeService);
        ReflectionTestUtils.setField(animeController, "animeService", animeService);
        mockMvc = MockMvcBuilders.standaloneSetup(broadcastTypeController, adaptationTypeController, animeController,
                        bookController, bookTypeController)
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

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"1\"", "1e0", "true"})
    void bookNumericInputsRejectImplicitConversionWithoutChangingAnime(String token) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        for (String field : java.util.List.of("status", "volumeCount", "chapterCount", "personalRatingScore",
                "bookTypeId", "adaptationTypeId", "regionId", "seriesId", "seriesSortOrder")) {
            var body = mapper.createObjectNode().put("name", "书籍").put("bookTypeId", 1L)
                    .put("adaptationTypeId", 1L).put("regionId", 1L).put("status", 0L);
            body.set(field, mapper.readTree(token));
            mockMvc.perform(post("/book/create").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(jsonPath("$.code").value(500));
        }
        for (String relation : java.util.List.of("\"companies\":[{\"companyId\":" + token + "}]",
                "\"companies\":[{\"companyId\":1,\"sortOrder\":" + token + "}]",
                "\"persons\":[{\"personId\":" + token + ",\"role\":\"作者\"}]",
                "\"persons\":[{\"personId\":1,\"role\":\"作者\",\"sortOrder\":" + token + "}]",
                "\"externalLinks\":[{\"url\":\"https://example.com\",\"sortOrder\":" + token + "}]",
                "\"tagIds\":[" + token + "]")) {
            mockMvc.perform(post("/book/update").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"id\":1,\"name\":\"书籍\",\"bookTypeId\":1,\"adaptationTypeId\":1,\"regionId\":1,\"status\":0," + relation + "}"))
                    .andExpect(jsonPath("$.code").value(500));
        }
        for (String path : java.util.List.of("book", "book-type")) {
            mockMvc.perform(post("/" + path + "/delete").contentType(MediaType.APPLICATION_JSON).content("[" + token + "]"))
                    .andExpect(jsonPath("$.code").value(500));
        }
        mockMvc.perform(post("/book-type/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"类型\",\"sortOrder\":" + token + "}"))
                .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(bookService, bookTypeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026", "2026-10", "2026-02-30", "", "2026-1-01", "0000-01-01"})
    void bookPublishDateDoesNotFillMissingPrecisionOrAcceptInvalidDates(String date) throws Exception {
        mockMvc.perform(post("/book/create").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"书籍\",\"bookTypeId\":1,\"adaptationTypeId\":1,\"regionId\":1,\"status\":0,\"publishDate\":\"" + date + "\"}"))
                .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(bookService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"2026-10-26\""})
    void bookPublishDateAcceptsNullOrFullDateAndPreservesZeros(String date) throws Exception {
        when(bookService.createBook(any())).thenAnswer(invocation -> {
            BookDTO dto = invocation.getArgument(0);
            return BookVO.builder().id(1L).publishDate(dto.getPublishDate()).status(dto.getStatus())
                    .volumeCount(dto.getVolumeCount()).personalRatingScore(dto.getPersonalRatingScore()).build();
        });
        mockMvc.perform(post("/book/create").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"书籍\",\"bookTypeId\":1,\"adaptationTypeId\":1,\"regionId\":1,\"status\":0,"
                        + "\"volumeCount\":0,\"personalRatingScore\":0,\"publishDate\":" + date + "}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.volumeCount").value(0)).andExpect(jsonPath("$.data.personalRatingScore").value(0));
    }

    @ParameterizedTest
    @CsvSource({"personId,0", "seriesId,-1", "companyId,2147483648", "bookTypeId,1.5", "adaptationTypeId,abc",
            "regionId,0x1", "ratingMin,1.5", "ratingMax,11", "status,6", "pageNum,0", "pageSize,101",
            "sortBy,BROADCAST_DATE", "sortDirection,INVALID", "tagIds,1.5", "publishStartDate,2026-13"})
    void bookPageRejectsInvalidLocalParametersBeforeQuery(String field, String value) throws Exception {
        BookMapper mapper = mock(BookMapper.class);
        bookPageMvc(mapper).perform(get("/book/page").param(field, value)).andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bookTypeId", "adaptationTypeId", "regionId", "companyId", "personId", "seriesId"})
    void bookPageReturnsEmptyForLegalUnknownIds(String field) throws Exception {
        BookMapper mapper = mock(BookMapper.class);
        bookPageMvc(mapper).perform(get("/book/page").param(field, "2147483647"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.rows").isEmpty());
    }

    /**
     * 复用现有测试文件验证上传，图片只在内存生成，COS 完全模拟。
     */
    @Nested
    class ImageUploads {
        private COSClient client;
        private ImageUploadServiceImpl uploadService;
        private MockMvc uploadMvc;

        @BeforeEach
        void prepareUploads() {
            client = mock(COSClient.class);
            var properties = new CosStorageProperties();
            properties.setEnabled(true);
            properties.setBucket("covers-1250000000");
            properties.setRegion("ap-guangzhou");
            properties.setSecretId("test-only-id");
            properties.setSecretKey("test-only-key");
            properties.setPublicBaseUrl("https://img.example.test///");
            properties.validate();
            uploadService = new ImageUploadServiceImpl();
            ReflectionTestUtils.setField(uploadService, "properties", properties);
            ReflectionTestUtils.setField(uploadService, "cosClientProvider",
                    new StaticListableBeanFactory(
                            java.util.Map.of("cosClient", client)).getBeanProvider(COSClient.class));
            ReflectionTestUtils.setField(uploadService, "clock", Clock.fixed(
                    Instant.parse("2026-12-31T16:00:00Z"), ZoneOffset.UTC));
            var result = new PutObjectResult();
            result.setETag("mock-confirmed-etag");
            when(client.putObject(any(PutObjectRequest.class))).thenReturn(result);
            var controller = new ImageUploadController();
            ReflectionTestUtils.setField(controller, "imageUploadService", uploadService);
            uploadMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
        }

        @ParameterizedTest
        @CsvSource({"ANIME,JpEg,jpeg,anime,jpg,image/jpeg", "BOOK,PNG,png,book,png,image/png"})
        void uploadsOriginalBytesWithTrustedMetadataAndShanghaiDate(String type, String suffix,
                String format, String directory, String extension, String mime) throws Exception {
            byte[] bytes = image(format, 12, 18);
            var file = new MockMultipartFile("file", "../../cover." + suffix,
                    "application/octet-stream", bytes);
            var result = uploadService.uploadImage(file, type);
            assertTrue(result.getObjectKey().matches(
                    directory + "/2027/01/[a-f0-9]{32}\\." + extension));
            assertEquals("https://img.example.test/" + result.getObjectKey(), result.getUrl());
            assertEquals(mime, result.getContentType());
            assertEquals((long) bytes.length, result.getSize());
            assertEquals(12L, result.getWidth());
            assertEquals(18L, result.getHeight());
            assertEquals(type, result.getTargetType());
            ArgumentCaptor<PutObjectRequest> captor =
                    ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(client).putObject(captor.capture());
            var request = captor.getValue();
            assertEquals("covers-1250000000", request.getBucketName());
            assertEquals(result.getObjectKey(), request.getKey());
            assertEquals(bytes.length, request.getMetadata().getContentLength());
            assertEquals(mime, request.getMetadata().getContentType());
            assertNull(request.getMetadata().getContentDisposition());
            assertNull(request.getCannedAcl());
            assertArrayEquals(bytes, request.getInputStream().readAllBytes());
            if (format.equals("png")) {
                var decoded = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                assertEquals(0, decoded.getRGB(0, 0) >>> 24);
            }
        }

        @Test
        void sameNameGeneratesNewKeysAndMonthUsesShanghaiInsteadOfUtc() throws Exception {
            var file = pngFile();
            ReflectionTestUtils.setField(uploadService, "clock", Clock.fixed(
                    Instant.parse("2026-01-31T15:59:59Z"), ZoneOffset.UTC));
            var before = uploadService.uploadImage(file, "ANIME");
            ReflectionTestUtils.setField(uploadService, "clock", Clock.fixed(
                    Instant.parse("2026-01-31T16:00:00Z"), ZoneOffset.UTC));
            var after = uploadService.uploadImage(file, "ANIME");
            assertTrue(before.getObjectKey().startsWith("anime/2026/01/"));
            assertTrue(after.getObjectKey().startsWith("anime/2026/02/"));
            assertNotEquals(before.getObjectKey(), after.getObjectKey());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "anime", "MANGA", "BOOK/../anime"})
        void rejectsUnsupportedTargetBeforeCos(String type) throws Exception {
            var file = pngFile();
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(file, type));
            verifyNoInteractions(client);
        }

        @Test
        void rejectsMissingEmptyAndOversizedFileBeforeCos() {
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(null, "ANIME"));
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(new MockMultipartFile(
                            "file", "cover.png", "image/png", new byte[0]), "BOOK"));
            uploadSizeCheck(new MockMultipartFile(
                    "file", "cover.png", "image/png", new byte[5242881]));
            // 不信任报告的文件大小，实际读取仍设上限。
            uploadSizeCheck(new MockMultipartFile(
                    "file", "cover.png", "image/png", new byte[5242881]) {
                @Override
                public long getSize() {
                    return 1;
                }
            });
            verifyNoInteractions(client);
        }

        private void uploadSizeCheck(MultipartFile file) {
            assertThrows(
                    MaxUploadSizeExceededException.class,
                    () -> uploadService.uploadImage(file, "ANIME"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"gif", "webp", "bmp", "svg", "zip", "png"})
        void rejectsUnsupportedSuffixOrAbsentDot(String suffix) throws Exception {
            String name = suffix.equals("png") ? "png" : "cover." + suffix;
            var file = new MockMultipartFile("file", name, "image/png", image("png", 1, 1));
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(file, "ANIME"));
            verifyNoInteractions(client);
        }

        @Test
        void rejectsDisguisedTruncatedCrcDamagedAndUndecodableImages() throws Exception {
            byte[] png = image("png", 4, 4);
            byte[] jpeg = image("jpeg", 4, 4);
            byte[] crcDamage = png.clone();
            crcDamage[29] ^= 1;
            for (byte[] bytes : java.util.List.of("not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    jpeg, java.util.Arrays.copyOf(png, png.length - 12), crcDamage)) {
                var file = new MockMultipartFile("file", "cover.png", "image/png", bytes);
                assertThrows(BusinessException.class,
                        () -> uploadService.uploadImage(file, "ANIME"));
            }
            byte[] truncated = java.util.Arrays.copyOf(jpeg, jpeg.length / 2);
            truncated[truncated.length - 2] = (byte) 255;
            truncated[truncated.length - 1] = (byte) 217;
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(new MockMultipartFile(
                            "file", "cover.jpg", "image/jpeg", truncated), "BOOK"));
            verifyNoInteractions(client);
        }

        @ParameterizedTest
        @CsvSource({"10001,1", "1,10001", "10000,2001", "0,1"})
        void rejectsDimensionsBeforeFullDecode(int width, int height) throws Exception {
            byte[] png = image("png", 1, 1);
            java.nio.ByteBuffer.wrap(png, 16, 8).putInt(width).putInt(height);
            var crc = new CRC32();
            crc.update(png, 12, 17);
            java.nio.ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
            var file = new MockMultipartFile("file", "cover.png", "image/png", png);
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(file, "ANIME"));
            verifyNoInteractions(client);
        }

        @Test
        void refusesDisabledAndUnconfirmedUploadsAndHidesSdkFailure() throws Exception {
            var file = pngFile();
            for (var error : java.util.List.of(new CosClientException("private-key"),
                    new CosServiceException("signature=private"))) {
                when(client.putObject(any(PutObjectRequest.class))).thenThrow(error);
                var exception = assertThrows(
                        BusinessException.class, () -> uploadService.uploadImage(file, "BOOK"));
                assertEquals("图片上传失败，请稍后重试", exception.getMessage());
            }
            when(client.putObject(any(PutObjectRequest.class))).thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(file, "BOOK"));
            when(client.putObject(any(PutObjectRequest.class)))
                    .thenReturn(new PutObjectResult());
            assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(file, "BOOK"));
            reset(client);
            ReflectionTestUtils.setField(uploadService, "properties", new CosStorageProperties());
            var exception = assertThrows(BusinessException.class,
                    () -> uploadService.uploadImage(file, "BOOK"));
            assertEquals("图片上传功能未启用", exception.getMessage());
            verifyNoInteractions(client);
        }

        @Test
        void multipartContractRejectsMissingMultipleAndWrongNamedFiles() throws Exception {
            var request = multipart("/uploads/images");
            uploadMvc.perform(request.param("targetType", "ANIME"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
            uploadMvc.perform(multipart("/uploads/images")
                            .file(pngFile()).file(pngFile()).param("targetType", "ANIME"))
                    .andExpect(jsonPath("$.code").value(500));
            uploadMvc.perform(multipart("/uploads/images")
                            .file("wrong", image("png", 1, 1)).param("targetType", "ANIME"))
                    .andExpect(jsonPath("$.code").value(500));
            uploadMvc.perform(multipart("/uploads/images")
                            .file(pngFile()))
                    .andExpect(jsonPath("$.code").value(500));
            uploadMvc.perform(multipart("/uploads/images")
                            .file(pngFile()).param("targetType", "ANIME", "BOOK"))
                    .andExpect(jsonPath("$.code").value(500));
            verifyNoInteractions(client);
        }

        @Test
        void multipartSuccessUsesConfiguredHostAndOversizeHasHttp413() throws Exception {
            uploadMvc.perform(multipart("/uploads/images")
                            .file(pngFile()).param("targetType", "BOOK").header("Host", "evil.example")
                            .param("bucket", "evil").param("objectKey", "evil.png").param("publicBaseUrl", "https://evil.example"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("https://img.example.test/book/")))
                    .andExpect(jsonPath("$.data.width").value(3));
            uploadMvc.perform(multipart("/uploads/images")
                            .file(new MockMultipartFile("file", "cover.png", "image/png",
                                    new byte[5242881])).param("targetType", "ANIME"))
                    .andExpect(status().is(413)).andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("图片不能超过 5 MiB，上传请求不能超过 6 MiB"));
            verify(client, times(1)).putObject(any(PutObjectRequest.class));
        }

        private MockMultipartFile pngFile() throws Exception {
            return new MockMultipartFile("file", "cover.png", "image/png", image("png", 3, 5));
        }

        private byte[] image(String format, int width, int height) throws Exception {
            var image = new BufferedImage(width, height, format.equals("png")
                    ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            try (var output = new ByteArrayOutputStream()) {
                assertTrue(javax.imageio.ImageIO.write(image, format, output));
                return output.toByteArray();
            } finally {
                image.flush();
            }
        }
    }

    private MockMvc bookPageMvc(BookMapper mapper) {
        BookServiceImpl service = new BookServiceImpl();
        ReflectionTestUtils.setField(service, "bookMapper", mapper);
        when(mapper.selectBookPage(any(), any(), isNull(), isNull())).thenAnswer(invocation -> {
            Page<Book> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of());
            return page;
        });
        BookController controller = new BookController();
        ReflectionTestUtils.setField(controller, "bookService", service);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
