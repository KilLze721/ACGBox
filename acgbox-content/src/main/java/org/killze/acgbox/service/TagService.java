package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.TagDTO;
import org.killze.acgbox.dto.content.TagPageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.TagVO;

import java.util.List;

/**
 * <p>
 * 标签服务接口。
 * </p>
 *
 * @author killze
 */
public interface TagService {
    /**
     * 创建标签
     *
     * @param tagDTO 标签信息
     * @return 标签信息
     */
    TagVO createTag(@Valid TagDTO tagDTO);

    /**
     * 修改标签
     *
     * @param tagDTO 标签信息
     * @return 标签信息
     */
    TagVO updateTag(@Valid  TagDTO tagDTO);

    /**
     * 删除标签
     *
     * @param ids 标签ID列表
     */
    void deleteTags(List<Integer> ids);

    /**
     * 根据ID查询标签
     *
     * @param id 标签ID
     * @return 标签信息
     */
    TagVO getTagById(Integer id);

    /**
     * 分页查询标签
     *
     * @param pageDTO 分页参数
     * @return 标签列表
     */
    PageVO<TagVO> pageTag(TagPageDTO pageDTO);
}
