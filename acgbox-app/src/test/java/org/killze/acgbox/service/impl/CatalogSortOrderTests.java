package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.dto.content.BroadcastTypeDTO;
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.entity.content.Anime;
import org.killze.acgbox.entity.content.BroadcastType;
import org.killze.acgbox.entity.content.Region;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AdaptationTypeMapper;
import org.killze.acgbox.mapper.AnimeMapper;
import org.killze.acgbox.mapper.BroadcastTypeMapper;
import org.killze.acgbox.mapper.RegionMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 两类字典排序及引用删除提示测试，所有 Mapper 使用模拟对象，不访问真实数据库。
 */
class CatalogSortOrderTests {

    private BroadcastTypeMapper broadcastTypeMapper;
    private AdaptationTypeMapper adaptationTypeMapper;
    private RegionMapper regionMapper;
    private AnimeMapper animeMapper;
    private BroadcastTypeServiceImpl broadcastTypeService;
    private AdaptationTypeServiceImpl adaptationTypeService;
    private RegionServiceImpl regionService;

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(BroadcastType.class, AdaptationType.class, Region.class, Anime.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
        broadcastTypeMapper = mock(BroadcastTypeMapper.class);
        adaptationTypeMapper = mock(AdaptationTypeMapper.class);
        regionMapper = mock(RegionMapper.class);
        animeMapper = mock(AnimeMapper.class);
        broadcastTypeService = new BroadcastTypeServiceImpl();
        adaptationTypeService = new AdaptationTypeServiceImpl();
        regionService = new RegionServiceImpl();
        ReflectionTestUtils.setField(broadcastTypeService, "broadcastTypeMapper", broadcastTypeMapper);
        ReflectionTestUtils.setField(broadcastTypeService, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(adaptationTypeService, "adaptationTypeMapper", adaptationTypeMapper);
        ReflectionTestUtils.setField(adaptationTypeService, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(regionService, "regionMapper", regionMapper);
        ReflectionTestUtils.setField(regionService, "animeMapper", animeMapper);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,0", "0,0", "-9,-9", "-2147483648,-2147483648", "2147483647,2147483647"}, nullValues = "null")
    void broadcastCreationReturnsAndPersistsSortOrder(Long input, long expected) {
        BroadcastTypeDTO dto = new BroadcastTypeDTO();
        dto.setName("TV");
        dto.setSortOrder(input);
        when(broadcastTypeMapper.insert(any(BroadcastType.class))).thenAnswer(invocation -> {
            BroadcastType entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        });
        var response = broadcastTypeService.createBroadcastType(dto);
        ArgumentCaptor<BroadcastType> saved = ArgumentCaptor.forClass(BroadcastType.class);
        verify(broadcastTypeMapper).insert(saved.capture());
        assertEquals(expected, saved.getValue().getSortOrder());
        assertEquals(expected, response.getSortOrder());
        assertEquals(10L, response.getId());
    }

    @ParameterizedTest
    @CsvSource(value = {"null,7", "0,0", "-9,-9", "2147483647,2147483647"}, nullValues = "null")
    void broadcastUpdateKeepsOmittedSortOrderAndSupportsExplicitZero(Long input, long expected) {
        BroadcastType entity = BroadcastType.builder().id(10L).name("TV").sortOrder(7L).build();
        when(broadcastTypeMapper.selectById(10L)).thenReturn(entity);
        BroadcastTypeDTO dto = new BroadcastTypeDTO();
        dto.setId(10L);
        dto.setName("WEB");
        dto.setSortOrder(input);
        var response = broadcastTypeService.updateBroadcastType(dto);
        verify(broadcastTypeMapper).updateById(entity);
        assertEquals(expected, entity.getSortOrder());
        assertEquals(expected, response.getSortOrder());
        assertEquals("WEB", response.getName());
        assertEquals(expected, broadcastTypeService.getBroadcastTypeById(10L).getSortOrder());
    }

    @Test
    void broadcastListRequestsStableSortAndIncludesSortOrder() {
        when(broadcastTypeMapper.selectList(any())).thenReturn(List.of(
                BroadcastType.builder().id(3L).name("Z").sortOrder(-1L).build(),
                BroadcastType.builder().id(1L).name("B").sortOrder(0L).build(),
                BroadcastType.builder().id(2L).name("A").sortOrder(0L).build()));
        var response = broadcastTypeService.listBroadcastTypes();
        ArgumentCaptor<LambdaQueryWrapper<BroadcastType>> query = ArgumentCaptor.captor();
        verify(broadcastTypeMapper).selectList(query.capture());
        assertEquals("ORDER BY sort_order ASC,id ASC", query.getValue().getSqlSegment().trim());
        assertEquals(List.of(3L, 1L, 2L), response.stream().map(value -> value.getId()).toList());
        assertEquals(List.of(-1L, 0L, 0L), response.stream().map(value -> value.getSortOrder()).toList());
    }

    @ParameterizedTest
    @CsvSource(value = {"null,0", "0,0", "-9,-9", "-2147483648,-2147483648", "2147483647,2147483647"}, nullValues = "null")
    void adaptationCreationReturnsAndPersistsSortOrder(Long input, long expected) {
        AdaptationTypeDTO dto = new AdaptationTypeDTO();
        dto.setName("原创");
        dto.setSortOrder(input);
        when(adaptationTypeMapper.insert(any(AdaptationType.class))).thenAnswer(invocation -> {
            AdaptationType entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        });
        var response = adaptationTypeService.createAdaptationType(dto);
        ArgumentCaptor<AdaptationType> saved = ArgumentCaptor.forClass(AdaptationType.class);
        verify(adaptationTypeMapper).insert(saved.capture());
        assertEquals(expected, saved.getValue().getSortOrder());
        assertEquals(expected, response.getSortOrder());
        assertEquals(10L, response.getId());
    }

    @ParameterizedTest
    @CsvSource(value = {"null,7", "0,0", "-9,-9", "2147483647,2147483647"}, nullValues = "null")
    void adaptationUpdateKeepsOmittedSortOrderAndSupportsExplicitZero(Long input, long expected) {
        AdaptationType entity = AdaptationType.builder().id(10L).name("原创").sortOrder(7L).build();
        when(adaptationTypeMapper.selectById(10L)).thenReturn(entity);
        AdaptationTypeDTO dto = new AdaptationTypeDTO();
        dto.setId(10L);
        dto.setName("漫画改");
        dto.setSortOrder(input);
        var response = adaptationTypeService.updateAdaptationType(dto);
        verify(adaptationTypeMapper).updateById(entity);
        assertEquals(expected, entity.getSortOrder());
        assertEquals(expected, response.getSortOrder());
        assertEquals("漫画改", response.getName());
        assertEquals(expected, adaptationTypeService.getAdaptationTypeById(10L).getSortOrder());
    }

    @Test
    void adaptationListRequestsStableSortAndIncludesSortOrder() {
        when(adaptationTypeMapper.selectList(any())).thenReturn(List.of(
                AdaptationType.builder().id(3L).name("Z").sortOrder(-1L).build(),
                AdaptationType.builder().id(1L).name("B").sortOrder(0L).build(),
                AdaptationType.builder().id(2L).name("A").sortOrder(0L).build()));
        var response = adaptationTypeService.listAdaptationTypes();
        ArgumentCaptor<LambdaQueryWrapper<AdaptationType>> query = ArgumentCaptor.captor();
        verify(adaptationTypeMapper).selectList(query.capture());
        assertEquals("ORDER BY sort_order ASC,id ASC", query.getValue().getSqlSegment().trim());
        assertEquals(List.of(3L, 1L, 2L), response.stream().map(value -> value.getId()).toList());
        assertEquals(List.of(-1L, 0L, 0L), response.stream().map(value -> value.getSortOrder()).toList());
    }

    @ParameterizedTest
    @CsvSource({"adaptation,false", "adaptation,true", "region,false", "region,true"})
    void referencedDeleteUsesWorkMessageForPrecheckAndForeignKey(String catalog, boolean foreignKey) {
        when(animeMapper.selectCount(any())).thenReturn(foreignKey ? 0L : 1L);
        if (catalog.equals("adaptation")) {
            if (foreignKey) {
                doThrow(new DataIntegrityViolationException("外键拒绝删除")).when(adaptationTypeMapper).deleteByIds(List.of(1L));
            }
            assertEquals("所选改编类型已被作品引用，不能删除", assertThrows(BusinessException.class,
                    () -> adaptationTypeService.deleteAdaptationTypes(List.of(1L))).getMessage());
            if (!foreignKey) {
                verify(adaptationTypeMapper, never()).deleteByIds(any());
            }
        } else {
            if (foreignKey) {
                doThrow(new DataIntegrityViolationException("外键拒绝删除")).when(regionMapper).deleteByIds(List.of(1L));
            }
            assertEquals("所选地区已被作品引用，不能删除", assertThrows(BusinessException.class,
                    () -> regionService.deleteRegions(List.of(1L))).getMessage());
            if (!foreignKey) {
                verify(regionMapper, never()).deleteByIds(any());
            }
        }
    }

    @Test
    void unreferencedCatalogsRemainDeletable() {
        adaptationTypeService.deleteAdaptationTypes(List.of(1L));
        regionService.deleteRegions(List.of(1L));
        verify(adaptationTypeMapper).deleteByIds(List.of(1L));
        verify(regionMapper).deleteByIds(List.of(1L));
    }
}
