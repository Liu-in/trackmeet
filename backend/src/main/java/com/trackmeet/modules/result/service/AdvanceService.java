package com.trackmeet.modules.result.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.result.dto.AdvanceGenerateDTO;
import com.trackmeet.modules.result.dto.AdvanceUpdateDTO;
import com.trackmeet.modules.result.entity.AdvanceResult;
import com.trackmeet.modules.result.vo.AdvanceRowVO;

import java.util.List;

/**
 * 晋级名单服务（对 {@code advance_result} 表）。
 * <p>
 * 自动晋级调用 {@code com.trackmeet.engine.advance.AdvanceCalculator#calculate}：
 * 「每组前 N 名（按 {@code heatNo} 分组）+ 剩余最好成绩递补至目标人数」，
 * 晋级方式落 {@code GROUP_TOP} / {@code BEST_LOSER}，人工调整后置 {@code MANUAL}；
 * 生成采用「先物理删除后插入」幂等重建，{@code is_confirmed} 统一置 0，待人工确认生效。
 *
 * @author TrackMeet
 */
public interface AdvanceService extends IService<AdvanceResult> {

    /**
     * 自动晋级：按来源轮次成绩生成目标轮次名单（幂等重建）。
     *
     * @param dto 晋级入参（缺省值取 {@code event_round_rule}）
     * @return 生成的晋级名单视图（按晋级顺序）
     */
    List<AdvanceRowVO> generateAdvance(AdvanceGenerateDTO dto);

    /**
     * 晋级名单查询。
     *
     * @param eventId     项目 ID（可空）
     * @param toRoundId   目标轮次 ID（可空）
     * @param fromRoundId 来源轮次 ID（可空）
     * @return 晋级名单视图列表
     */
    List<AdvanceRowVO> listAdvance(Long eventId, Long toRoundId, Long fromRoundId);

    /**
     * 手动调整晋级名单：把该条置为 {@code MANUAL} 并重排顺序号。
     *
     * @param id  晋级名单 ID
     * @param dto 调整入参
     * @return 调整后的晋级名单视图
     */
    AdvanceRowVO updateAdvance(Long id, AdvanceUpdateDTO dto);

    /**
     * 确认晋级名单生效（按项目 × 目标轮次批量置 {@code is_confirmed = 1}）。
     *
     * @param eventId   项目 ID
     * @param toRoundId 目标轮次 ID
     * @return 确认后的晋级名单视图列表
     */
    List<AdvanceRowVO> confirmAdvance(Long eventId, Long toRoundId);
}
