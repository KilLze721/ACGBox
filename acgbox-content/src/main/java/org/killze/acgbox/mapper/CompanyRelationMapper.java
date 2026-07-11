package org.killze.acgbox.mapper;

import org.apache.ibatis.annotations.Insert;
import org.killze.acgbox.entity.content.CompanyRelation;

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
}
