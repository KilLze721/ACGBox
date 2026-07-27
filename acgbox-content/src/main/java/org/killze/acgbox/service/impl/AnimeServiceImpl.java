package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.CompanyRelationDTO;
import org.killze.acgbox.dto.content.ExternalLinkDTO;
import org.killze.acgbox.entity.content.*;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AliasMapper;
import org.killze.acgbox.mapper.AnimeMapper;
import org.killze.acgbox.mapper.CompanyMapper;
import org.killze.acgbox.mapper.CompanyRelationMapper;
import org.killze.acgbox.mapper.ExternalLinkMapper;
import org.killze.acgbox.mapper.PersonalRatingMapper;
import org.killze.acgbox.mapper.SeriesItemMapper;
import org.killze.acgbox.mapper.SeriesMapper;
import org.killze.acgbox.mapper.TagMapper;
import org.killze.acgbox.mapper.TagRelationMapper;
import org.killze.acgbox.service.AnimeService;
import org.killze.acgbox.vo.content.AnimeVO;
import org.killze.acgbox.vo.content.CompanyRelationVO;
import org.killze.acgbox.vo.content.ExternalLinkVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
