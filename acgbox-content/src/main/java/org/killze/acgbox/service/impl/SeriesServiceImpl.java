package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.content.SeriesDTO;
import org.killze.acgbox.dto.content.SeriesPageDTO;
import org.killze.acgbox.entity.content.Series;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.SeriesMapper;
import org.killze.acgbox.service.SeriesService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.SeriesVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系列服务实现类。
 *
 * @author killze
 */
@Service
public class SeriesServiceImpl implements SeriesService {

    @Autowired
    private SeriesMapper seriesMapper;

    /**
     * 创建系列
     *
     * @param seriesDTO 系列信息
     * @return 创建成功的系列信息
     */
    @Override
    public SeriesVO createSeries(SeriesDTO seriesDTO) {
        // 判断此系列是否存在
        Series exist = seriesMapper.selectOne(
                new LambdaQueryWrapper<Series>()
                        .eq(Series::getName, seriesDTO.getName())
        );
        if (exist != null) {
            throw new BusinessException("此系列已存在");
        }
        // 创建系列
        Series series = Series.builder()
                .name(seriesDTO.getName())
                .description(seriesDTO.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        seriesMapper.insert(series);
        // 返回系列信息
        return buildSeriesVO(series);
    }

    /**
     * 修改系列
     *
     * @param seriesDTO 系列信息
     * @return 更新后的系列信息
     */
    @Override
    public SeriesVO updateSeries(SeriesDTO seriesDTO) {
        // 判断系列是否存在
        Series series = seriesMapper.selectById(seriesDTO.getId());
        if (series == null) {
            throw new BusinessException("此系列不存在");
        }
        // 判断新名称是否被其他系列使用
        Series exist = seriesMapper.selectOne(
                new LambdaQueryWrapper<Series>()
                        .eq(Series::getName, seriesDTO.getName())
                        .ne(Series::getId, seriesDTO.getId())
        );
        if (exist != null) {
            throw new BusinessException("此系列已存在");
        }
        // 修改系列
        series.setName(seriesDTO.getName());
        series.setDescription(seriesDTO.getDescription());
        series.setUpdatedAt(LocalDateTime.now());
        seriesMapper.updateById(series);
        // 返回系列信息
        return buildSeriesVO(series);
    }

    /**
     * 批量删除系列
     *
     * @param ids 系列 ID 列表
     */
    @Override
    public void deleteSeries(List<Long> ids) {
        // 判断 ids 是否为空
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的系列");
        }
        // 删除系列
        seriesMapper.deleteByIds(ids);
    }

    /**
     * 根据 ID 获取系列信息
     *
     * @param id 系列 ID
     * @return 系列信息
     */
    @Override
    public SeriesVO getSeriesById(Long id) {
        // 判断系列是否存在
        Series series = seriesMapper.selectById(id);
        if (series == null) {
            throw new BusinessException("此系列不存在");
        }
        // 返回系列信息
        return buildSeriesVO(series);
    }

    /**
     * 分页查询系列
     *
     * @param pageDTO 分页参数
     * @return 系列列表
     */
    @Override
    public PageVO<SeriesVO> pageSeries(SeriesPageDTO pageDTO) {
        // 创建分页对象
        Page<Series> page = new Page<>(pageDTO.getPageNum(), pageDTO.getPageSize());
        // 模糊名称查询
        LambdaQueryWrapper<Series> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(
                // 判断名称是否为空
                StringUtils.hasText(pageDTO.getName()),
                Series::getName,
                pageDTO.getName()
        );
        wrapper.orderByDesc(Series::getUpdatedAt);
        // 分页查询
        Page<Series> result = seriesMapper.selectPage(page, wrapper);
        // 转换成 VO
        List<SeriesVO> rows = result.getRecords().stream().map(this::buildSeriesVO).toList();
        // 返回结果
        return PageVO.<SeriesVO>builder()
                .total(result.getTotal())
                .rows(rows)
                .build();
    }

    private SeriesVO buildSeriesVO(Series series) {
        return SeriesVO.builder()
                .id(series.getId())
                .name(series.getName())
                .description(series.getDescription())
                .build();
    }
}
