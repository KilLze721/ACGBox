package org.killze.acgbox.service;

import org.killze.acgbox.dto.content.BroadcastTypeDTO;
import org.killze.acgbox.vo.content.BroadcastTypeVO;

import java.util.List;

/**
 * 放送类型服务接口
 *
 * @author killze
 */
public interface BroadcastTypeService {

    /**
     * 创建放送类型
     *
     * @param dto 放送类型信息
     * @return 放送类型信息
     */
    BroadcastTypeVO createBroadcastType(BroadcastTypeDTO dto);

    /**
     * 修改放送类型
     *
     * @param dto 放送类型信息
     * @return 放送类型信息
     */
    BroadcastTypeVO updateBroadcastType(BroadcastTypeDTO dto);

    /**
     * 批量删除放送类型
     *
     * @param ids 放送类型 ID 列表
     */
    void deleteBroadcastTypes(List<Long> ids);

    /**
     * 根据 ID 获取放送类型
     *
     * @param id 放送类型 ID
     * @return 放送类型信息
     */
    BroadcastTypeVO getBroadcastTypeById(Long id);

    /**
     * 获取全部放送类型
     *
     * @return 放送类型列表
     */
    List<BroadcastTypeVO> listBroadcastTypes();
}
