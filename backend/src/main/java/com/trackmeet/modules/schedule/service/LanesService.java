package com.trackmeet.modules.schedule.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.schedule.dto.LaneAssignDTO;
import com.trackmeet.modules.schedule.dto.LaneSwapDTO;
import com.trackmeet.modules.schedule.entity.Lane;
import com.trackmeet.modules.schedule.vo.LaneVO;

import java.util.List;

/**
 * 道次服务：批量分配、手工改道次、两道次交换。
 *
 * @author TrackMeet
 */
public interface LanesService extends IService<Lane> {

    /**
     * 批量道次分配（对若干分组按指定方式重排道次）。
     *
     * @param dto 入参（heatIds / scheduleId / mode / laneCount / randomSeed）
     * @return 落库后的道次视图列表
     */
    List<LaneVO> assign(LaneAssignDTO dto);

    /**
     * 手工修改道次号；目标道次已被占用时抛 {@code BizException(CONFLICT)}。
     *
     * @param id    道次记录 ID
     * @param laneNo 目标道次号（从 1 开始）
     */
    void updateLaneNo(Long id, Integer laneNo);

    /**
     * 交换同一分组内的两条道次（保留交换留痕）。
     *
     * @param dto 入参（laneIdA / laneIdB / swapReason）
     */
    void swap(LaneSwapDTO dto);

    /**
     * 道次实体存在性校验（不存在抛 404，跨届次抛 403）。
     *
     * @param id 道次 ID
     * @return 道次实体
     */
    Lane getEntity(Long id);
}
