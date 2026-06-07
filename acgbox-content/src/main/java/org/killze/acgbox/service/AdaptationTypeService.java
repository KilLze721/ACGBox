package org.killze.acgbox.service;

import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.vo.content.AdaptationTypeVO;

import java.util.List;

/**
 * 改编类型服务接口。
 *
 * @author killze
 */
public interface AdaptationTypeService {

    /**
     * 创建改编类型
     */
    AdaptationTypeVO createAdaptationType(AdaptationTypeDTO dto);

    /**
     * 修改改编类型
     */
    AdaptationTypeVO updateAdaptationType(AdaptationTypeDTO dto);

    /**
     * 批量删除改编类型
     */
    void deleteAdaptationTypes(List<Integer> ids);

    /**
     * 根据ID获取改编类型
     */
    AdaptationTypeVO getAdaptationTypeById(
            Integer id
    );

    /**
     * 获取全部改编类型
     */
    List<AdaptationTypeVO> listAdaptationTypes();
}
