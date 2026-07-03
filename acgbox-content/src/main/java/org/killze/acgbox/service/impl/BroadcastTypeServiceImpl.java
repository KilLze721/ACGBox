package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.killze.acgbox.dto.content.BroadcastTypeDTO;
import org.killze.acgbox.entity.content.BroadcastType;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.BroadcastTypeMapper;
import org.killze.acgbox.service.BroadcastTypeService;
import org.killze.acgbox.vo.content.BroadcastTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 放送类型服务实现类
 *
 * @author killze
 */
@Service
public class BroadcastTypeServiceImpl implements BroadcastTypeService {

    @Autowired
    private BroadcastTypeMapper broadcastTypeMapper;

    /**
     * 创建放送类型
     *
     * @param dto 放送类型信息
     * @return 放送类型信息
     */
    @Override
    public BroadcastTypeVO createBroadcastType(BroadcastTypeDTO dto) {
        // 判断此放送类型是否存在
        BroadcastType exist = broadcastTypeMapper.selectOne(
                new LambdaQueryWrapper<BroadcastType>()
                        .eq(BroadcastType::getName, dto.getName())
        );
        if (exist != null) {
            throw new BusinessException("此放送类型已存在");
        }
        // 创建放送类型
        BroadcastType broadcastType = BroadcastType.builder()
                .name(dto.getName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        broadcastTypeMapper.insert(broadcastType);
        // 返回放送类型信息
        return BroadcastTypeVO.builder()
                .id(broadcastType.getId())
                .name(broadcastType.getName())
                .build();
    }

    /**
     * 修改放送类型
     *
     * @param dto 放送类型信息
     * @return 放送类型信息
     */
    @Override
    public BroadcastTypeVO updateBroadcastType(BroadcastTypeDTO dto) {
        // 判断放送类型是否存在
        BroadcastType broadcastType = broadcastTypeMapper.selectById(dto.getId());
        if (broadcastType == null) {
            throw new BusinessException("此放送类型不存在");
        }
        // 判断新名称是否被其他放送类型使用
        BroadcastType exist = broadcastTypeMapper.selectOne(
                new LambdaQueryWrapper<BroadcastType>()
                        .eq(BroadcastType::getName, dto.getName())
                        .ne(BroadcastType::getId, dto.getId())
        );
        if (exist != null) {
            throw new BusinessException("此放送类型已存在");
        }
        // 修改放送类型
        broadcastType.setName(dto.getName());
        broadcastType.setUpdatedAt(LocalDateTime.now());
        broadcastTypeMapper.updateById(broadcastType);
        // 返回放送类型信息
        return BroadcastTypeVO.builder()
                .id(broadcastType.getId())
                .name(broadcastType.getName())
                .build();
    }

    /**
     * 批量删除放送类型
     *
     * @param ids 放送类型 ID 列表
     */
    @Override
    public void deleteBroadcastTypes(List<Long> ids) {
        // 判断 ids 是否为空
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的放送类型");
        }
        // TODO 判断放送类型是否被作品引用

        // 删除放送类型
        broadcastTypeMapper.deleteByIds(ids);
    }

    /**
     * 根据 ID 获取放送类型
     *
     * @param id 放送类型 ID
     * @return 放送类型信息
     */
    @Override
    public BroadcastTypeVO getBroadcastTypeById(Long id) {
        // 判断放送类型是否存在
        BroadcastType broadcastType = broadcastTypeMapper.selectById(id);
        if (broadcastType == null) {
            throw new BusinessException("此放送类型不存在");
        }
        // 返回放送类型信息
        return BroadcastTypeVO.builder()
                .id(broadcastType.getId())
                .name(broadcastType.getName())
                .build();
    }

    /**
     * 获取全部放送类型
     *
     * @return 放送类型列表
     */
    @Override
    public List<BroadcastTypeVO> listBroadcastTypes() {
        // 查询全部放送类型
        List<BroadcastType> list = broadcastTypeMapper.selectList(
                new LambdaQueryWrapper<BroadcastType>()
                        .orderByAsc(BroadcastType::getName)
        );
        // 返回放送类型列表
        return list.stream()
                .map(broadcastType -> BroadcastTypeVO.builder()
                        .id(broadcastType.getId())
                        .name(broadcastType.getName())
                        .build())
                .toList();
    }
}
