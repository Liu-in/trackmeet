package com.trackmeet.modules.schedule.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.schedule.dto.FieldOrderGenerateDTO;
import com.trackmeet.modules.schedule.dto.FieldOrderReorderDTO;
import com.trackmeet.modules.schedule.entity.FieldOrder;
import com.trackmeet.modules.schedule.vo.FieldOrderVO;

import java.util.List;

/**
 * 田赛出场顺序服务：生成（随机/按成绩/按单位/按号码）与手工调整顺序。
 *
 * @author TrackMeet
 */
public interface FieldOrdersService extends IService<FieldOrder> {

    /**
     * 生成田赛出场顺序（{@code apply=false} 仅预览）。
     * <p>
     * {@code FieldOrder.is_locked=1} 的既有顺序不重建。
     *
     * @param dto 入参（scheduleId / mode / apply / randomSeed）
     * @return 出场顺序视图列表
     */
    List<FieldOrderVO> generate(FieldOrderGenerateDTO dto);

    /**
     * 按 {@code memberIds} 数组顺序重写分组内 {@code order_no}（从 1 开始）。
     *
     * @param dto 入参（heatId / memberIds）
     */
    void reorder(FieldOrderReorderDTO dto);
}
