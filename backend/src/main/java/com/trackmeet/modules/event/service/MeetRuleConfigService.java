package com.trackmeet.modules.event.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.event.dto.MeetRuleConfigSaveDTO;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import com.trackmeet.modules.event.vo.MeetRuleConfigVO;

/**
 * 届次级默认规则服务（三层继承最外层：{@code meet_rule_config}）。
 * <p>
 * 读取语义：{@link #getEffective(Long)} 返回已套用系统兜底默认的生效值；
 * {@link #getRaw(Long)} 返回库中原始值（未配置字段为 {@code null}）。
 *
 * @author TrackMeet
 */
public interface MeetRuleConfigService extends IService<MeetRuleConfig> {

    /**
     * 查询届次级规则的原始记录（不存在返回 {@code null}）。
     *
     * @param meetId 届次 ID
     * @return 届次级规则记录
     */
    MeetRuleConfig getRaw(Long meetId);

    /**
     * 查询届次级生效规则：库中值为空时套用系统兜底默认常量。
     *
     * @param meetId 届次 ID
     * @return 生效规则视图（字段已全量填充）
     */
    MeetRuleConfigVO getEffective(Long meetId);

    /**
     * 保存届次级规则：按 {@code meet_id} 不存在则插入、存在则更新（唯一键 {@code uk_meet_id}）。
     * <p>
     * 仅写入非空字段与集合字段整体替换；{@code null} 字段保持原值（新增时即库列默认值 / 系统兜底默认）。
     *
     * @param meetId 届次 ID
     * @param dto    入参
     * @return 届次级规则记录主键
     */
    Long saveByMeetId(Long meetId, MeetRuleConfigSaveDTO dto);

    /**
     * 按届次 ID 解析「届次级 + 系统兜底默认」的生效规则实体，供项目级规则继承回退使用。
     *
     * @param meetId 届次 ID
     * @return 生效规则实体（字段已全量填充，可直接作为项目级规则的取值来源）
     */
    MeetRuleConfig resolveEffective(Long meetId);
}
