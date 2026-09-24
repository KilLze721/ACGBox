package org.killze.acgbox.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.entity.content.Alias;
import org.killze.acgbox.entity.content.Anime;
import org.killze.acgbox.entity.content.BroadcastType;
import org.killze.acgbox.entity.content.Company;
import org.killze.acgbox.entity.content.CompanyRelation;
import org.killze.acgbox.entity.content.ExternalLink;
import org.killze.acgbox.entity.content.PersonalRating;
import org.killze.acgbox.entity.content.Region;
import org.killze.acgbox.entity.content.Series;
import org.killze.acgbox.entity.content.SeriesItem;
import org.killze.acgbox.entity.content.Tag;
import org.killze.acgbox.entity.content.TagRelation;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AdaptationTypeMapper;
import org.killze.acgbox.mapper.AliasMapper;
import org.killze.acgbox.mapper.AnimeMapper;
import org.killze.acgbox.mapper.BroadcastTypeMapper;
import org.killze.acgbox.mapper.CompanyMapper;
import org.killze.acgbox.mapper.CompanyRelationMapper;
import org.killze.acgbox.mapper.ExternalLinkMapper;
import org.killze.acgbox.mapper.PersonalRatingMapper;
import org.killze.acgbox.mapper.RegionMapper;
import org.killze.acgbox.mapper.SeriesItemMapper;
import org.killze.acgbox.mapper.SeriesMapper;
import org.killze.acgbox.mapper.TagMapper;
import org.killze.acgbox.mapper.TagRelationMapper;
import org.killze.acgbox.service.AnimeService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.AnimePageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 动画分页查询集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnimePageServiceIntegrationTests {

    @Autowired
    private AnimeService animeService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnimeMapper animeMapper;

    @Autowired
    private AliasMapper aliasMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private TagRelationMapper tagRelationMapper;

    @Autowired
    private CompanyMapper companyMapper;

    @Autowired
    private CompanyRelationMapper companyRelationMapper;

    @Autowired
    private ExternalLinkMapper externalLinkMapper;

    @Autowired
    private SeriesMapper seriesMapper;

    @Autowired
    private SeriesItemMapper seriesItemMapper;

    @Autowired
    private PersonalRatingMapper personalRatingMapper;

    @Autowired
    private BroadcastTypeMapper broadcastTypeMapper;

    @Autowired
    private AdaptationTypeMapper adaptationTypeMapper;

    @Autowired
    private RegionMapper regionMapper;

    private String prefix;
    private BroadcastType broadcastType;
    private AdaptationType adaptationType;
    private Region region;
    private Tag tagA;
    private Tag tagB;
    private Tag tagC;
    private Company companyA;
    private Anime animeA;
    private Anime animeB;
    private Anime animeC;
    private Anime animeD;
    private Anime animeWithoutRating;

    @BeforeEach
    void setUp() {
        prefix = "PAGE_TEST_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        broadcastType = BroadcastType.builder()
                .name(prefix + "放送类型")
                .createdAt(now)
                .updatedAt(now)
                .build();
        broadcastTypeMapper.insert(broadcastType);
        adaptationType = AdaptationType.builder()
                .name(prefix + "改编类型")
                .createdAt(now)
                .updatedAt(now)
                .build();
        adaptationTypeMapper.insert(adaptationType);
        region = Region.builder()
                .name(prefix + "地区")
                .createdAt(now)
                .updatedAt(now)
                .build();
        regionMapper.insert(region);

        tagA = createTag(prefix + "标签A", now);
        tagB = createTag(prefix + "标签B", now);
        tagC = createTag(prefix + "标签C", now);
        companyA = createCompany(prefix + "公司A", now);
        Company companyB = createCompany(prefix + "公司B", now);
        Series seriesA = createSeries(prefix + "关键词命中系列", now);
        Series seriesB = createSeries(prefix + "第二系列", now);

        animeA = createAnime(prefix + "关键词命中主名称", LocalDate.of(2006, 7, 1), 3L, now);
        animeB = createAnime(prefix + "第二动画", LocalDate.of(2006, 8, 15), 2L, now);
        animeC = createAnime(prefix + "并列动画一", LocalDate.of(2008, 1, 1), 3L, now);
        animeD = createAnime(prefix + "并列动画二", LocalDate.of(2008, 1, 1), 3L, now);
        animeWithoutRating = createAnime(prefix + "无评分动画", LocalDate.of(2009, 1, 1), 3L, now);

        createAlias(animeA.getId(), prefix + "关键词命中别名一");
        createAlias(animeA.getId(), prefix + "关键词命中别名二");
        createAlias(animeB.getId(), prefix + "第二别名");
        createTagRelation(animeA.getId(), tagA.getId());
        createTagRelation(animeA.getId(), tagB.getId());
        createTagRelation(animeB.getId(), tagA.getId());
        createTagRelation(animeC.getId(), tagC.getId());
        createTagRelation(animeD.getId(), tagC.getId());
        createCompanyRelation(animeA.getId(), companyA.getId());
        createCompanyRelation(animeB.getId(), companyB.getId());
        createSeriesItem(animeA.getId(), seriesA.getId());
        createSeriesItem(animeB.getId(), seriesB.getId());
        createExternalLink(animeA.getId());
        createRating(animeA.getId(), "4.0", now);
        createRating(animeB.getId(), "6.0", now);
        createRating(animeC.getId(), "8.0", now);
        createRating(animeD.getId(), "8.0", now);
    }

    @Test
    void shouldBindPageParameterAndReturnPaginationMetadata() throws Exception {
        mockMvc.perform(get("/anime/page")
                        .param("page", "1")
                        .param("pageSize", "1")
                        .param("tagIds", tagA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.rows.length()").value(1));
    }

    @Test
    void shouldReturnRatingFieldWhenRatingFormatIsInvalid() throws Exception {
        mockMvc.perform(get("/anime/page")
                        .param("ratingMin", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("ratingMin")));
    }

    @Test
    void shouldCreateQueryUpdateAndDeleteAnimeDescription() throws Exception {
        AnimeDTO animeDTO = new AnimeDTO();
        animeDTO.setName(prefix + "简介测试动画");
        animeDTO.setEpisodeCount(12L);
        animeDTO.setBroadcastTypeId(broadcastType.getId());
        animeDTO.setAdaptationTypeId(adaptationType.getId());
        animeDTO.setRegionId(region.getId());
        animeDTO.setAirDate(LocalDate.of(2026, 9, 23));
        animeDTO.setStatus(3L);
        animeDTO.setDescription("创建时的动画简介");

        AnimePageVO pageAnime;
        var createdAnime = animeService.createAnime(animeDTO);
        assertEquals("创建时的动画简介", createdAnime.getDescription());
        assertEquals("创建时的动画简介", animeService.getAnimeById(createdAnime.getId()).getDescription());

        animeDTO.setId(createdAnime.getId());
        animeDTO.setDescription("修改后的动画简介");
        assertEquals("修改后的动画简介", animeService.updateAnime(animeDTO).getDescription());

        pageAnime = animeService.pageAnime(buildKeywordQuery("简介测试动画")).getRows().getFirst();
        assertEquals(createdAnime.getId(), pageAnime.getId());
        mockMvc.perform(get("/anime/page")
                        .param("keyword", prefix + "简介测试动画"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].description").doesNotExist());

        animeDTO.setDescription(null);
        assertNull(animeService.updateAnime(animeDTO).getDescription());

        animeService.deleteAnime(List.of(createdAnime.getId()));
        assertNull(animeMapper.selectById(createdAnime.getId()));
    }

    @Test
    void shouldQueryKeywordAndRemoveDuplicateAnimeRows() {
        assertEquals(Set.of(animeA.getId()), queryIds(buildKeywordQuery("关键词命中主名称")));
        assertEquals(Set.of(animeA.getId()), queryIds(buildKeywordQuery("关键词命中别名一")));
        assertEquals(Set.of(animeA.getId()), queryIds(buildKeywordQuery("关键词命中系列")));

        PageVO<AnimePageVO> result = animeService.pageAnime(buildKeywordQuery("关键词命中"));
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRows().size());
        AnimePageVO anime = result.getRows().getFirst();
        assertEquals(2, anime.getAliasNames().size());
        assertEquals(2, anime.getTags().size());
        assertEquals(companyA.getName(), anime.getCompanies().getFirst().getCompanyName());
        assertEquals(1, anime.getExternalLinks().size());
        assertNotNull(anime.getSeries());
        assertEquals(broadcastType.getName(), anime.getBroadcastType().getName());
        assertEquals(adaptationType.getName(), anime.getAdaptationType().getName());
        assertEquals(region.getName(), anime.getRegion().getName());
    }

    @Test
    void shouldSupportAllAnyDefaultAndCombinedFilters() {
        AnimePageDTO defaultAll = buildTagQuery(List.of(tagA.getId(), tagB.getId()), null);
        assertEquals(Set.of(animeA.getId()), queryIds(defaultAll));

        AnimePageDTO any = buildTagQuery(List.of(tagA.getId(), tagB.getId()), "ANY");
        assertEquals(Set.of(animeA.getId(), animeB.getId()), queryIds(any));

        AnimePageDTO duplicateTags = buildTagQuery(List.of(tagA.getId(), tagA.getId()), null);
        assertEquals(Set.of(animeA.getId(), animeB.getId()), queryIds(duplicateTags));

        AnimePageDTO emptyTags = buildTagQuery(List.of(), null);
        emptyTags.setBroadcastTypeId(broadcastType.getId());
        assertEquals(5L, animeService.pageAnime(emptyTags).getTotal());

        AnimePageDTO combined = buildTagQuery(List.of(tagA.getId()), null);
        combined.setBroadcastTypeId(broadcastType.getId());
        combined.setAdaptationTypeId(adaptationType.getId());
        combined.setRegionId(region.getId());
        combined.setStatus(3L);
        combined.setCompanyId(companyA.getId());
        combined.setBroadcastStartDate("2006-07");
        combined.setBroadcastEndDate("2006-07");
        combined.setRatingMin(new BigDecimal("4.0"));
        combined.setRatingMax(new BigDecimal("4.0"));
        assertEquals(Set.of(animeA.getId()), queryIds(combined));
    }

    @Test
    void shouldSupportYearMonthAndOpenDateRanges() {
        assertEquals(
                Set.of(animeA.getId(), animeB.getId()),
                queryIds(buildDateQuery("2006", "2006"))
        );
        assertEquals(
                Set.of(animeA.getId()),
                queryIds(buildDateQuery("2006-07", "2006-07"))
        );
        assertEquals(
                Set.of(
                        animeB.getId(),
                        animeC.getId(),
                        animeD.getId(),
                        animeWithoutRating.getId()
                ),
                queryIds(buildDateQuery("2006-08", null))
        );
        assertEquals(
                Set.of(animeA.getId()),
                queryIds(buildDateQuery(null, "2006-07"))
        );

        assertThrows(BusinessException.class, () -> animeService.pageAnime(buildDateQuery("2006-13", null)));
        assertThrows(BusinessException.class, () -> animeService.pageAnime(buildDateQuery("2006-7", null)));
        assertThrows(BusinessException.class, () -> animeService.pageAnime(buildDateQuery("2007", "2006")));
    }

    @Test
    void shouldSupportRatingFiltersNullLastAndStableSorting() {
        assertEquals(Set.of(animeA.getId()), queryIds(buildRatingQuery("4.0", "4.0")));
        assertEquals(
                Set.of(animeA.getId(), animeB.getId()),
                queryIds(buildRatingQuery("4.0", "6.0"))
        );
        assertEquals(Set.of(animeA.getId()), queryIds(buildRatingQuery(null, "4.0")));
        assertEquals(
                Set.of(animeB.getId(), animeC.getId(), animeD.getId()),
                queryIds(buildRatingQuery("6.0", null))
        );

        AnimePageDTO ratingSort = buildTagQuery(List.of(tagC.getId()), null);
        ratingSort.setSortBy("PERSONAL_RATING");
        ratingSort.setSortDirection("DESC");
        List<Long> ratingSortedIds = animeService.pageAnime(ratingSort)
                .getRows()
                .stream()
                .map(AnimePageVO::getId)
                .toList();
        assertEquals(List.of(animeD.getId(), animeC.getId()), ratingSortedIds);

        AnimePageDTO allRatingSort = new AnimePageDTO();
        allRatingSort.setBroadcastTypeId(broadcastType.getId());
        allRatingSort.setSortBy("PERSONAL_RATING");
        allRatingSort.setSortDirection("ASC");
        List<Long> allRatingSortedIds = animeService.pageAnime(allRatingSort)
                .getRows()
                .stream()
                .map(AnimePageVO::getId)
                .toList();
        assertEquals(animeWithoutRating.getId(), allRatingSortedIds.getLast());
    }

    @Test
    void shouldReturnCorrectPaginationAndValidateParameters() {
        AnimePageDTO firstPage = buildTagQuery(List.of(tagA.getId()), null);
        firstPage.setPage(1L);
        firstPage.setPageSize(1L);
        PageVO<AnimePageVO> firstResult = animeService.pageAnime(firstPage);
        assertEquals(1L, firstResult.getPageNum());
        assertEquals(1L, firstResult.getPageSize());
        assertEquals(2L, firstResult.getTotal());
        assertEquals(2L, firstResult.getPages());
        assertEquals(animeB.getId(), firstResult.getRows().getFirst().getId());

        AnimePageDTO secondPage = buildTagQuery(List.of(tagA.getId()), null);
        secondPage.setPage(2L);
        secondPage.setPageSize(1L);
        assertEquals(animeA.getId(), animeService.pageAnime(secondPage).getRows().getFirst().getId());

        AnimePageDTO overPage = buildTagQuery(List.of(tagA.getId()), null);
        overPage.setPage(3L);
        overPage.setPageSize(1L);
        assertTrue(animeService.pageAnime(overPage).getRows().isEmpty());

        assertTrue(animeService.pageAnime(buildKeywordQuery("不存在")).getRows().isEmpty());

        AnimePageDTO invalidRating = new AnimePageDTO();
        invalidRating.setRatingMin(new BigDecimal("6.0"));
        invalidRating.setRatingMax(new BigDecimal("4.0"));
        assertThrows(BusinessException.class, () -> animeService.pageAnime(invalidRating));

        AnimePageDTO invalidSort = new AnimePageDTO();
        invalidSort.setSortBy("UNKNOWN");
        assertThrows(BusinessException.class, () -> animeService.pageAnime(invalidSort));

        AnimePageDTO invalidTagMode = new AnimePageDTO();
        invalidTagMode.setTagMatchMode("UNKNOWN");
        assertThrows(BusinessException.class, () -> animeService.pageAnime(invalidTagMode));

        AnimePageDTO invalidPage = new AnimePageDTO();
        invalidPage.setPage(0L);
        assertThrows(BusinessException.class, () -> animeService.pageAnime(invalidPage));
    }

    private AnimePageDTO buildKeywordQuery(String keyword) {
        AnimePageDTO pageDTO = new AnimePageDTO();
        pageDTO.setKeyword(prefix + keyword);
        return pageDTO;
    }

    private AnimePageDTO buildTagQuery(List<Long> tagIds, String tagMatchMode) {
        AnimePageDTO pageDTO = new AnimePageDTO();
        pageDTO.setTagIds(tagIds);
        pageDTO.setTagMatchMode(tagMatchMode);
        return pageDTO;
    }

    private AnimePageDTO buildDateQuery(String startDate, String endDate) {
        AnimePageDTO pageDTO = new AnimePageDTO();
        pageDTO.setBroadcastTypeId(broadcastType.getId());
        pageDTO.setBroadcastStartDate(startDate);
        pageDTO.setBroadcastEndDate(endDate);
        return pageDTO;
    }

    private AnimePageDTO buildRatingQuery(String ratingMin, String ratingMax) {
        AnimePageDTO pageDTO = new AnimePageDTO();
        pageDTO.setBroadcastTypeId(broadcastType.getId());
        pageDTO.setRatingMin(ratingMin == null ? null : new BigDecimal(ratingMin));
        pageDTO.setRatingMax(ratingMax == null ? null : new BigDecimal(ratingMax));
        return pageDTO;
    }

    private Set<Long> queryIds(AnimePageDTO pageDTO) {
        return animeService.pageAnime(pageDTO)
                .getRows()
                .stream()
                .map(AnimePageVO::getId)
                .collect(Collectors.toSet());
    }

    private Tag createTag(String name, LocalDateTime now) {
        Tag tag = Tag.builder()
                .name(name)
                .createdAt(now)
                .updatedAt(now)
                .build();
        tagMapper.insert(tag);
        return tag;
    }

    private Company createCompany(String name, LocalDateTime now) {
        Company company = Company.builder()
                .name(name)
                .description(name)
                .createdAt(now)
                .updatedAt(now)
                .build();
        companyMapper.insert(company);
        return company;
    }

    private Series createSeries(String name, LocalDateTime now) {
        Series series = Series.builder()
                .name(name)
                .description(name)
                .createdAt(now)
                .updatedAt(now)
                .build();
        seriesMapper.insert(series);
        return series;
    }

    private Anime createAnime(String name, LocalDate airDate, Long status, LocalDateTime now) {
        Anime anime = Anime.builder()
                .name(name)
                .episodeCount(12L)
                .broadcastTypeId(broadcastType.getId())
                .adaptationTypeId(adaptationType.getId())
                .regionId(region.getId())
                .airDate(airDate)
                .coverImageUrl(prefix + "封面")
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
        animeMapper.insert(anime);
        return anime;
    }

    private void createAlias(Long animeId, String aliasName) {
        aliasMapper.insert(
                Alias.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .aliasName(aliasName)
                        .build()
        );
    }

    private void createTagRelation(Long animeId, Long tagId) {
        tagRelationMapper.insert(
                TagRelation.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .tagId(tagId)
                        .build()
        );
    }

    private void createCompanyRelation(Long animeId, Long companyId) {
        companyRelationMapper.insert(
                CompanyRelation.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .companyId(companyId)
                        .role("制作")
                        .build()
        );
    }

    private void createSeriesItem(Long animeId, Long seriesId) {
        seriesItemMapper.insert(
                SeriesItem.builder()
                        .seriesId(seriesId)
                        .workType("anime")
                        .workId(animeId)
                        .sortOrder(0L)
                        .build()
        );
    }

    private void createExternalLink(Long animeId) {
        externalLinkMapper.insert(
                ExternalLink.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .title("测试链接")
                        .url("https://example.com/" + prefix)
                        .sortOrder(0L)
                        .build()
        );
    }

    private void createRating(Long animeId, String score, LocalDateTime now) {
        personalRatingMapper.insert(
                PersonalRating.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .score(new BigDecimal(score))
                        .createdAt(now)
                        .updatedAt(now)
                        .build()
        );
    }
}
