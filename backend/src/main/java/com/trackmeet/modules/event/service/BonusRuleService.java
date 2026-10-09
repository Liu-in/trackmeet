package com.trackmeet.modules.event.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.event.dto.BonusRuleSaveDTO;
import com.trackmeet.modules.event.entity.BonusRule;
import com.trackmeet.modules.event.vo.BonusRuleVO;

/**
 * 加分规则服务：破纪录 / 接力双倍 / 破平局三类加分规则的 CRUD。
 *
 * @author TrackMeet
 */
public interface BonusRuleService extends IService<BonusRule> {

    /**
     * 加分规则分页查询（可按 meetId、bonusType、status 过滤）。
     *
     * @param meetId    届次 ID（可空）
     * @param bonusType 加分类型（可空）
     * @param status    状态（可空）
     * @param keyword   名称 / 编码关键字（可空）
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @return 分页结果
     */
    PageResult<BonusRuleVO> pageRule(Long meetId, String bonusType, String status, String keyword,
                                     long page, long size);

    /**
     * 加分规则详情。
     *
     * @param id 规则 ID
     * @return 规则视图
     */
    BonusRuleVO getRuleDetail(Long id);

    /**
     * 新增加分规则：校验 {@code rule_code} 届次内唯一；
     * {@code bonus_type} 取值 RECORD 破纪录 / RELAY 接力双倍 / BREAK_TIE 破平局。
     *
     * @param dto 入参
     * @return 新增主键
     */
    Long createRule(BonusRuleSaveDTO dto);

    /**
     * 修改加分规则：校验存在性与 {@code rule_code} 届次内唯一（排除自身）。
     *
     * @param id  规则 ID
     * @param dto 入参
     */
    void updateRule(Long id, BonusRuleSaveDTO dto);

    /**
     * 删除加分规则。
     *
     * @param id 规则 ID
     */
    void deleteRule(Long id);

    /**
     * 规则实体存在性校验（不存在抛 404）。
     *
     * @param id 规则 ID
     * @return 规则实体
     */
    BonusRule getEntity(Long id);
}
