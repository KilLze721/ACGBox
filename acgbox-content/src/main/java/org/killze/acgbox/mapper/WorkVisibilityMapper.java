package org.killze.acgbox.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.killze.acgbox.entity.content.WorkVisibility;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 联合主键状态读写，调用方在真实作品的事务中保存和清理。 */
public interface WorkVisibilityMapper {
    /** 按类型批量读取当前页的状态，缺少记录的作品由调用方视为公开。 */
    @Select("""
            <script>
            SELECT target_type, target_id, is_public FROM work_visibility
            WHERE target_type = #{targetType} AND target_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<WorkVisibility> selectByTargets(@Param("targetType") String targetType, @Param("ids") List<Long> ids);

    /** 返回按作品 ID 索引的状态，供详情和列表复用批量查询。 */
    default Map<Long, Boolean> selectStates(String targetType, List<Long> ids) {
        return selectByTargets(targetType, ids).stream()
                .collect(Collectors.toMap(WorkVisibility::getTargetId, WorkVisibility::getIsPublic));
    }

    /** 显式开关按联合主键幂等保存，不把状态写入动画或书籍主表。 */
    @Insert("""
            INSERT INTO work_visibility (target_type, target_id, is_public)
            VALUES (#{targetType}, #{targetId}, #{isPublic})
            ON CONFLICT (target_type, target_id) DO UPDATE SET is_public = EXCLUDED.is_public
            """)
    int save(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("isPublic") Boolean isPublic);

    /** 仅清理被删除作品所属类型的状态，不影响同数字 ID 的其他类型。 */
    @Delete("""
            <script>
            DELETE FROM work_visibility WHERE target_type = #{targetType} AND target_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByTargets(@Param("targetType") String targetType, @Param("ids") List<Long> ids);
}
