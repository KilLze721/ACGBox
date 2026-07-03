package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.entity.content.AdaptationType;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.AdaptationTypeMapper;
import org.killze.acgbox.service.AdaptationTypeService;
import org.killze.acgbox.vo.content.AdaptationTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 改编类型服务实现类
 *
 * @author killze
 */
@Service
public class AdaptationTypeServiceImpl implements AdaptationTypeService {

    @Autowired
    private AdaptationTypeMapper adaptationTypeMapper;

    /**
     * 创建改编类型
     *
     * @param dto 改编类型信息
     * @return 改编类型信息
     */
    @Override
    public AdaptationTypeVO createAdaptationType(AdaptationTypeDTO dto) {
        // 判断此改编类型是否存在
        AdaptationType exist = adaptationTypeMapper.selectOne(
                new LambdaQueryWrapper<AdaptationType>()
                        .eq(AdaptationType::getName, dto.getName())
        );
        if (exist != null) {
            throw new BusinessException("此改编类型已存在");
        }
        // 创建改编类型
        AdaptationType adaptationType = AdaptationType.builder()
                .name(dto.getName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        adaptationTypeMapper.insert(adaptationType);
        // 返回改编类型信息
        return AdaptationTypeVO.builder()
                .id(adaptationType.getId())
                .name(adaptationType.getName())
                .build();
    }

    /**
     * 修改改编类型
     *
     * @param dto 改编类型信息
     * @return 改编类型信息
     */
    @Override
    public AdaptationTypeVO updateAdaptationType(AdaptationTypeDTO dto) {
        // 判断改编类型是否存在
        AdaptationType adaptationType = adaptationTypeMapper.selectById(dto.getId());
        if (adaptationType == null) {
            throw new BusinessException("此改编类型不存在");
        }
        // 判断新名称是否被其他改编类型使用
        AdaptationType exist = adaptationTypeMapper.selectOne(
                new LambdaQueryWrapper<AdaptationType>()
                        .eq(AdaptationType::getName, dto.getName())
                        .ne(AdaptationType::getId, dto.getId())
        );
        if (exist != null) {
            throw new BusinessException("此改编类型已存在");
        }
        // 修改改编类型
        adaptationType.setName(dto.getName());
        adaptationType.setUpdatedAt(LocalDateTime.now());
        adaptationTypeMapper.updateById(adaptationType);
        // 返回改编类型信息
        return AdaptationTypeVO.builder()
                .id(adaptationType.getId())
                .name(adaptationType.getName())
                .build();
    }

    /**
     * 批量删除改编类型
     *
     * @param ids 改编类型 ID 列表
     */
    @Override
    public void deleteAdaptationTypes(List<Long> ids) {
        // 判断 ids 是否为空
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的改编类型");
        }
        // TODO 判断改编类型是否被作品引用

        // 删除改编类型
        adaptationTypeMapper.deleteByIds(ids);
    }

    /**
     * 根据 ID 获取改编类型
     *
     * @param id 改编类型 ID
     * @return 改编类型信息
     */
    @Override
    public AdaptationTypeVO getAdaptationTypeById(Long id) {
        // 判断改编类型是否存在
        AdaptationType adaptationType = adaptationTypeMapper.selectById(id);
        if (adaptationType == null) {
            throw new BusinessException("此改编类型不存在");
        }
        // 返回改编类型信息
        return AdaptationTypeVO.builder()
                .id(adaptationType.getId())
                .name(adaptationType.getName())
                .build();
    }

    /**
     * 获取全部改编类型
     *
     * @return 改编类型列表
     */
    @Override
    public List<AdaptationTypeVO> listAdaptationTypes() {
        // 查询全部改编类型
        List<AdaptationType> adaptationTypes = adaptationTypeMapper.selectList(
                new LambdaQueryWrapper<AdaptationType>()
                        .orderByAsc(AdaptationType::getName)
        );
        // 返回改编类型列表
        return adaptationTypes.stream()
                .map(adaptationType -> AdaptationTypeVO.builder()
                        .id(adaptationType.getId())
                        .name(adaptationType.getName())
                        .build())
                .toList();
    }
}
