package org.killze.acgbox.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.killze.acgbox.entity.content.CompanyRelation;

import java.util.List;

public interface CompanyRelationMapper {

    /**
     * 创建公司关系
     * @param companyRelation
     * @return
     */
    @Insert("""
            INSERT INTO company_relation (target_type, target_id, company_id, role, created_at, updated_at)
            VALUES (#{targetType}, #{targetId}, #{companyId}, #{role}, #{createdAt}, #{updatedAt})
            """)
    int insert(CompanyRelation companyRelation);

    /**
     * 根据关联对象查询公司关系
     *
     * @param targetType 关联对象类型
     * @param targetId 关联对象 ID
     * @return 公司关系列表
     */
    @Select("""
            SELECT id, target_type, target_id, company_id, role, created_at, updated_at
            FROM company_relation
            WHERE target_type = #{targetType}
              AND target_id = #{targetId}
            ORDER BY id ASC
            """)
    List<CompanyRelation> selectByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
