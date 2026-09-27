package org.killze.acgbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.entity.content.Anime;
import org.killze.acgbox.entity.content.BroadcastType;
import org.killze.acgbox.entity.content.Region;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AdaptationTypeMapper;
import org.killze.acgbox.mapper.AnimeMapper;
import org.killze.acgbox.mapper.BroadcastTypeMapper;
import org.killze.acgbox.mapper.RegionMapper;
import org.killze.acgbox.service.AdaptationTypeService;
import org.killze.acgbox.service.BroadcastTypeService;
import org.killze.acgbox.service.RegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class ReferencedCatalogDeleteIntegrationTests {

    @Autowired
    private AdaptationTypeMapper adaptationTypeMapper;

    @Autowired
    private BroadcastTypeMapper broadcastTypeMapper;

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private AnimeMapper animeMapper;

    @Autowired
    private AdaptationTypeService adaptationTypeService;

    @Autowired
    private BroadcastTypeService broadcastTypeService;

    @Autowired
    private RegionService regionService;

    private AdaptationType adaptationType;
    private BroadcastType broadcastType;
    private Region region;

    @BeforeEach
    void createCatalogEntries() {
        String suffix = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        adaptationType = AdaptationType.builder()
                .name("adaptation-" + suffix)
                .createdAt(now)
                .updatedAt(now)
                .build();
        broadcastType = BroadcastType.builder()
                .name("broadcast-" + suffix)
                .createdAt(now)
                .updatedAt(now)
                .build();
        region = Region.builder()
                .name("region-" + suffix)
                .createdAt(now)
                .updatedAt(now)
                .build();
        adaptationTypeMapper.insert(adaptationType);
        broadcastTypeMapper.insert(broadcastType);
        regionMapper.insert(region);
    }

    @Test
    void rejectsReferencedEntries() {
        animeMapper.insert(Anime.builder()
                .name("anime-" + UUID.randomUUID())
                .adaptationTypeId(adaptationType.getId())
                .broadcastTypeId(broadcastType.getId())
                .regionId(region.getId())
                .airDate(LocalDate.now())
                .status(3L)
                .build());

        assertEquals("所选改编类型已被动画引用，不能删除", assertThrows(BusinessException.class,
                () -> adaptationTypeService.deleteAdaptationTypes(List.of(adaptationType.getId()))).getMessage());
        assertEquals("所选放送类型已被动画引用，不能删除", assertThrows(BusinessException.class,
                () -> broadcastTypeService.deleteBroadcastTypes(List.of(broadcastType.getId()))).getMessage());
        assertEquals("所选地区已被动画引用，不能删除", assertThrows(BusinessException.class,
                () -> regionService.deleteRegions(List.of(region.getId()))).getMessage());

        assertNotNull(adaptationTypeMapper.selectById(adaptationType.getId()));
        assertNotNull(broadcastTypeMapper.selectById(broadcastType.getId()));
        assertNotNull(regionMapper.selectById(region.getId()));
    }

    @Test
    void deletesUnreferencedEntries() {
        adaptationTypeService.deleteAdaptationTypes(List.of(adaptationType.getId()));
        broadcastTypeService.deleteBroadcastTypes(List.of(broadcastType.getId()));
        regionService.deleteRegions(List.of(region.getId()));

        assertNull(adaptationTypeMapper.selectById(adaptationType.getId()));
        assertNull(broadcastTypeMapper.selectById(broadcastType.getId()));
        assertNull(regionMapper.selectById(region.getId()));
    }

    @Test
    void rejectsEntireBatchWhenOneEntryIsReferenced() {
        animeMapper.insert(Anime.builder()
                .name("anime-" + UUID.randomUUID())
                .adaptationTypeId(adaptationType.getId())
                .broadcastTypeId(broadcastType.getId())
                .regionId(region.getId())
                .airDate(LocalDate.now())
                .status(3L)
                .build());
        AdaptationType unused = AdaptationType.builder()
                .name("adaptation-" + UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        adaptationTypeMapper.insert(unused);

        assertThrows(BusinessException.class, () -> adaptationTypeService.deleteAdaptationTypes(
                List.of(unused.getId(), adaptationType.getId())));
        assertNotNull(adaptationTypeMapper.selectById(unused.getId()));
        assertNotNull(adaptationTypeMapper.selectById(adaptationType.getId()));
    }
}
