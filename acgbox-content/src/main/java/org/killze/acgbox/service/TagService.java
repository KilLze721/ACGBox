package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.TagDTO;
import org.killze.acgbox.result.Result;
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
    TagVO createTag(TagDTO tagDTO);

    /**
     * 修改标签
     *
     * @param tagDTO 标签信息
     * @return 标签信息
     */
    TagVO updateTag(TagDTO tagDTO);

    /**
     * 删除标签
     *
     * @param ids 标签ID列表
     */
    void deleteTags(List<Integer> ids);
}
