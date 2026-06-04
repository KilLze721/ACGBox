package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 放送类型实体，对应 broadcast_type 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("broadcast_type")
public class BroadcastType {

    /**
     * 放送类型 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 放送类型名称，例如 TV、WEB、OVA、剧场版。
     */
    private String name;
}
