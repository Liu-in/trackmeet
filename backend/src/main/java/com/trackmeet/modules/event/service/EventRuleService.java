package com.trackmeet.modules.event.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.event.dto.EventRuleConfigSaveDTO;
import com.trackmeet.modules.event.entity.EventRuleConfig;
import com.trackmeet.modules.event.vo.EventRuleConfigVO;
import com.trackmeet.modules.event.vo.MeetRuleConfigVO;

/**
 * 项目级规则服务（三层继承中间层：{@code event_rule_config}）。
 * <p>
 * 读取优先级：{@code event_rule_config}（项目级）&gt; {@code meet_rule_config}（届次级）&gt;
 * 系统兜底默认常量；返回的 {@link EventRuleConfigVO} 每个字段都带
 * {@code xxxSource}（PROJECT / MEET / DEFAULT）来源标记。
 *
 * @author TrackMeet
 */
public interface EventRuleService extends IService<EventRuleConfig> {

    /**
     * 查询项目级规则的原始记录（不存在返回 {@code null}）。
     *
     * @param eventId 项目 ID
     * @return 项目级规则记录
     */
    EventRuleConfig getRaw(Long eventId);

    /**
     * 查询项目级生效规则：按「项目级 &gt; 届次级 &gt; 系统默认」逐字段回退，并标注每个字段的来源。
     *
     * @param eventId 项目 ID
     * @return 带来源标记的项目级规则视图
     */
    EventRuleConfigVO getRuleConfig(Long eventId);

    /**
     * 查询项目所属届次的生效默认规则（已套用系统兜底默认）。
     *
     * @param eventId 项目 ID
     * @return 届次级生效规则视图
     */
    MeetRuleConfigVO getMeetRuleConfig(Long eventId);

    /**
     * 保存项目级规则：不存在则插入、存在则更新；仅写入非空字段（空值语义为「继承上一层」）。
     *
     * @param eventId 项目 ID
     * @param dto     入参
     * @return 项目级规则记录主键
     */
    Long saveRuleConfig(Long eventId, EventRuleConfigSaveDTO dto);

    /**
     * 恢复继承：物理删除该项目的项目级规则行，使全部配置回退到届次级 / 系统默认。
     *
     * @param eventId 项目 ID
     * @return 是否删除到记录（{@code false} 表示本来就未配置专项规则）
     */
    boolean resetRuleConfig(Long eventId);
}
