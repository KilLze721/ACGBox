package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.killze.acgbox.dto.content.TagDTO;
import org.killze.acgbox.dto.content.TagPageDTO;
import org.killze.acgbox.entity.content.Tag;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.TagMapper;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.TagService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.TagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    /**
     * 批量删除标签
     *
     * @param ids 标签ID列表
     */
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

    /**
     * 根据ID获取标签信息
     *
     * @param id 标签ID
     * @return 标签信息
     */
    @Override
    public TagVO getTagById(Integer id) {
        // 判断标签是否存在
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException("标签不存在");
        }
        // 返回标签信息
        return TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build();
    }

    /**
     * 分页查询标签
     *
     * @param pageDTO 分页参数
     * @return 标签列表
     */
    @Override
    public PageVO<TagVO> pageTag(TagPageDTO pageDTO) {
        // 创建分页对象
        Page<Tag> page = new Page<>(pageDTO.getPageNum(), pageDTO.getPageSize());
        // 模糊名称查询
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(
                // 判断名称是否为空
                StringUtils.hasText(pageDTO.getName()),
                Tag::getName,
                pageDTO.getName()
        );
        // 分页查询
        Page<Tag> result = tagMapper.selectPage(page, wrapper);
        // 转换成VO
        List<TagVO> rows = result.getRecords().stream().map(tag -> TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build()).toList();
        // 返回结果
        return PageVO.<TagVO>builder()
                .total(result.getTotal())
                .rows(rows)
                .build();
    }


}
