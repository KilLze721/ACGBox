package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.RegionDTO;
import org.killze.acgbox.vo.content.RegionVO;

import java.util.List;

/**
 * 地区服务接口
 *
 * @author killze
 */
public interface RegionService {

    /**
     * 创建地区
     *
     * @param regionDTO 地区信息
     * @return 地区信息
     */
    RegionVO createRegion(@Valid RegionDTO regionDTO);

    /**
     * 修改地区
     *
     * @param regionDTO 地区信息
     * @return 地区信息
     */
    RegionVO updateRegion(@Valid RegionDTO regionDTO);

    /**
     * 批量删除地区
     *
     * @param ids 地区 ID 列表
     */
    void deleteRegions(List<Long> ids);

    /**
     * 根据 ID 获取地区
     *
     * @param id 地区 ID
     * @return 地区信息
     */
    RegionVO getRegionById(Long id);

    /**
     * 查询地区
     *
     * @return 地区列表
     */
    List<RegionVO> listRegions();
}
