package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.entity.content.Anime;
import org.killze.acgbox.entity.content.BroadcastType;
import org.killze.acgbox.entity.content.Region;
import org.killze.acgbox.entity.content.Book;
import org.killze.acgbox.mapper.BookMapper;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AdaptationTypeMapper;
import org.killze.acgbox.mapper.AnimeMapper;
import org.killze.acgbox.mapper.BroadcastTypeMapper;
import org.killze.acgbox.mapper.RegionMapper;
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
    private BookMapper bookMapper;
    private BroadcastTypeServiceImpl broadcastTypeService;
    private AdaptationTypeServiceImpl adaptationTypeService;
    private RegionServiceImpl regionService;

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(BroadcastType.class, AdaptationType.class, Region.class, Anime.class, Book.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
        broadcastTypeMapper = mock(BroadcastTypeMapper.class);
        adaptationTypeMapper = mock(AdaptationTypeMapper.class);
        regionMapper = mock(RegionMapper.class);
        animeMapper = mock(AnimeMapper.class);
        bookMapper = mock(BookMapper.class);
        broadcastTypeService = new BroadcastTypeServiceImpl();
        adaptationTypeService = new AdaptationTypeServiceImpl();
        regionService = new RegionServiceImpl();
        ReflectionTestUtils.setField(broadcastTypeService, "broadcastTypeMapper", broadcastTypeMapper);
        ReflectionTestUtils.setField(broadcastTypeService, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(adaptationTypeService, "adaptationTypeMapper", adaptationTypeMapper);
        ReflectionTestUtils.setField(adaptationTypeService, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(adaptationTypeService, "bookMapper", bookMapper);
        ReflectionTestUtils.setField(regionService, "regionMapper", regionMapper);
        ReflectionTestUtils.setField(regionService, "animeMapper", animeMapper);
        ReflectionTestUtils.setField(regionService, "bookMapper", bookMapper);
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
