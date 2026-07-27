package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系列作品条目实体，对应 series_item 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("series_item")
public class SeriesItem {

    /**
     * 系列作品条目 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系列 ID，关联 series 表。
     */
    @TableField("series_id")
    private Long seriesId;

    /**
     * 作品类型，例如 anime、manga、novel。
     */
    @TableField("work_type")
    private String workType;

    /**
     * 作品 ID，对应 work_type 指向的作品表主键。
     */
    @TableField("work_id")
    private Long workId;

    /**
     * 系列内排序值，数值越小越靠前。
     */
    @TableField("sort_order")
    private Long sortOrder;
}
