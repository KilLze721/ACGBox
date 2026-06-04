package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公司实体，对应 company 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("company")
public class Company {

    /**
     * 公司 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 公司名称。
     */
    private String name;
}
