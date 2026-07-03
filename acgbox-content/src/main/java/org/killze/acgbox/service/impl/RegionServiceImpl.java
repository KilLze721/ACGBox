package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.killze.acgbox.dto.content.RegionDTO;
import org.killze.acgbox.entity.content.Region;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.RegionMapper;
import org.killze.acgbox.service.RegionService;
import org.killze.acgbox.vo.content.RegionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 地区服务实现类
 *
 * @author killze
 */
@Service
public class RegionServiceImpl implements RegionService {

    @Autowired
    private RegionMapper regionMapper;

    /**
     * 创建地区
     *
     * @param regionDTO 地区信息
     * @return 地区信息
     */
    @Override
    public RegionVO createRegion(RegionDTO regionDTO) {
        // 判断此地区是否存在
        Region exist = regionMapper.selectOne(
                new LambdaQueryWrapper<Region>()
                        .eq(Region::getName, regionDTO.getName())
        );
        if (exist != null) {
            throw new BusinessException("此地区已存在");
        }
        // 创建地区
        Region region = Region.builder()
                .name(regionDTO.getName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        regionMapper.insert(region);
        // 返回地区信息
        return RegionVO.builder()
                .id(region.getId())
                .name(region.getName())
                .build();
    }

    /**
     * 修改地区
     *
     * @param regionDTO 地区信息
     * @return 地区信息
     */
    @Override
    public RegionVO updateRegion(RegionDTO regionDTO) {
        // 判断地区是否存在
        Region region = regionMapper.selectById(regionDTO.getId());
        if (region == null) {
            throw new BusinessException("此地区不存在");
        }
        // 判断新名称是否被其他地区使用
        Region exist = regionMapper.selectOne(
                new LambdaQueryWrapper<Region>()
                        .eq(Region::getName, regionDTO.getName())
                        .ne(Region::getId, regionDTO.getId())
        );
        if (exist != null) {
            throw new BusinessException("此地区已存在");
        }
        // 修改地区
        region.setName(regionDTO.getName());
        region.setUpdatedAt(LocalDateTime.now());
        regionMapper.updateById(region);
        // 返回地区信息
        return RegionVO.builder()
                .id(region.getId())
                .name(region.getName())
                .build();
    }

    /**
     * 批量删除地区
     *
     * @param ids 地区 ID 列表
     */
    @Override
    public void deleteRegions(List<Long> ids) {
        // 判断 ids 是否为空
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的地区");
        }
        // TODO 判断地区是否被作品引用

        // 删除地区
        regionMapper.deleteByIds(ids);
    }

    /**
     * 根据 ID 获取地区
     *
     * @param id 地区 ID
     * @return 地区信息
     */
    @Override
    public RegionVO getRegionById(Long id) {
        // 判断地区是否存在
        Region region = regionMapper.selectById(id);
        if (region == null) {
            throw new BusinessException("此地区不存在");
        }
        // 返回地区信息
        return RegionVO.builder()
                .id(region.getId())
                .name(region.getName())
                .build();
    }

    /**
     * 获取全部地区
     *
     * @return 地区列表
     */
    @Override
    public List<RegionVO> listRegions() {
        // 查询全部地区
        List<Region> regions = regionMapper.selectList(
                new LambdaQueryWrapper<Region>()
                        .orderByAsc(Region::getName)
        );
        // 返回地区列表
        return regions.stream()
                .map(region -> RegionVO.builder()
                        .id(region.getId())
                        .name(region.getName())
                        .build())
                .toList();
    }
}
