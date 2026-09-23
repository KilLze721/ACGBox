package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.dto.content.CompanyRelationDTO;
import org.killze.acgbox.dto.content.ExternalLinkDTO;
import org.killze.acgbox.entity.content.*;
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
import org.killze.acgbox.vo.content.AdaptationTypeVO;
import org.killze.acgbox.vo.content.AnimeCompanyVO;
import org.killze.acgbox.vo.content.AnimePageVO;
import org.killze.acgbox.vo.content.AnimeVO;
import org.killze.acgbox.vo.content.BroadcastTypeVO;
import org.killze.acgbox.vo.content.CompanyRelationVO;
import org.killze.acgbox.vo.content.ExternalLinkVO;
import org.killze.acgbox.vo.content.RegionVO;
import org.killze.acgbox.vo.content.SeriesVO;
import org.killze.acgbox.vo.content.TagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 动画服务实现类。
 *
 * @author killze
 */
@Service
public class AnimeServiceImpl implements AnimeService {

    @Autowired
    private AnimeMapper animeMapper;

    @Autowired
    private BroadcastTypeMapper broadcastTypeMapper;

    @Autowired
    private AdaptationTypeMapper adaptationTypeMapper;

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private AliasMapper aliasMapper;

    @Autowired
    private CompanyRelationMapper companyRelationMapper;

    @Autowired
    private CompanyMapper companyMapper;

    @Autowired
    private ExternalLinkMapper externalLinkMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private TagRelationMapper tagRelationMapper;

    @Autowired
    private SeriesMapper seriesMapper;

    @Autowired
    private SeriesItemMapper seriesItemMapper;

    @Autowired
    private PersonalRatingMapper personalRatingMapper;

    /**
     * 一、创建动画。
     *
     * @param animeDTO 动画信息
     * @return 动画信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnimeVO createAnime(AnimeDTO animeDTO) {
        // 判断此动画是否存在。
        Anime exist = animeMapper.selectOne(
                new LambdaQueryWrapper<Anime>()
                        .eq(Anime::getName, animeDTO.getName())
        );
        if (exist != null) {
            throw new BusinessException("此动画已存在");
        }
        // 创建动画。
        Anime anime = Anime.builder()
                .name(animeDTO.getName())
                .episodeCount(animeDTO.getEpisodeCount())
                .broadcastTypeId(animeDTO.getBroadcastTypeId())
                .adaptationTypeId(animeDTO.getAdaptationTypeId())
                .regionId(animeDTO.getRegionId())
                .airDate(animeDTO.getAirDate())
                .coverImageUrl(animeDTO.getCoverImageUrl())
                .status(animeDTO.getStatus())
                .description(animeDTO.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        animeMapper.insert(anime);
        // 创建动画别名。
        List<String> aliasNames = buildAliasNames(animeDTO.getAliasNames());
        aliasNames.forEach(aliasName -> aliasMapper.insert(
                Alias.builder()
                        .targetType("ANIME")
                        .targetId(anime.getId())
                        .aliasName(aliasName)
                        .build()
        ));
        // 创建动画公司关联。
        List<CompanyRelationDTO> companies = buildCompanies(animeDTO.getCompanies());
        companies.forEach(company -> companyRelationMapper.insert(
                CompanyRelation.builder()
                        .targetType("ANIME")
                        .targetId(anime.getId())
                        .companyId(company.getCompanyId())
                        .role(company.getRole())
                        .build()
        ));
        // 创建动画外部链接。
        List<ExternalLinkDTO> externalLinks = buildExternalLinks(animeDTO.getExternalLinks());
        externalLinks.forEach(externalLink -> externalLinkMapper.insert(
                ExternalLink.builder()
                        .targetType("ANIME")
                        .targetId(anime.getId())
                        .title(externalLink.getTitle())
                        .url(externalLink.getUrl())
                        .sortOrder(externalLink.getSortOrder())
                        .build()
        ));
        // 创建动画标签关联。
        List<Long> tagIds = buildTagIds(animeDTO.getTagIds());
        tagIds.forEach(tagId -> tagRelationMapper.insert(
                TagRelation.builder()
                        .targetType("ANIME")
                        .targetId(anime.getId())
                        .tagId(tagId)
                        .build()
        ));
        // 创建动画系列关联。
        Long seriesId = buildSeriesId(animeDTO, anime);
        Long seriesSortOrder = buildSeriesSortOrder(animeDTO);
        if (seriesId != null) {
            seriesItemMapper.insert(
                    SeriesItem.builder()
                            .seriesId(seriesId)
                            .workType("anime")
                            .workId(anime.getId())
                            .sortOrder(seriesSortOrder)
                            .build()
            );
        }
        // 创建动画个人评分。
        BigDecimal personalRatingScore = saveOrUpdatePersonalRating(anime.getId(), animeDTO.getPersonalRatingScore());
        // 返回动画信息。
        return buildAnimeVO(anime, aliasNames, companies, externalLinks, tagIds, seriesId, seriesSortOrder, personalRatingScore);
    }

    /**
     * 1.构建动画信息。
     *
     * @param anime 动画信息
     * @param aliasNames 别名列表
     * @param companies 公司关联列表
     * @param externalLinks 外部链接列表
     * @param tagIds 标签 ID 列表
     * @param seriesId 系列 ID
     * @param seriesSortOrder 系列内排序值
     * @param personalRatingScore 个人评分
     * @return 动画信息
     */
    private AnimeVO buildAnimeVO(
            Anime anime,
            List<String> aliasNames,
            List<CompanyRelationDTO> companies,
            List<ExternalLinkDTO> externalLinks,
            List<Long> tagIds,
            Long seriesId,
            Long seriesSortOrder,
            BigDecimal personalRatingScore
    ) {
        return AnimeVO.builder()
                .id(anime.getId())
                .name(anime.getName())
                .episodeCount(anime.getEpisodeCount())
                .broadcastTypeId(anime.getBroadcastTypeId())
                .adaptationTypeId(anime.getAdaptationTypeId())
                .regionId(anime.getRegionId())
                .airDate(anime.getAirDate())
                .coverImageUrl(anime.getCoverImageUrl())
                .status(anime.getStatus())
                .description(anime.getDescription())
                .aliasNames(aliasNames)
                .companies(buildCompanyRelationVOList(companies))
                .externalLinks(buildExternalLinkVOList(externalLinks))
                .tagIds(tagIds)
                .seriesId(seriesId)
                .seriesSortOrder(seriesId == null ? null : seriesSortOrder)
                .personalRatingScore(personalRatingScore)
                .build();
    }

    /**
     * 1.1 构建动画所属系列 ID。
     *
     * @param animeDTO 动画信息
     * @param anime 动画实体
     * @return 系列 ID
     */
    private Long buildSeriesId(AnimeDTO animeDTO, Anime anime) {
        // 优先使用已有系列。
        if (animeDTO.getSeriesId() != null) {
            if (seriesMapper.selectById(animeDTO.getSeriesId()) == null) {
                throw new BusinessException("系列不存在");
            }
            return animeDTO.getSeriesId();
        }
        // 未选择系列时，按需使用动画名称自动创建或复用系列。
        if (!Boolean.TRUE.equals(animeDTO.getAutoCreateSeries())) {
            return null;
        }
        String seriesName = anime.getName().trim();
        Series exist = seriesMapper.selectOne(
                new LambdaQueryWrapper<Series>()
                        .eq(Series::getName, seriesName)
        );
        if (exist != null) {
            return exist.getId();
        }
        Series series = Series.builder()
                .name(seriesName)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        seriesMapper.insert(series);
        return series.getId();
    }

    /**
     * 1.2 构建动画在系列内的排序值。
     *
     * @param animeDTO 动画信息
     * @return 系列内排序值
     */
    private Long buildSeriesSortOrder(AnimeDTO animeDTO) {
        // 加入已有系列时允许手动传入排序值，自动创建系列时默认排序为 0。
        if (animeDTO.getSeriesId() != null && animeDTO.getSeriesSortOrder() != null) {
            return animeDTO.getSeriesSortOrder();
        }
        return 0L;
    }

    /**
     * 1.3 构建动画公司关联信息。
     *
     * @param companies 公司关联列表
     * @return 公司关联信息列表
     */
    private List<CompanyRelationVO> buildCompanyRelationVOList(List<CompanyRelationDTO> companies) {
        return companies.stream()
                .map(company -> CompanyRelationVO.builder()
                        .companyId(company.getCompanyId())
                        .role(company.getRole())
                        .build())
                .toList();
    }

    /**
     * 1.4 构建外部链接信息。
     *
     * @param externalLinks 外部链接列表
     * @return 外部链接信息列表
     */
    private List<ExternalLinkVO> buildExternalLinkVOList(List<ExternalLinkDTO> externalLinks) {
        return externalLinks.stream()
                .map(externalLink -> ExternalLinkVO.builder()
                        .title(externalLink.getTitle())
                        .url(externalLink.getUrl())
                        .sortOrder(externalLink.getSortOrder())
                        .build())
                .toList();
    }




    /**
     * 二、根据 ID 查询动画详情。
     *
     * @param id 动画 ID
     * @return 动画详情
     */
    @Override
    public AnimeVO getAnimeById(Long id) {
        Anime anime = animeMapper.selectById(id);
        if (anime == null) {
            throw new BusinessException("此动画不存在");
        }
        return buildAnimeDetailVO(anime);
    }

    /**
     * 2.构建动画详情信息。
     *
     * @param anime 动画信息
     * @return 动画详情
     */
    private AnimeVO buildAnimeDetailVO(Anime anime) {
        // 获取别名列表
        List<String> aliasNames = buildDetailAliasNames(anime.getId());
        // 获取公司关联列表
        List<CompanyRelationVO> companies = buildDetailCompanyRelationVOList(anime.getId());
        // 获取外部链接列表
        List<ExternalLinkVO> externalLinks = buildDetailExternalLinkVOList(anime.getId());
        // 获取标签 ID 列表
        List<TagRelation> tagRelations = buildDetailTagRelations(anime.getId());
        List<Long> tagIds = tagRelations.stream().map(TagRelation::getTagId).toList();
        // 获取系列信息
        SeriesItem seriesItem = buildDetailSeriesItem(anime.getId());
        Long seriesId = seriesItem == null ? null : seriesItem.getSeriesId();
        Long seriesSortOrder = seriesItem == null ? null : seriesItem.getSortOrder();
        // 获取个人评分
        BigDecimal personalRatingScore = buildDetailPersonalRatingScore(anime.getId());
        // 构建动画详情信息
        return AnimeVO.builder()
                .id(anime.getId())
                .name(anime.getName())
                .episodeCount(anime.getEpisodeCount())
                .broadcastTypeId(anime.getBroadcastTypeId())
                .adaptationTypeId(anime.getAdaptationTypeId())
                .regionId(anime.getRegionId())
                .airDate(anime.getAirDate())
                .coverImageUrl(anime.getCoverImageUrl())
                .status(anime.getStatus())
                .description(anime.getDescription())
                .aliasNames(aliasNames)
                .companies(companies)
                .externalLinks(externalLinks)
                .tagIds(tagIds)
                .seriesId(seriesId)
                .seriesSortOrder(seriesSortOrder)
                .personalRatingScore(personalRatingScore)
                .build();
    }

    /**
     * 2.1 查询动画别名列表。
     *
     * @param animeId 动画 ID
     * @return 别名列表
     */
    private List<String> buildDetailAliasNames(Long animeId) {
        return aliasMapper.selectList(
                        new LambdaQueryWrapper<Alias>()
                                .eq(Alias::getTargetType, "ANIME")
                                .eq(Alias::getTargetId, animeId)
                                .orderByAsc(Alias::getId)
                )
                .stream()
                .map(Alias::getAliasName)
                .toList();
    }

    /**
     * 2.2 查询动画公司关联列表。
     *
     * @param animeId 动画 ID
     * @return 公司关联列表
     */
    private List<CompanyRelationVO> buildDetailCompanyRelationVOList(Long animeId) {
        return companyRelationMapper.selectList(
                        new LambdaQueryWrapper<CompanyRelation>()
                                .eq(CompanyRelation::getTargetType, "ANIME")
                                .eq(CompanyRelation::getTargetId, animeId)
                                .orderByAsc(CompanyRelation::getId)
                )
                .stream()
                .map(companyRelation -> {
                    return CompanyRelationVO.builder()
                            .companyId(companyRelation.getCompanyId())
                            .role(companyRelation.getRole())
                            .build();
                })
                .toList();
    }

    /**
     * 2.3 查询动画外部链接列表。
     *
     * @param animeId 动画 ID
     * @return 外部链接列表
     */
    private List<ExternalLinkVO> buildDetailExternalLinkVOList(Long animeId) {
        return externalLinkMapper.selectList(
                        new LambdaQueryWrapper<ExternalLink>()
                                .eq(ExternalLink::getTargetType, "ANIME")
                                .eq(ExternalLink::getTargetId, animeId)
                                .orderByAsc(ExternalLink::getSortOrder)
                                .orderByAsc(ExternalLink::getId)
                )
                .stream()
                .map(externalLink -> ExternalLinkVO.builder()
                        .title(externalLink.getTitle())
                        .url(externalLink.getUrl())
                        .sortOrder(externalLink.getSortOrder())
                        .build())
                .toList();
    }

    /**
     * 2.4 查询动画标签关联列表。
     *
     * @param animeId 动画 ID
     * @return 标签关联列表
     */
    private List<TagRelation> buildDetailTagRelations(Long animeId) {
        return tagRelationMapper.selectList(
                new LambdaQueryWrapper<TagRelation>()
                        .eq(TagRelation::getTargetType, "ANIME")
                        .eq(TagRelation::getTargetId, animeId)
                        .orderByAsc(TagRelation::getId)
        );
    }

    /**
     * 2.5 查询动画系列条目。
     *
     * @param animeId 动画 ID
     * @return 系列条目
     */
    private SeriesItem buildDetailSeriesItem(Long animeId) {
        List<SeriesItem> seriesItems = seriesItemMapper.selectList(
                new LambdaQueryWrapper<SeriesItem>()
                        .eq(SeriesItem::getWorkType, "anime")
                        .eq(SeriesItem::getWorkId, animeId)
                        .orderByAsc(SeriesItem::getSortOrder)
                        .orderByAsc(SeriesItem::getId)
        );
        return seriesItems.isEmpty() ? null : seriesItems.getFirst();
    }

    /**
     * 2.6 查询动画个人评分。
     *
     * @param animeId 动画 ID
     * @return 个人评分
     */
    private BigDecimal buildDetailPersonalRatingScore(Long animeId) {
        PersonalRating personalRating = personalRatingMapper.selectOne(
                new LambdaQueryWrapper<PersonalRating>()
                        .eq(PersonalRating::getTargetType, "ANIME")
                        .eq(PersonalRating::getTargetId, animeId)
        );
        return personalRating == null ? null : personalRating.getScore();
    }




    /**
     * 三、修改动画。
     *
     * @param animeDTO 动画信息
     * @return 动画信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnimeVO updateAnime(AnimeDTO animeDTO) {
        // 判断动画 ID 是否为空。
        if (animeDTO.getId() == null) {
            throw new BusinessException("动画 ID 不能为空");
        }
        // 判断动画是否存在。
        Anime anime = animeMapper.selectById(animeDTO.getId());
        if (anime == null) {
            throw new BusinessException("此动画不存在");
        }
        // 判断新名称是否被其他动画使用。
        Anime exist = animeMapper.selectOne(
                new LambdaQueryWrapper<Anime>()
                        .eq(Anime::getName, animeDTO.getName())
                        .ne(Anime::getId, animeDTO.getId())
        );
        if (exist != null) {
            throw new BusinessException("此动画已存在");
        }
        // 在修改数据库前完成关联数据清洗和校验。
        List<String> aliasNames = buildAliasNames(animeDTO.getAliasNames());
        List<CompanyRelationDTO> companies = buildCompanies(animeDTO.getCompanies());
        List<ExternalLinkDTO> externalLinks = buildExternalLinks(animeDTO.getExternalLinks());
        List<Long> tagIds = buildTagIds(animeDTO.getTagIds());
        Long seriesId = buildUpdateSeriesId(animeDTO.getSeriesId());
        Long seriesSortOrder = seriesId == null
                ? null
                : animeDTO.getSeriesSortOrder() == null ? 0L : animeDTO.getSeriesSortOrder();
        // 显式设置全部基础字段，确保可空字段也能被清空。
        animeMapper.update(
                null,
                new LambdaUpdateWrapper<Anime>()
                        .eq(Anime::getId, anime.getId())
                        .set(Anime::getName, animeDTO.getName())
                        .set(Anime::getEpisodeCount, animeDTO.getEpisodeCount())
                        .set(Anime::getBroadcastTypeId, animeDTO.getBroadcastTypeId())
                        .set(Anime::getAdaptationTypeId, animeDTO.getAdaptationTypeId())
                        .set(Anime::getRegionId, animeDTO.getRegionId())
                        .set(Anime::getAirDate, animeDTO.getAirDate())
                        .set(Anime::getCoverImageUrl, animeDTO.getCoverImageUrl())
                        .set(Anime::getStatus, animeDTO.getStatus())
                        .set(Anime::getDescription, animeDTO.getDescription())
                        .set(Anime::getUpdatedAt, LocalDateTime.now())
        );
        // 使用前端提交的最终数据覆盖动画关联信息。
        replaceAnimeAliases(anime.getId(), aliasNames);
        replaceAnimeCompanies(anime.getId(), companies);
        replaceAnimeExternalLinks(anime.getId(), externalLinks);
        replaceAnimeTags(anime.getId(), tagIds);
        replaceAnimeSeries(anime.getId(), seriesId, seriesSortOrder);
        saveOrUpdatePersonalRating(anime.getId(), animeDTO.getPersonalRatingScore());
        // 返回修改后的动画完整信息。
        return getAnimeById(anime.getId());
    }

    /**
     * 3.1 校验修改时传入的系列 ID。
     *
     * @param seriesId 系列 ID
     * @return 系列 ID
     */
    private Long buildUpdateSeriesId(Long seriesId) {
        if (seriesId != null && seriesMapper.selectById(seriesId) == null) {
            throw new BusinessException("系列不存在");
        }
        return seriesId;
    }

    /**
     * 3.2 覆盖动画别名。
     *
     * @param animeId 动画 ID
     * @param aliasNames 别名列表
     */
    private void replaceAnimeAliases(Long animeId, List<String> aliasNames) {
        aliasMapper.delete(
                new LambdaQueryWrapper<Alias>()
                        .eq(Alias::getTargetType, "ANIME")
                        .eq(Alias::getTargetId, animeId)
        );
        aliasNames.forEach(aliasName -> aliasMapper.insert(
                Alias.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .aliasName(aliasName)
                        .build()
        ));
    }

    /**
     * 3.3 覆盖动画公司关联。
     *
     * @param animeId 动画 ID
     * @param companies 公司关联列表
     */
    private void replaceAnimeCompanies(Long animeId, List<CompanyRelationDTO> companies) {
        companyRelationMapper.delete(
                new LambdaQueryWrapper<CompanyRelation>()
                        .eq(CompanyRelation::getTargetType, "ANIME")
                        .eq(CompanyRelation::getTargetId, animeId)
        );
        companies.forEach(company -> companyRelationMapper.insert(
                CompanyRelation.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .companyId(company.getCompanyId())
                        .role(company.getRole())
                        .build()
        ));
    }

    /**
     * 3.4 覆盖动画外部链接。
     *
     * @param animeId 动画 ID
     * @param externalLinks 外部链接列表
     */
    private void replaceAnimeExternalLinks(Long animeId, List<ExternalLinkDTO> externalLinks) {
        externalLinkMapper.delete(
                new LambdaQueryWrapper<ExternalLink>()
                        .eq(ExternalLink::getTargetType, "ANIME")
                        .eq(ExternalLink::getTargetId, animeId)
        );
        externalLinks.forEach(externalLink -> externalLinkMapper.insert(
                ExternalLink.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .title(externalLink.getTitle())
                        .url(externalLink.getUrl())
                        .sortOrder(externalLink.getSortOrder())
                        .build()
        ));
    }

    /**
     * 3.5 覆盖动画标签关联。
     *
     * @param animeId 动画 ID
     * @param tagIds 标签 ID 列表
     */
    private void replaceAnimeTags(Long animeId, List<Long> tagIds) {
        tagRelationMapper.delete(
                new LambdaQueryWrapper<TagRelation>()
                        .eq(TagRelation::getTargetType, "ANIME")
                        .eq(TagRelation::getTargetId, animeId)
        );
        tagIds.forEach(tagId -> tagRelationMapper.insert(
                TagRelation.builder()
                        .targetType("ANIME")
                        .targetId(animeId)
                        .tagId(tagId)
                        .build()
        ));
    }

    /**
     * 3.6 覆盖动画系列关联。
     *
     * @param animeId 动画 ID
     * @param seriesId 系列 ID
     * @param seriesSortOrder 系列内排序值
     */
    private void replaceAnimeSeries(Long animeId, Long seriesId, Long seriesSortOrder) {
        seriesItemMapper.delete(
                new LambdaQueryWrapper<SeriesItem>()
                        .eq(SeriesItem::getWorkType, "anime")
                        .eq(SeriesItem::getWorkId, animeId)
        );
        if (seriesId == null) {
            return;
        }
        seriesItemMapper.insert(
                SeriesItem.builder()
                        .seriesId(seriesId)
                        .workType("anime")
                        .workId(animeId)
                        .sortOrder(seriesSortOrder)
                        .build()
        );
    }

    /**
     * 保存、修改或删除动画个人评分。
     *
     * @param animeId 动画 ID
     * @param score 个人评分
     * @return 个人评分
     */
    private BigDecimal saveOrUpdatePersonalRating(Long animeId, BigDecimal score) {
        PersonalRating personalRating = personalRatingMapper.selectOne(
                new LambdaQueryWrapper<PersonalRating>()
                        .eq(PersonalRating::getTargetType, "ANIME")
                        .eq(PersonalRating::getTargetId, animeId)
        );
        // 评分为空时删除已有评分。
        if (score == null) {
            if (personalRating != null) {
                personalRatingMapper.deleteById(personalRating.getId());
            }
            return null;
        }
        // 没有评分记录时创建评分。
        if (personalRating == null) {
            personalRatingMapper.insert(
                    PersonalRating.builder()
                            .targetType("ANIME")
                            .targetId(animeId)
                            .score(score)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build()
            );
            return score;
        }
        // 已有评分记录时修改评分。
        personalRating.setScore(score);
        personalRating.setUpdatedAt(LocalDateTime.now());
        personalRatingMapper.updateById(personalRating);
        return score;
    }

    /**
     * 四、批量删除动画。
     *
     * @param ids 动画 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnime(List<Long> ids) {
        // 判断动画 ID 列表是否为空。
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的动画");
        }
        // 删除动画别名。
        aliasMapper.delete(
                new LambdaQueryWrapper<Alias>()
                        .eq(Alias::getTargetType, "ANIME")
                        .in(Alias::getTargetId, ids)
        );
        // 删除动画公司关联。
        companyRelationMapper.delete(
                new LambdaQueryWrapper<CompanyRelation>()
                        .eq(CompanyRelation::getTargetType, "ANIME")
                        .in(CompanyRelation::getTargetId, ids)
        );
        // 删除动画外部链接。
        externalLinkMapper.delete(
                new LambdaQueryWrapper<ExternalLink>()
                        .eq(ExternalLink::getTargetType, "ANIME")
                        .in(ExternalLink::getTargetId, ids)
        );
        // 删除动画标签关联。
        tagRelationMapper.delete(
                new LambdaQueryWrapper<TagRelation>()
                        .eq(TagRelation::getTargetType, "ANIME")
                        .in(TagRelation::getTargetId, ids)
        );
        // 删除动画系列关联。
        seriesItemMapper.delete(
                new LambdaQueryWrapper<SeriesItem>()
                        .eq(SeriesItem::getWorkType, "anime")
                        .in(SeriesItem::getWorkId, ids)
        );
        // 删除动画个人评分。
        personalRatingMapper.delete(
                new LambdaQueryWrapper<PersonalRating>()
                        .eq(PersonalRating::getTargetType, "ANIME")
                        .in(PersonalRating::getTargetId, ids)
        );
        // 删除动画。
        animeMapper.deleteByIds(ids);
    }

    /**
     * 五、分页查询动画。
     *
     * @param pageDTO 分页查询参数
     * @return 动画分页结果
     */
    @Override
    public PageVO<AnimePageVO> pageAnime(AnimePageDTO pageDTO) {
        BroadcastDateRange dateRange = normalizeAnimePageDTO(pageDTO);
        Page<Anime> page = new Page<>(pageDTO.getPageNum(), pageDTO.getPageSize());
        IPage<Anime> result = animeMapper.selectAnimePage(
                page,
                pageDTO,
                dateRange.startDate(),
                dateRange.endDate()
        );
        return PageVO.<AnimePageVO>builder()
                .pageNum(result.getCurrent())
                .pageSize(result.getSize())
                .total(result.getTotal())
                .pages(result.getPages())
                .rows(buildAnimePageVOList(result.getRecords()))
                .build();
    }

    /**
     * 5.1 规范化并校验动画分页查询参数。
     *
     * @param pageDTO 分页查询参数
     * @return 转换后的放送日期范围
     */
    private BroadcastDateRange normalizeAnimePageDTO(AnimePageDTO pageDTO) {
        if (pageDTO == null) {
            throw new BusinessException("分页查询参数不能为空");
        }
        if (pageDTO.getPageNum() == null) {
            pageDTO.setPageNum(1L);
        }
        if (pageDTO.getPageSize() == null) {
            pageDTO.setPageSize(20L);
        }
        if (pageDTO.getPageNum() < 1L) {
            throw new BusinessException("page 必须大于等于 1");
        }
        if (pageDTO.getPageSize() < 1L || pageDTO.getPageSize() > 100L) {
            throw new BusinessException("pageSize 必须处于 1 到 100 之间");
        }
        pageDTO.setKeyword(normalizeText(pageDTO.getKeyword()));
        pageDTO.setBroadcastStartDate(normalizeText(pageDTO.getBroadcastStartDate()));
        pageDTO.setBroadcastEndDate(normalizeText(pageDTO.getBroadcastEndDate()));
        pageDTO.setTagIds(buildPageTagIds(pageDTO.getTagIds()));
        pageDTO.setTagMatchMode(normalizeTagMatchMode(pageDTO.getTagMatchMode()));
        pageDTO.setSortBy(normalizeSortBy(pageDTO.getSortBy()));
        pageDTO.setSortDirection(normalizeSortDirection(pageDTO.getSortDirection()));
        validatePageReferenceIds(pageDTO);
        validatePageRating(pageDTO);
        LocalDate startDate = parseBroadcastDate(
                pageDTO.getBroadcastStartDate(),
                false,
                "broadcastStartDate"
        );
        LocalDate endDate = parseBroadcastDate(
                pageDTO.getBroadcastEndDate(),
                true,
                "broadcastEndDate"
        );
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException("broadcastStartDate 不能晚于 broadcastEndDate");
        }
        return new BroadcastDateRange(startDate, endDate);
    }

    /**
     * 5.2 去除字符串首尾空格。
     *
     * @param value 字符串
     * @return 规范化后的字符串
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 5.3 规范化标签 ID 列表。
     *
     * @param tagIds 标签 ID 列表
     * @return 去空去重后的标签 ID 列表
     */
    private List<Long> buildPageTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagIds.stream()
                .filter(tagId -> tagId != null)
                .distinct()
                .toList();
    }

    /**
     * 5.4 规范化标签匹配方式。
     *
     * @param tagMatchMode 标签匹配方式
     * @return 标签匹配方式
     */
    private String normalizeTagMatchMode(String tagMatchMode) {
        String value = normalizeText(tagMatchMode);
        if (value == null) {
            return "ALL";
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!"ALL".equals(value) && !"ANY".equals(value)) {
            throw new BusinessException("tagMatchMode 仅支持 ALL 或 ANY");
        }
        return value;
    }

    /**
     * 5.5 规范化排序字段。
     *
     * @param sortBy 排序字段
     * @return 排序字段
     */
    private String normalizeSortBy(String sortBy) {
        String value = normalizeText(sortBy);
        if (value == null) {
            return "BROADCAST_DATE";
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!"BROADCAST_DATE".equals(value) && !"PERSONAL_RATING".equals(value)) {
            throw new BusinessException("sortBy 仅支持 BROADCAST_DATE 或 PERSONAL_RATING");
        }
        return value;
    }

    /**
     * 5.6 规范化排序方向。
     *
     * @param sortDirection 排序方向
     * @return 排序方向
     */
    private String normalizeSortDirection(String sortDirection) {
        String value = normalizeText(sortDirection);
        if (value == null) {
            return "DESC";
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!"ASC".equals(value) && !"DESC".equals(value)) {
            throw new BusinessException("sortDirection 仅支持 ASC 或 DESC");
        }
        return value;
    }

    /**
     * 5.7 校验分页查询中的关联 ID。
     *
     * @param pageDTO 分页查询参数
     */
    private void validatePageReferenceIds(AnimePageDTO pageDTO) {
        if (pageDTO.getBroadcastTypeId() != null
                && broadcastTypeMapper.selectById(pageDTO.getBroadcastTypeId()) == null) {
            throw new BusinessException("broadcastTypeId 对应的放送类型不存在");
        }
        if (pageDTO.getAdaptationTypeId() != null
                && adaptationTypeMapper.selectById(pageDTO.getAdaptationTypeId()) == null) {
            throw new BusinessException("adaptationTypeId 对应的改编类型不存在");
        }
        if (pageDTO.getRegionId() != null && regionMapper.selectById(pageDTO.getRegionId()) == null) {
            throw new BusinessException("regionId 对应的地区不存在");
        }
        if (pageDTO.getCompanyId() != null && companyMapper.selectById(pageDTO.getCompanyId()) == null) {
            throw new BusinessException("companyId 对应的公司不存在");
        }
        if (pageDTO.getStatus() != null && (pageDTO.getStatus() < 1L || pageDTO.getStatus() > 4L)) {
            throw new BusinessException("status 仅支持 1、2、3、4");
        }
        if (!pageDTO.getTagIds().isEmpty()) {
            Set<Long> existTagIds = tagMapper.selectByIds(pageDTO.getTagIds())
                    .stream()
                    .map(Tag::getId)
                    .collect(Collectors.toSet());
            if (!existTagIds.containsAll(pageDTO.getTagIds())) {
                throw new BusinessException("tagIds 中存在无效的标签 ID");
            }
        }
    }

    /**
     * 5.8 校验评分范围。
     *
     * @param pageDTO 分页查询参数
     */
    private void validatePageRating(AnimePageDTO pageDTO) {
        validateRatingValue(pageDTO.getRatingMin(), "ratingMin");
        validateRatingValue(pageDTO.getRatingMax(), "ratingMax");
        if (pageDTO.getRatingMin() != null
                && pageDTO.getRatingMax() != null
                && pageDTO.getRatingMin().compareTo(pageDTO.getRatingMax()) > 0) {
            throw new BusinessException("ratingMin 不能大于 ratingMax");
        }
    }

    /**
     * 5.9 校验单个评分值。
     *
     * @param rating 评分
     * @param fieldName 字段名称
     */
    private void validateRatingValue(BigDecimal rating, String fieldName) {
        if (rating == null) {
            return;
        }
        if (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(BigDecimal.TEN) > 0) {
            throw new BusinessException(fieldName + " 必须处于 0 到 10 之间");
        }
        if (rating.stripTrailingZeros().scale() > 1) {
            throw new BusinessException(fieldName + " 最多支持一位小数");
        }
    }

    /**
     * 5.10 转换放送日期查询参数。
     *
     * @param value 日期参数
     * @param end 是否为结束日期
     * @param fieldName 字段名称
     * @return 转换后的日期
     */
    private LocalDate parseBroadcastDate(String value, boolean end, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            if (value.matches("\\d{4}")) {
                int year = Integer.parseInt(value);
                if (year < 1) {
                    throw new DateTimeException("年份必须大于 0");
                }
                return end ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
            }
            if (value.matches("\\d{4}-\\d{2}")) {
                int year = Integer.parseInt(value.substring(0, 4));
                int month = Integer.parseInt(value.substring(5, 7));
                if (year < 1) {
                    throw new DateTimeException("年份必须大于 0");
                }
                LocalDate firstDay = LocalDate.of(year, month, 1);
                return end ? firstDay.withDayOfMonth(firstDay.lengthOfMonth()) : firstDay;
            }
        } catch (DateTimeException | NumberFormatException e) {
            throw new BusinessException(fieldName + " 格式错误，正确格式为 yyyy 或 yyyy-MM");
        }
        throw new BusinessException(fieldName + " 格式错误，正确格式为 yyyy 或 yyyy-MM");
    }

    /**
     * 5.11 批量构建动画分页数据。
     *
     * @param animeList 当前页动画列表
     * @return 动画分页数据列表
     */
    private List<AnimePageVO> buildAnimePageVOList(List<Anime> animeList) {
        if (animeList == null || animeList.isEmpty()) {
            return List.of();
        }
        List<Long> animeIds = animeList.stream().map(Anime::getId).toList();
        Map<Long, List<String>> aliasNamesMap = buildPageAliasNamesMap(animeIds);
        Map<Long, List<TagVO>> tagsMap = buildPageTagsMap(animeIds);
        Map<Long, List<AnimeCompanyVO>> companiesMap = buildPageCompaniesMap(animeIds);
        Map<Long, List<ExternalLinkVO>> externalLinksMap = buildPageExternalLinksMap(animeIds);
        Map<Long, BigDecimal> ratingMap = buildPageRatingMap(animeIds);
        Map<Long, SeriesVO> seriesMap = buildPageSeriesMap(animeIds);

        Set<Long> broadcastTypeIds = animeList.stream()
                .map(Anime::getBroadcastTypeId)
                .collect(Collectors.toSet());
        Set<Long> adaptationTypeIds = animeList.stream()
                .map(Anime::getAdaptationTypeId)
                .collect(Collectors.toSet());
        Set<Long> regionIds = animeList.stream()
                .map(Anime::getRegionId)
                .collect(Collectors.toSet());
        Map<Long, BroadcastType> broadcastTypeMap = buildEntityMap(
                broadcastTypeMapper.selectByIds(broadcastTypeIds),
                BroadcastType::getId
        );
        Map<Long, AdaptationType> adaptationTypeMap = buildEntityMap(
                adaptationTypeMapper.selectByIds(adaptationTypeIds),
                AdaptationType::getId
        );
        Map<Long, Region> regionMap = buildEntityMap(
                regionMapper.selectByIds(regionIds),
                Region::getId
        );

        return animeList.stream()
                .map(anime -> {
                    BroadcastType broadcastType = broadcastTypeMap.get(anime.getBroadcastTypeId());
                    AdaptationType adaptationType = adaptationTypeMap.get(anime.getAdaptationTypeId());
                    Region region = regionMap.get(anime.getRegionId());
                    return AnimePageVO.builder()
                            .id(anime.getId())
                            .name(anime.getName())
                            .aliasNames(aliasNamesMap.getOrDefault(anime.getId(), List.of()))
                            .tags(tagsMap.getOrDefault(anime.getId(), List.of()))
                            .episodeCount(anime.getEpisodeCount())
                            .broadcastType(buildBroadcastTypeVO(broadcastType))
                            .adaptationType(buildAdaptationTypeVO(adaptationType))
                            .airDate(anime.getAirDate())
                            .coverImageUrl(anime.getCoverImageUrl())
                            .status(anime.getStatus())
                            .region(buildRegionVO(region))
                            .companies(companiesMap.getOrDefault(anime.getId(), List.of()))
                            .externalLinks(externalLinksMap.getOrDefault(anime.getId(), List.of()))
                            .personalRating(ratingMap.get(anime.getId()))
                            .series(seriesMap.get(anime.getId()))
                            .build();
                })
                .toList();
    }

    /**
     * 5.12 批量查询动画别名。
     *
     * @param animeIds 动画 ID 列表
     * @return 动画别名映射
     */
    private Map<Long, List<String>> buildPageAliasNamesMap(List<Long> animeIds) {
        Map<Long, List<String>> result = new HashMap<>();
        aliasMapper.selectList(
                new LambdaQueryWrapper<Alias>()
                        .eq(Alias::getTargetType, "ANIME")
                        .in(Alias::getTargetId, animeIds)
                        .orderByAsc(Alias::getId)
        ).forEach(alias -> result.computeIfAbsent(alias.getTargetId(), key -> new ArrayList<>())
                .add(alias.getAliasName()));
        return result;
    }

    /**
     * 5.13 批量查询动画标签。
     *
     * @param animeIds 动画 ID 列表
     * @return 动画标签映射
     */
    private Map<Long, List<TagVO>> buildPageTagsMap(List<Long> animeIds) {
        List<TagRelation> relations = tagRelationMapper.selectList(
                new LambdaQueryWrapper<TagRelation>()
                        .eq(TagRelation::getTargetType, "ANIME")
                        .in(TagRelation::getTargetId, animeIds)
                        .orderByAsc(TagRelation::getId)
        );
        Set<Long> tagIds = relations.stream()
                .map(TagRelation::getTagId)
                .collect(Collectors.toSet());
        List<Tag> tags = tagIds.isEmpty() ? List.of() : tagMapper.selectByIds(tagIds);
        Map<Long, Tag> tagMap = buildEntityMap(tags, Tag::getId);
        Map<Long, List<TagVO>> result = new HashMap<>();
        relations.forEach(relation -> {
            Tag tag = tagMap.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getTargetId(), key -> new ArrayList<>())
                        .add(TagVO.builder().id(tag.getId()).name(tag.getName()).build());
            }
        });
        return result;
    }

    /**
     * 5.14 批量查询动画关联公司。
     *
     * @param animeIds 动画 ID 列表
     * @return 动画关联公司映射
     */
    private Map<Long, List<AnimeCompanyVO>> buildPageCompaniesMap(List<Long> animeIds) {
        List<CompanyRelation> relations = companyRelationMapper.selectList(
                new LambdaQueryWrapper<CompanyRelation>()
                        .eq(CompanyRelation::getTargetType, "ANIME")
                        .in(CompanyRelation::getTargetId, animeIds)
                        .orderByAsc(CompanyRelation::getId)
        );
        Set<Long> companyIds = relations.stream()
                .map(CompanyRelation::getCompanyId)
                .collect(Collectors.toSet());
        List<Company> companies = companyIds.isEmpty() ? List.of() : companyMapper.selectByIds(companyIds);
        Map<Long, Company> companyMap = buildEntityMap(companies, Company::getId);
        Map<Long, List<AnimeCompanyVO>> result = new HashMap<>();
        relations.forEach(relation -> {
            Company company = companyMap.get(relation.getCompanyId());
            if (company != null) {
                result.computeIfAbsent(relation.getTargetId(), key -> new ArrayList<>())
                        .add(AnimeCompanyVO.builder()
                                .companyId(company.getId())
                                .companyName(company.getName())
                                .role(relation.getRole())
                                .build());
            }
        });
        return result;
    }

    /**
     * 5.15 批量查询动画外部链接。
     *
     * @param animeIds 动画 ID 列表
     * @return 动画外部链接映射
     */
    private Map<Long, List<ExternalLinkVO>> buildPageExternalLinksMap(List<Long> animeIds) {
        Map<Long, List<ExternalLinkVO>> result = new HashMap<>();
        externalLinkMapper.selectList(
                new LambdaQueryWrapper<ExternalLink>()
                        .eq(ExternalLink::getTargetType, "ANIME")
                        .in(ExternalLink::getTargetId, animeIds)
                        .orderByAsc(ExternalLink::getSortOrder)
                        .orderByAsc(ExternalLink::getId)
        ).forEach(externalLink -> result.computeIfAbsent(externalLink.getTargetId(), key -> new ArrayList<>())
                .add(ExternalLinkVO.builder()
                        .title(externalLink.getTitle())
                        .url(externalLink.getUrl())
                        .sortOrder(externalLink.getSortOrder())
                        .build()));
        return result;
    }

    /**
     * 5.16 批量查询动画个人评分。
     *
     * @param animeIds 动画 ID 列表
     * @return 动画个人评分映射
     */
    private Map<Long, BigDecimal> buildPageRatingMap(List<Long> animeIds) {
        return personalRatingMapper.selectList(
                        new LambdaQueryWrapper<PersonalRating>()
                                .eq(PersonalRating::getTargetType, "ANIME")
                                .in(PersonalRating::getTargetId, animeIds)
                )
                .stream()
                .collect(Collectors.toMap(
                        PersonalRating::getTargetId,
                        PersonalRating::getScore,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    /**
     * 5.17 批量查询动画所属系列。
     *
     * @param animeIds 动画 ID 列表
     * @return 动画所属系列映射
     */
    private Map<Long, SeriesVO> buildPageSeriesMap(List<Long> animeIds) {
        List<SeriesItem> seriesItems = seriesItemMapper.selectList(
                new LambdaQueryWrapper<SeriesItem>()
                        .eq(SeriesItem::getWorkType, "anime")
                        .in(SeriesItem::getWorkId, animeIds)
                        .orderByAsc(SeriesItem::getSortOrder)
                        .orderByAsc(SeriesItem::getId)
        );
        Set<Long> seriesIds = seriesItems.stream()
                .map(SeriesItem::getSeriesId)
                .collect(Collectors.toSet());
        List<Series> seriesList = seriesIds.isEmpty() ? List.of() : seriesMapper.selectByIds(seriesIds);
        Map<Long, Series> seriesEntityMap = buildEntityMap(seriesList, Series::getId);
        Map<Long, SeriesVO> result = new HashMap<>();
        seriesItems.forEach(seriesItem -> {
            Series series = seriesEntityMap.get(seriesItem.getSeriesId());
            if (series != null) {
                result.putIfAbsent(
                        seriesItem.getWorkId(),
                        SeriesVO.builder()
                                .id(series.getId())
                                .name(series.getName())
                                .description(series.getDescription())
                                .build()
                );
            }
        });
        return result;
    }

    /**
     * 5.18 构建放送类型信息。
     *
     * @param broadcastType 放送类型实体
     * @return 放送类型信息
     */
    private BroadcastTypeVO buildBroadcastTypeVO(BroadcastType broadcastType) {
        if (broadcastType == null) {
            return null;
        }
        return BroadcastTypeVO.builder()
                .id(broadcastType.getId())
                .name(broadcastType.getName())
                .build();
    }

    /**
     * 5.19 构建改编类型信息。
     *
     * @param adaptationType 改编类型实体
     * @return 改编类型信息
     */
    private AdaptationTypeVO buildAdaptationTypeVO(AdaptationType adaptationType) {
        if (adaptationType == null) {
            return null;
        }
        return AdaptationTypeVO.builder()
                .id(adaptationType.getId())
                .name(adaptationType.getName())
                .build();
    }

    /**
     * 5.20 构建地区信息。
     *
     * @param region 地区实体
     * @return 地区信息
     */
    private RegionVO buildRegionVO(Region region) {
        if (region == null) {
            return null;
        }
        return RegionVO.builder()
                .id(region.getId())
                .name(region.getName())
                .build();
    }

    /**
     * 5.21 根据实体 ID 构建映射。
     *
     * @param entities 实体列表
     * @param idGetter ID 获取方法
     * @return 实体映射
     * @param <T> 实体类型
     */
    private <T> Map<Long, T> buildEntityMap(List<T> entities, Function<T, Long> idGetter) {
        return entities.stream().collect(Collectors.toMap(
                idGetter,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new
        ));
    }

    /**
     * 放送日期范围。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    private record BroadcastDateRange(LocalDate startDate, LocalDate endDate) {
    }


    /**
     * 通用别名数据清洗方法。
     *
     * @param aliasNames 别名列表
     * @return 去重去空后的别名列表
     */
    private List<String> buildAliasNames(List<String> aliasNames) {
        // 如果别名列表为空，则返回空列表。
        if (aliasNames == null || aliasNames.isEmpty()) {
            return List.of();
        }
        // 去重去空后返回。
        return aliasNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 通用动画公司关联数据清洗方法。
     *
     * @param companies 公司关联列表
     * @return 去重后的公司关联列表
     */
    private List<CompanyRelationDTO> buildCompanies(List<CompanyRelationDTO> companies) {
        // 如果公司关联列表为空，则返回空列表。
        if (companies == null || companies.isEmpty()) {
            return List.of();
        }
        // 按公司 ID 去重，保留第一次传入的职责。
        Map<Long, CompanyRelationDTO> companyMap = new LinkedHashMap<>();
        companies.forEach(company -> {
            if (company == null || company.getCompanyId() == null) {
                return;
            }
            // 判断公司是否存在。
            if (companyMapper.selectById(company.getCompanyId()) == null) {
                throw new BusinessException("公司不存在");
            }
            CompanyRelationDTO dto = new CompanyRelationDTO();
            dto.setCompanyId(company.getCompanyId());
            dto.setRole(StringUtils.hasText(company.getRole()) ? company.getRole().trim() : null);
            companyMap.putIfAbsent(dto.getCompanyId(), dto);
        });
        return new ArrayList<>(companyMap.values());
    }

    /**
     * 通用外部链接数据清洗方法。
     *
     * @param externalLinks 外部链接列表
     * @return 去重去空后的外部链接列表
     */
    private List<ExternalLinkDTO> buildExternalLinks(List<ExternalLinkDTO> externalLinks) {
        // 如果外部链接列表为空，则返回空列表。
        if (externalLinks == null || externalLinks.isEmpty()) {
            return List.of();
        }
        // 按 URL 去重，保留第一次传入的链接信息。
        Map<String, ExternalLinkDTO> externalLinkMap = new LinkedHashMap<>();
        externalLinks.forEach(externalLink -> {
            if (externalLink == null || !StringUtils.hasText(externalLink.getUrl())) {
                return;
            }
            String url = externalLink.getUrl().trim();
            ExternalLinkDTO dto = new ExternalLinkDTO();
            dto.setTitle(StringUtils.hasText(externalLink.getTitle()) ? externalLink.getTitle().trim() : null);
            dto.setUrl(url);
            dto.setSortOrder(externalLink.getSortOrder() == null ? 0L : externalLink.getSortOrder());
            externalLinkMap.putIfAbsent(url, dto);
        });
        return new ArrayList<>(externalLinkMap.values());
    }

    /**
     * 通用标签关联数据清洗方法。
     *
     * @param tagIds 标签 ID 列表
     * @return 去重后的标签 ID 列表
     */
    private List<Long> buildTagIds(List<Long> tagIds) {
        // 如果标签 ID 列表为空，则返回空列表。
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        // 按标签 ID 去重，并校验标签是否存在。
        Map<Long, Long> tagMap = new LinkedHashMap<>();
        tagIds.forEach(tagId -> {
            if (tagId == null) {
                return;
            }
            if (tagMapper.selectById(tagId) == null) {
                throw new BusinessException("标签不存在");
            }
            tagMap.putIfAbsent(tagId, tagId);
        });
        return new ArrayList<>(tagMap.values());
    }


}
