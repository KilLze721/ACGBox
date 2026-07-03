package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.SeriesDTO;
import org.killze.acgbox.dto.content.SeriesPageDTO;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.SeriesVO;

import java.util.List;

/**
 * 系列服务接口。
 *
 * @author killze
 */
public interface SeriesService {

    /**
     * 创建系列
     *
     * @param seriesDTO 系列信息
     * @return 系列信息
     */
    SeriesVO createSeries(@Valid SeriesDTO seriesDTO);

    /**
     * 修改系列
     *
     * @param seriesDTO 系列信息
     * @return 系列信息
     */
    SeriesVO updateSeries(@Valid SeriesDTO seriesDTO);

    /**
     * 删除系列
     *
     * @param ids 系列 ID 列表
     */
    void deleteSeries(List<Long> ids);

    /**
     * 根据 ID 查询系列
     *
     * @param id 系列 ID
     * @return 系列信息
     */
    SeriesVO getSeriesById(Long id);

    /**
     * 分页查询系列
     *
     * @param pageDTO 分页参数
     * @return 系列列表
     */
    PageVO<SeriesVO> pageSeries(SeriesPageDTO pageDTO);
}
