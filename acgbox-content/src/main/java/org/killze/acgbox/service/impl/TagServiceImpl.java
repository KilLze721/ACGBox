package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.killze.acgbox.dto.content.TagDTO;
import org.killze.acgbox.entity.content.Tag;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.TagMapper;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.TagService;
import org.killze.acgbox.vo.content.TagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 标签服务实现类。
 * </p>
 * @author killze
 */
@Service
public class TagServiceImpl implements TagService {

    @Autowired
    private TagMapper tagMapper;

    /**
     * 创建标签
     *
     * @param tagDTO 标签信息
     * @return 创建成功的标签信息
     */
    @Override
    public TagVO createTag(TagDTO tagDTO) {
        // 判断此标签是否存在
        Tag exist = tagMapper.selectOne(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getName, tagDTO.getName())
        );
        if (exist != null){
            throw new BusinessException("标签已存在");
        }
        // 创建标签
        Tag tag = Tag.builder()
                .name(tagDTO.getName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        tagMapper.insert(tag);
        // 返回标签信息
        return TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build();
    }

    /**
     * 修改标签
     *
     * @param tagDTO 标签信息
     * @return 更新的标签信息
     */
    @Override
    public TagVO updateTag(TagDTO tagDTO) {
        // 判断标签是否存在
        Tag tag = tagMapper.selectById(tagDTO.getId());
        if (tag == null){
            throw new BusinessException("标签不存在");
        }
        // 判断新名称是否被其他标签使用
        Tag exist = tagMapper.selectOne(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getName, tagDTO.getName())
                        .ne(Tag::getId, tagDTO.getId())
        );
        if (exist != null) {
            throw new BusinessException("标签已存在");
        }
        // 修改标签
        tag.setName(tagDTO.getName());
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.updateById(tag);
        // 返回标签信息
        return TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build();
    }

    @Override
    public void deleteTags(List<Integer> ids) {
        // 判断ids是否为空
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的标签");
        }
        // TODO 判断标签是否被作品引用
        // 删除标签
        tagMapper.deleteByIds(ids);
    }
}
