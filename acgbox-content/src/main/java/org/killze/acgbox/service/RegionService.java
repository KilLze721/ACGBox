package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.RegionDTO;
import org.killze.acgbox.vo.content.RegionVO;

import java.util.List;

public interface RegionService {
    
    /**
     * 创建地区
     *
     * @param regionDTO 地区信息
     * @return 创建成功的地区信息
     */
    RegionVO createRegion(@Valid RegionDTO regionDTO);

    /**
     * 修改地区
     *
     * @param regionDTO 地区信息
     * @return 更新的地区信息
     */
    RegionVO updateRegion(@Valid RegionDTO regionDTO);

    /**
     * 批量删除地区
     *
     * @param ids 地区ID列表
     */
    void deleteRegions(List<Integer> ids);

    /**
     * 根据id获取地区
     *
     * @param id 地区ID
     * @return 地区信息
     */
    RegionVO getRegionById(Integer id);

    /**
     * 查询地区
     *
     * @return 地区列表
     */
    List<RegionVO> listRegions();
}
