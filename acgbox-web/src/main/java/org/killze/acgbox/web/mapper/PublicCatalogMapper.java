package org.killze.acgbox.web.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.killze.acgbox.entity.content.Tag;
import org.killze.acgbox.web.dto.PublicCatalogPageDTO;

import java.time.LocalDate;
import java.util.List;

/** 公开目录分页和关系摘要，表名只由固定的动画或书籍分支决定。 */
public interface PublicCatalogMapper {
    /** 仅查询公开作品，分页及计数使用同一份条件。 */
    Page<WorkRow> selectPage(Page<WorkRow> page, @Param("anime") boolean anime,
                             @Param("filter") PublicCatalogPageDTO filter, @Param("pattern") String pattern,
                             @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** 公开作品的年份集合，null 用于表示存在未知日期。 */
    List<Integer> selectYears(@Param("anime") boolean anime);

    /** 只读取当前类型公开作品实际使用的标签。 */
    List<Tag> selectFilterTags(@Param("anime") boolean anime);

    /** 按当前页作品 ID 批量读取人员摘要。 */
    List<RelationRow> selectPersons(@Param("type") String type, @Param("ids") List<Long> ids);

    /** 按当前页作品 ID 批量读取公司摘要。 */
    List<RelationRow> selectCompanies(@Param("type") String type, @Param("ids") List<Long> ids);

    /** 列表主查询内部投影，明确列出目录所需的字段。 */
    record WorkRow(Long id, String name, String coverImageUrl, Long status, LocalDate workDate,
                   Long typeId, String typeName, Long adaptationTypeId, String adaptationTypeName) { }

    /** 关系查询内部投影，按作品归组后转换为人物或公司响应。 */
    record RelationRow(Long workId, Long id, String name, String role) { }
}
