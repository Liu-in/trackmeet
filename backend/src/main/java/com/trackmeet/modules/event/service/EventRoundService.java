package com.trackmeet.modules.event.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.event.dto.EventRoundRuleSaveDTO;
import com.trackmeet.modules.event.dto.EventRoundSaveDTO;
import com.trackmeet.modules.event.entity.EventRound;
import com.trackmeet.modules.event.vo.EventRoundVO;

import java.util.List;

/**
 * 轮次服务：项目内轮次 CRUD 与轮次顺序 / 决赛标记维护。
 *
 * @author TrackMeet
 */
public interface EventRoundService extends IService<EventRound> {

    /**
     * 查询项目下的轮次列表（按 {@code round_no} 升序，含轮次级规则）。
     *
     * @param eventId 项目 ID
     * @return 轮次视图列表
     */
    List<EventRoundVO> listByEventId(Long eventId);

    /**
     * 轮次详情（不存在抛 404），含轮次级规则。
     *
     * @param id 轮次 ID
     * @return 轮次视图
     */
    EventRoundVO getRoundDetail(Long id);

    /**
     * 新增轮次：校验 {@code round_code} 项目内唯一；自动维护 {@code round_no} 顺序与 {@code is_final}；
     * 并同步项目的 {@code round_count}。
     *
     * @param eventId 项目 ID
     * @param dto     入参
     * @return 新增主键
     */
    Long createRound(Long eventId, EventRoundSaveDTO dto);

    /**
     * 修改轮次：可改 {@code round_code / round_name / plan_heat_count / plan_advance_count / status} 等字段。
     *
     * @param id  轮次 ID
     * @param dto 入参
     */
    void updateRound(Long id, EventRoundSaveDTO dto);

    /**
     * 删除轮次：存在下级轮次引用（{@code event_round_rule.source_round_id}）时抛 {@code CONFLICT}；
     * 通过校验后级联清理轮次级规则并重排项目内 {@code round_no}。
     *
     * @param id 轮次 ID
     */
    void deleteRound(Long id);

    /**
     * 轮次实体存在性校验（不存在抛 404）。
     *
     * @param id 轮次 ID
     * @return 轮次实体
     */
    EventRound getEntity(Long id);

    /**
     * 保存轮次级规则与录取名次：{@code round_id} 唯一，先查后插 / 更新；
     * {@code advanceJson} / {@code tieBreakJson} 必须为合法 JSON，否则抛 {@code PARAM_ERROR}。
     *
     * @param roundId 轮次 ID
     * @param dto     入参
     * @return 规则记录主键
     */
    Long saveRoundRule(Long roundId, EventRoundRuleSaveDTO dto);
}
