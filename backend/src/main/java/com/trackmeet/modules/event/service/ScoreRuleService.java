package com.trackmeet.modules.event.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.event.dto.ScoreRuleItemDTO;
import com.trackmeet.modules.event.dto.ScoreRuleSaveDTO;
import com.trackmeet.modules.event.entity.ScoreRule;
import com.trackmeet.modules.event.vo.ScoreRuleVO;

import java.util.List;

/**
 * 名次分方案服务：方案 CRUD 与名次分明细批量维护。
 *
 * @author TrackMeet
 */
public interface ScoreRuleService extends IService<ScoreRule> {

    /**
     * 名次分方案分页查询（可按 meetId、status 过滤）。
     *
     * @param meetId 届次 ID（可空）
     * @param status 状态（可空）
     * @param keyword 名称 / 编码关键字（可空）
     * @param page   页码，从 1 开始
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<ScoreRuleVO> pageRule(Long meetId, String status, String keyword, long page, long size);

    /**
     * 方案详情（含明细，按 {@code rank_no} 升序）。
     *
     * @param id 方案 ID
     * @return 方案视图
     */
    ScoreRuleVO getRuleDetail(Long id);

    /**
     * 新增方案：校验 {@code rule_code} 届次内唯一。
     *
     * @param dto 入参
     * @return 新增主键
     */
    Long createRule(ScoreRuleSaveDTO dto);

    /**
     * 修改方案：校验存在性与 {@code rule_code} 届次内唯一（排除自身）。
     *
     * @param id  方案 ID
     * @param dto 入参
     */
    void updateRule(Long id, ScoreRuleSaveDTO dto);

    /**
     * 删除方案：{@code is_default = 1} 的默认方案禁删（抛 {@code CONFLICT}）；
     * 被届次默认规则或项目引用时同样抛 {@code CONFLICT}；通过校验后同时物理删除明细。
     *
     * @param id 方案 ID
     */
    void deleteRule(Long id);

    /**
     * 查询方案明细列表（按 {@code rank_no} 升序）。
     *
     * @param ruleId 方案 ID
     * @return 明细列表
     */
    List<ScoreRuleItemDTO> listItems(Long ruleId);

    /**
     * 批量保存方案明细：先按 {@code rule_id} 物理删除再批量插入，并回写 {@code score_rule.rank_count}。
     *
     * @param ruleId 方案 ID
     * @param items  明细列表（为空表示清空明细）
     */
    void saveItems(Long ruleId, List<ScoreRuleItemDTO> items);

    /**
     * 方案实体存在性校验（不存在抛 404）。
     *
     * @param id 方案 ID
     * @return 方案实体
     */
    ScoreRule getEntity(Long id);
}
