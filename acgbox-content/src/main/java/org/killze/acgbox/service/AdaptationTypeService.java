package org.killze.acgbox.service;

import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.vo.content.AdaptationTypeVO;

import java.util.List;

/**
 * 改编类型服务接口
 *
 * @author killze
 */
public interface AdaptationTypeService {

    /**
     * 创建改编类型
     *
     * @param dto 改编类型信息
     * @return 改编类型信息
     */
    AdaptationTypeVO createAdaptationType(AdaptationTypeDTO dto);

    /**
     * 修改改编类型
     *
     * @param dto 改编类型信息
     * @return 改编类型信息
     */
    AdaptationTypeVO updateAdaptationType(AdaptationTypeDTO dto);

    /**
     * 批量删除改编类型
     *
     * @param ids 改编类型 ID 列表
     */
    void deleteAdaptationTypes(List<Long> ids);

    /**
     * 根据 ID 获取改编类型
     *
     * @param id 改编类型 ID
     * @return 改编类型信息
     */
    AdaptationTypeVO getAdaptationTypeById(Long id);

    /**
     * 获取全部改编类型
     *
     * @return 改编类型列表
     */
    List<AdaptationTypeVO> listAdaptationTypes();
}
