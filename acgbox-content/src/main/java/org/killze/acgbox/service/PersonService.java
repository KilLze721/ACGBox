package org.killze.acgbox.service;

import org.killze.acgbox.dto.content.PersonDTO;
import org.killze.acgbox.dto.content.PersonPageDTO;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.PersonVO;

import java.util.List;

/**
 * 人物资料服务接口。
 */
public interface PersonService {

    /**
     * 创建人物，允许同名。
     *
     * @param personDTO 人物资料
     * @return 创建后的人物资料
     */
    PersonVO createPerson(PersonDTO personDTO);

    /**
     * 修改已有的人物资料。
     *
     * @param personDTO 人物资料
     * @return 修改后的人物资料
     */
    PersonVO updatePerson(PersonDTO personDTO);

    /**
     * 整批删除人物，被任意作品引用时拒绝删除。
     *
     * @param ids 人物 ID 列表
     */
    void deletePersons(List<Long> ids);

    /**
     * 根据 ID 获取人物资料。
     *
     * @param id 人物 ID
     * @return 人物资料
     */
    PersonVO getPersonById(Long id);

    /**
     * 分页搜索人物，按更新时间和 ID 倒序。
     *
     * @param pageDTO 分页参数
     * @return 人物分页结果
     */
    PageVO<PersonVO> pagePerson(PersonPageDTO pageDTO);
}
