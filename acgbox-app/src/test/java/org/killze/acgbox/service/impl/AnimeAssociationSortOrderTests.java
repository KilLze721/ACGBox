package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.dto.content.CompanyRelationDTO;
import org.killze.acgbox.dto.content.PersonRelationDTO;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.entity.content.*;
import org.killze.acgbox.mapper.*;
import org.killze.acgbox.vo.content.AnimeVO;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 动画关联排序测试，使用带内存记录的模拟 Mapper 验证写入、查询条件和响应，不访问外部服务。
 */
class AnimeAssociationSortOrderTests {

    private AnimeServiceImpl service;
    private AnimeMapper animeMapper;
    private AliasMapper aliasMapper;
    private CompanyRelationMapper companyRelationMapper;
    private CompanyMapper companyMapper;
    private PersonMapper personMapper;
    private PersonRelationMapper personRelationMapper;
    private final Map<Long, Person> people = new HashMap<>();
    private final List<PersonRelation> personRelations = new ArrayList<>();
    private final AtomicLong personRelationId = new AtomicLong(100);
    private Anime anime;
    private final List<Alias> aliases = new ArrayList<>();
    private final List<CompanyRelation> relations = new ArrayList<>();
    private final AtomicLong aliasId = new AtomicLong(100);
    private final AtomicLong relationId = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(Anime.class, Alias.class, CompanyRelation.class, Company.class,
                ExternalLink.class, TagRelation.class, SeriesItem.class, PersonalRating.class,
                BroadcastType.class, AdaptationType.class, Region.class, Person.class, PersonRelation.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
        service = new AnimeServiceImpl();
        animeMapper = mock(AnimeMapper.class);
        aliasMapper = mock(AliasMapper.class);
        companyRelationMapper = mock(CompanyRelationMapper.class);
        companyMapper = mock(CompanyMapper.class);
        personMapper = mock(PersonMapper.class);
        personRelationMapper = mock(PersonRelationMapper.class);
        ReflectionTestUtils.setField(service, "personMapper", personMapper);
        ReflectionTestUtils.setField(service, "personRelationMapper", personRelationMapper);
        people.put(3L, Person.builder().id(3L).name("人员三").build());
        people.put(9L, Person.builder().id(9L).name("人员九").build());
        when(personMapper.selectByIds(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return people.values().stream().filter(person -> ids.contains(person.getId())).toList();
        });
        when(personRelationMapper.insert(any(PersonRelation.class))).thenAnswer(invocation -> {
            PersonRelation relation = invocation.getArgument(0);
            relation.setId(personRelationId.incrementAndGet());
            personRelations.add(relation);
            return 1;
        });
        when(personRelationMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PersonRelation> query = invocation.getArgument(0);
            assertAssociationQuery(query, true);
            return personRelations.stream().filter(relation -> relation.getTargetType().equals("ANIME")
                            && query.getParamNameValuePairs().containsValue(relation.getTargetId()))
                    .sorted(Comparator.comparing(PersonRelation::getSortOrder).thenComparing(PersonRelation::getId)).toList();
        });
        when(personRelationMapper.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PersonRelation> query = invocation.getArgument(0);
            assertAssociationQuery(query, false);
            personRelations.removeIf(relation -> relation.getTargetType().equals("ANIME")
                    && query.getParamNameValuePairs().containsValue(relation.getTargetId()));
            return 1;
        });
        ReflectionTestUtils.setField(service, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(service, "aliasMapper", aliasMapper);
        ReflectionTestUtils.setField(service, "companyRelationMapper", companyRelationMapper);
        ReflectionTestUtils.setField(service, "companyMapper", companyMapper);
        ReflectionTestUtils.setField(service, "broadcastTypeMapper", mock(BroadcastTypeMapper.class));
        ReflectionTestUtils.setField(service, "adaptationTypeMapper", mock(AdaptationTypeMapper.class));
        ReflectionTestUtils.setField(service, "regionMapper", mock(RegionMapper.class));
        ReflectionTestUtils.setField(service, "externalLinkMapper", mock(ExternalLinkMapper.class));
        ReflectionTestUtils.setField(service, "tagMapper", mock(TagMapper.class));
        ReflectionTestUtils.setField(service, "tagRelationMapper", mock(TagRelationMapper.class));
        ReflectionTestUtils.setField(service, "seriesMapper", mock(SeriesMapper.class));
        ReflectionTestUtils.setField(service, "seriesItemMapper", mock(SeriesItemMapper.class));
        ReflectionTestUtils.setField(service, "personalRatingMapper", mock(PersonalRatingMapper.class));
        anime = Anime.builder().id(1L).name("测试动画").broadcastTypeId(1L).adaptationTypeId(1L)
                .regionId(1L).airDate(LocalDate.of(2026, 1, 1)).status(3L).build();
        when(animeMapper.selectById(1L)).thenReturn(anime);
        when(animeMapper.insert(any(Anime.class))).thenAnswer(invocation -> {
            Anime entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(aliasMapper.insert(any(Alias.class))).thenAnswer(invocation -> {
            Alias entity = invocation.getArgument(0);
            entity.setId(aliasId.incrementAndGet());
            aliases.add(entity);
            return 1;
        });
        when(companyRelationMapper.insert(any(CompanyRelation.class))).thenAnswer(invocation -> {
            CompanyRelation entity = invocation.getArgument(0);
            entity.setId(relationId.incrementAndGet());
            relations.add(entity);
            return 1;
        });
        when(aliasMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<Alias> query = invocation.getArgument(0);
            assertAssociationQuery(query, true);
            return aliases.stream().filter(value -> value.getTargetType().equals("ANIME")
                            && query.getParamNameValuePairs().containsValue(value.getTargetId()))
                    .sorted(Comparator.comparing(Alias::getSortOrder).thenComparing(Alias::getId)).toList();
        });
        when(companyRelationMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<CompanyRelation> query = invocation.getArgument(0);
            assertAssociationQuery(query, true);
            return relations.stream().filter(value -> value.getTargetType().equals("ANIME")
                            && query.getParamNameValuePairs().containsValue(value.getTargetId()))
                    .sorted(Comparator.comparing(CompanyRelation::getSortOrder).thenComparing(CompanyRelation::getId)).toList();
        });
        when(aliasMapper.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<Alias> query = invocation.getArgument(0);
            assertAssociationQuery(query, false);
            aliases.removeIf(value -> value.getTargetType().equals("ANIME")
                    && query.getParamNameValuePairs().containsValue(value.getTargetId()));
            return 1;
        });
        when(companyRelationMapper.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<CompanyRelation> query = invocation.getArgument(0);
            assertAssociationQuery(query, false);
            relations.removeIf(value -> value.getTargetType().equals("ANIME")
                    && query.getParamNameValuePairs().containsValue(value.getTargetId()));
            return 1;
        });
        when(companyMapper.selectById(anyLong())).thenAnswer(invocation ->
                Company.builder().id(invocation.getArgument(0)).name("公司" + invocation.getArgument(0)).build());
        when(companyMapper.selectByIds(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> Company.builder().id(id).name("公司" + id).build()).toList();
        });
    }

    @Test
    void createCleansAliasesAndUsesContinuousArrayPositions() {
        AnimeDTO dto = animeDTO();
        dto.setAliasNames(Arrays.asList(" Z ", "", "A", "Z", null, " B ", "A"));
        AnimeVO created = service.createAnime(dto);
        assertEquals(List.of("Z", "A", "B"), created.getAliasNames());
        assertEquals(List.of(0L, 1L, 2L), aliases.stream().map(Alias::getSortOrder).toList());
        assertEquals(created.getAliasNames(), service.getAnimeById(1L).getAliasNames());
        assertEquals(created.getAliasNames(), page().getFirst().getAliasNames());
    }

    @Test
    void createKeepsFirstCompanyRoleAndSortAndMatchesRefresh() {
        AnimeDTO dto = animeDTO();
        dto.setCompanies(Arrays.asList(null, company(8L, " 制作 ", 4L), company(3L, "发行", -2L),
                company(8L, "忽略重复", -100L), company(9L, " ", null), company(2L, "企划", 4L)));
        AnimeVO created = service.createAnime(dto);
        assertEquals(List.of(4L, -2L, 2L, 4L), relations.stream().map(CompanyRelation::getSortOrder).toList());
        assertEquals(List.of(3L, 9L, 8L, 2L), created.getCompanies().stream().map(value -> value.getCompanyId()).toList());
        assertEquals("制作", created.getCompanies().get(2).getRole());
        assertNull(created.getCompanies().get(1).getRole());
        assertEquals(created.getCompanies(), service.getAnimeById(1L).getCompanies());
        var companies = page().getFirst().getCompanies();
        assertEquals(List.of(3L, 9L, 8L, 2L), companies.stream().map(value -> value.getCompanyId()).toList());
        assertEquals(List.of(-2L, 2L, 4L, 4L), companies.stream().map(value -> value.getSortOrder()).toList());
        assertEquals("公司3", companies.getFirst().getCompanyName());
    }

    @Test
    void oldUpdatePreservesExistingCompanySortAndAssignsNewPositionAfterCleaning() {
        relations.add(relation(3L, "ANIME", 1L, 8L, "旧职责", 40L));
        relations.add(relation(4L, "ANIME", 1L, 3L, "旧职责", -5L));
        addOtherTargets();
        AnimeDTO dto = animeDTO();
        dto.setId(1L);
        dto.setAliasNames(Arrays.asList(" B ", "A", "B", ""));
        dto.setCompanies(Arrays.asList(null, company(8L, "新职责", null), company(8L, "重复", 1L),
                company(3L, "发行", null), company(9L, "新增", null)));
        AnimeVO updated = service.updateAnime(dto);
        assertEquals(List.of("B", "A"), updated.getAliasNames());
        assertEquals(List.of(0L, 1L), aliases.stream().filter(value -> value.getTargetType().equals("ANIME")
                && value.getTargetId().equals(1L)).map(Alias::getSortOrder).toList());
        assertEquals(List.of(3L, 9L, 8L), updated.getCompanies().stream().map(value -> value.getCompanyId()).toList());
        assertEquals(List.of(-5L, 2L, 40L), updated.getCompanies().stream().map(value -> value.getSortOrder()).toList());
        assertEquals("新职责", updated.getCompanies().getLast().getRole());
        assertEquals(updated.getCompanies(), service.getAnimeById(1L).getCompanies());
        assertEquals(2L, relations.stream().filter(value -> !value.getTargetType().equals("ANIME")
                || !value.getTargetId().equals(1L)).count());
        assertEquals(2L, aliases.stream().filter(value -> !value.getTargetType().equals("ANIME")
                || !value.getTargetId().equals(1L)).count());
        assertEquals(List.of(3L, 9L, 8L), page().getFirst().getCompanies().stream().map(value -> value.getCompanyId()).toList());
    }

    @Test
    void explicitUpdateSupportsZeroNegativeBoundariesAndStableAssociationIdTies() {
        relations.add(relation(3L, "ANIME", 1L, 8L, "旧职责", 40L));
        AnimeDTO dto = animeDTO();
        dto.setId(1L);
        dto.setCompanies(List.of(company(8L, "制作", 0L), company(3L, "发行", 0L),
                company(4L, "企划", -2147483648L), company(5L, "协力", 2147483647L)));
        AnimeVO updated = service.updateAnime(dto);
        assertEquals(List.of(4L, 8L, 3L, 5L), updated.getCompanies().stream().map(value -> value.getCompanyId()).toList());
        assertEquals(List.of(-2147483648L, 0L, 0L, 2147483647L), updated.getCompanies().stream().map(value -> value.getSortOrder()).toList());
        assertEquals(updated.getCompanies(), service.getAnimeById(1L).getCompanies());
    }

    @Test
    void emptyUpdateClearsOnlyThisAnimeAssociations() {
        aliases.add(Alias.builder().id(1L).targetType("ANIME").targetId(1L).aliasName("旧别名").sortOrder(0L).build());
        relations.add(relation(3L, "ANIME", 1L, 8L, "旧职责", 40L));
        addOtherTargets();
        AnimeDTO dto = animeDTO();
        dto.setId(1L);
        AnimeVO updated = service.updateAnime(dto);
        assertTrue(updated.getAliasNames().isEmpty());
        assertTrue(updated.getCompanies().isEmpty());
        assertEquals(2, aliases.size());
        assertEquals(2, relations.size());
    }

    @Test
    void detailAndPageReadStoredSortWithIdTieBreakNotCompanyId() {
        aliases.addAll(List.of(
                Alias.builder().id(3L).targetType("ANIME").targetId(1L).aliasName("第三").sortOrder(0L).build(),
                Alias.builder().id(2L).targetType("ANIME").targetId(1L).aliasName("第一").sortOrder(-1L).build(),
                Alias.builder().id(1L).targetType("ANIME").targetId(1L).aliasName("第二").sortOrder(0L).build()));
        relations.addAll(List.of(relation(3L, "ANIME", 1L, 2L, "发行", 0L),
                relation(2L, "ANIME", 1L, 9L, "制作", -1L), relation(1L, "ANIME", 1L, 8L, "协力", 0L)));
        addOtherTargets();
        AnimeVO detail = service.getAnimeById(1L);
        assertEquals(List.of("第一", "第二", "第三"), detail.getAliasNames());
        assertEquals(List.of(9L, 8L, 2L), detail.getCompanies().stream().map(value -> value.getCompanyId()).toList());
        var rows = page();
        assertEquals(detail.getAliasNames(), rows.getFirst().getAliasNames());
        assertEquals(List.of(9L, 8L, 2L), rows.getFirst().getCompanies().stream().map(value -> value.getCompanyId()).toList());
        assertEquals(List.of("其他动画"), rows.getLast().getAliasNames());
        assertEquals(List.of(20L), rows.getLast().getCompanies().stream().map(value -> value.getCompanyId()).toList());
    }

    @Test
    void pageIncludesDictionarySortWithoutChangingSelectedIds() {
        BroadcastTypeMapper broadcastTypeMapper = (BroadcastTypeMapper) ReflectionTestUtils.getField(service, "broadcastTypeMapper");
        AdaptationTypeMapper adaptationTypeMapper = (AdaptationTypeMapper) ReflectionTestUtils.getField(service, "adaptationTypeMapper");
        when(broadcastTypeMapper.selectByIds(anyCollection())).thenReturn(List.of(
                BroadcastType.builder().id(1L).name("TV").sortOrder(-3L).build()));
        when(adaptationTypeMapper.selectByIds(anyCollection())).thenReturn(List.of(
                AdaptationType.builder().id(1L).name("漫画改").sortOrder(5L).build()));
        var row = page().getFirst();
        assertEquals(1L, row.getBroadcastType().getId());
        assertEquals(-3L, row.getBroadcastType().getSortOrder());
        assertEquals(1L, row.getAdaptationType().getId());
        assertEquals(5L, row.getAdaptationType().getSortOrder());
    }

    @Test
    void createAndUpdateRemainTransactional() throws Exception {
        assertNotNull(AnimeServiceImpl.class.getMethod("createAnime", AnimeDTO.class).getAnnotation(Transactional.class));
        assertNotNull(AnimeServiceImpl.class.getMethod("updateAnime", AnimeDTO.class).getAnnotation(Transactional.class));
    }

    @Test
    void personsSupportMultipleRolesStableSortAndCurrentNamesWithBatchReads() {
        AnimeDTO dto = animeDTO();
        dto.setPersons(List.of(person(9L, " 导演 ", 2L), person(3L, "音乐", 0L), person(9L, "脚本", null)));
        AnimeVO created = service.createAnime(dto);
        assertEquals(List.of("音乐", "导演", "脚本"), created.getPersons().stream().map(value -> value.getRole()).toList());
        assertEquals(List.of(0L, 2L, 2L), created.getPersons().stream().map(value -> value.getSortOrder()).toList());
        assertEquals(created.getPersons(), service.getAnimeById(1L).getPersons());
        people.get(9L).setName("新署名");
        assertEquals("新署名", service.getAnimeById(1L).getPersons().get(1).getPersonName());
        clearInvocations(personMapper, personRelationMapper);
        var rows = page();
        assertEquals(List.of(3L, 9L, 9L), rows.getFirst().getPersons().stream().map(value -> value.getPersonId()).toList());
        assertTrue(rows.getLast().getPersons().isEmpty());
        verify(personRelationMapper).selectList(any());
        verify(personMapper).selectByIds(anyCollection());
    }

    @Test
    void invalidPersonsFailBeforeCreateOrUpdateWrites() {
        List<List<PersonRelationDTO>> invalid = List.of(
                List.of(person(99L, "导演", 0L)), List.of(person(9L, " ", 0L)),
                List.of(person(9L, "导".repeat(51), 0L)), List.of(person(null, "导演", 0L)),
                List.of(person(9L, "导演", 0L), person(9L, " 导演 ", 1L)),
                List.of(person(9L, "导演", -1L)), List.of(person(9L, "导演", 2147483648L)),
                Arrays.asList((PersonRelationDTO) null));
        for (List<PersonRelationDTO> persons : invalid) {
            AnimeDTO dto = animeDTO();
            dto.setPersons(persons);
            assertThrows(BusinessException.class, () -> service.createAnime(dto));
            dto.setId(1L);
            assertThrows(BusinessException.class, () -> service.updateAnime(dto));
        }
        verify(animeMapper, never()).insert(any(Anime.class));
        verify(animeMapper, never()).update(isNull(), any());
        verify(personRelationMapper, never()).insert(any(PersonRelation.class));
        verify(personRelationMapper, never()).delete(any());
    }

    @Test
    void omittedPersonsPreserveRelationsButEmptyArrayClearsOnlyThisAnime() {
        AnimeDTO dto = animeDTO();
        dto.setPersons(List.of(person(9L, "导演", 0L)));
        service.createAnime(dto);
        personRelations.add(PersonRelation.builder().id(2L).targetType("BOOK").targetId(1L)
                .personId(9L).role("作者").sortOrder(0L).build());
        personRelations.add(PersonRelation.builder().id(3L).targetType("ANIME").targetId(2L)
                .personId(3L).role("导演").sortOrder(0L).build());
        clearInvocations(personRelationMapper);
        AnimeDTO update = animeDTO();
        update.setId(1L);
        assertEquals(1, service.updateAnime(update).getPersons().size());
        verify(personRelationMapper, never()).delete(any());
        update.setPersons(List.of());
        assertTrue(service.updateAnime(update).getPersons().isEmpty());
        assertEquals(2, personRelations.size());
        assertEquals(2, people.size());
    }

    @Test
    void deleteAnimeRemovesOnlyItsPersonRelationsAndKeepsPersonArchives() {
        personRelations.add(PersonRelation.builder().id(1L).targetType("ANIME").targetId(1L)
                .personId(9L).role("导演").sortOrder(0L).build());
        personRelations.add(PersonRelation.builder().id(2L).targetType("BOOK").targetId(1L)
                .personId(9L).role("作者").sortOrder(0L).build());
        service.deleteAnime(List.of(1L));
        assertEquals(List.of("BOOK"), personRelations.stream().map(PersonRelation::getTargetType).toList());
        verify(personMapper, never()).deleteByIds(anyCollection());
        verify(animeMapper).deleteByIds(List.of(1L));
        assertEquals(2, people.size());
    }

    private PersonRelationDTO person(Long personId, String role, Long sortOrder) {
        PersonRelationDTO dto = new PersonRelationDTO();
        dto.setPersonId(personId);
        dto.setRole(role);
        dto.setSortOrder(sortOrder);
        return dto;
    }

    @Test
    void associationFilterStillReturnsCompletePersonsForEachAnime() {
        personRelations.add(PersonRelation.builder().id(1L).targetType("ANIME").targetId(1L)
                .personId(9L).role("导演").sortOrder(0L).build());
        personRelations.add(PersonRelation.builder().id(2L).targetType("ANIME").targetId(1L)
                .personId(3L).role("音乐").sortOrder(1L).build());
        Page<Anime> result = new Page<>(1, 20);
        result.setRecords(List.of(anime));
        result.setTotal(1L);
        when(animeMapper.selectAnimePage(any(), any(), isNull(), isNull())).thenReturn(result);
        AnimePageDTO dto = new AnimePageDTO();
        dto.setPersonId(9L);
        dto.setSeriesId(8L);
        dto.setCompanyId(3L);
        var response = service.pageAnime(dto);
        assertEquals(List.of(9L, 3L), response.getRows().getFirst().getPersons().stream()
                .map(value -> value.getPersonId()).toList());
        verify(personMapper).selectByIds(anyCollection());
        verify(personRelationMapper).selectList(any());
    }

    private void assertAssociationQuery(LambdaQueryWrapper<?> query, boolean ordered) {
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("target_type ="));
        assertTrue(sql.contains("target_id =") || sql.contains("target_id IN"));
        assertTrue(query.getParamNameValuePairs().containsValue("ANIME"));
        if (ordered) {
            assertTrue(sql.endsWith("ORDER BY sort_order ASC,id ASC"), sql);
        }
    }

    private List<org.killze.acgbox.vo.content.AnimePageVO> page() {
        Page<Anime> page = new Page<>(1, 20);
        page.setRecords(List.of(anime, Anime.builder().id(2L).name("其他动画").build()));
        page.setTotal(2L);
        when(animeMapper.selectAnimePage(any(), any(), isNull(), isNull())).thenReturn(page);
        return service.pageAnime(new AnimePageDTO()).getRows();
    }

    private void addOtherTargets() {
        aliases.add(Alias.builder().id(70L).targetType("BOOK").targetId(1L).aliasName("其他类型").sortOrder(-100L).build());
        aliases.add(Alias.builder().id(71L).targetType("ANIME").targetId(2L).aliasName("其他动画").sortOrder(-100L).build());
        relations.add(relation(70L, "BOOK", 1L, 19L, "其他类型", -100L));
        relations.add(relation(71L, "ANIME", 2L, 20L, "其他动画", -100L));
    }

    private AnimeDTO animeDTO() {
        AnimeDTO dto = new AnimeDTO();
        dto.setName(anime.getName());
        dto.setBroadcastTypeId(1L);
        dto.setAdaptationTypeId(1L);
        dto.setRegionId(1L);
        dto.setAirDate(anime.getAirDate());
        dto.setStatus(3L);
        return dto;
    }

    private CompanyRelationDTO company(Long companyId, String role, Long sortOrder) {
        CompanyRelationDTO dto = new CompanyRelationDTO();
        dto.setCompanyId(companyId);
        dto.setRole(role);
        dto.setSortOrder(sortOrder);
        return dto;
    }

    private CompanyRelation relation(Long id, String targetType, Long targetId, Long companyId, String role, Long sortOrder) {
        return CompanyRelation.builder().id(id).targetType(targetType).targetId(targetId)
                .companyId(companyId).role(role).sortOrder(sortOrder).build();
    }
}
