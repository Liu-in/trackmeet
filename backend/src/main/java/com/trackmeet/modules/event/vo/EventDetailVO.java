package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目详情视图对象：项目 + 轮次列表 + 项目级规则 + 届次默认规则。
 * <p>
 * { 项目详情聚合 }：{@code ruleConfig} 已套用「项目级 &gt; 届次级 &gt; 系统默认」三层继承并标注来源，
 * {@code meetRuleConfig} 为届次级生效默认值（已套用系统兜底默认），便于前端展示「继承自哪一层」。
 *
 * @author TrackMeet
 */
@Data
public class EventDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目主体 */
    private EventVO event;

    /** 轮次列表（按 round_no 升序，含轮次级规则） */
    private List<EventRoundVO> rounds = new ArrayList<>();

    /** 项目级规则（含来源标记） */
    private EventRuleConfigVO ruleConfig;

    /** 届次级默认规则（生效值） */
    private MeetRuleConfigVO meetRuleConfig;
}
