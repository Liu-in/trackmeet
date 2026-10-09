package com.trackmeet.modules.schedule.service.support;

import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.modules.schedule.vo.AthleteEntryRowVO;
import com.trackmeet.modules.schedule.vo.HeatMemberVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 装配行 → 引擎输入（{@link AthleteEntry}）的转换工厂。
 * <p>
 * 分组、分道、田赛出场顺序共用同一份装配数据（报名 × 运动员 × 单位），
 * 差异仅在「种子成绩字段来源」与「接力标记」：报名行取 {@code registration.seed_value}，
 * 组内人员行取 {@code heat_member.seed_result}（列名 {@code seed_result}，属性名 {@code seedResult}）。
 *
 * @author TrackMeet
 */
@Component
public class AthleteEntryFactory {

    /**
     * 装配行列表 → 引擎输入列表。
     *
     * @param rows 装配行（可为空）
     * @return 引擎输入列表（永不为 null）
     */
    public List<AthleteEntry> fromRows(List<AthleteEntryRowVO> rows) {
        List<AthleteEntry> entries = new ArrayList<>();
        if (rows == null) {
            return entries;
        }
        for (AthleteEntryRowVO row : rows) {
            if (row != null) {
                entries.add(fromRow(row));
            }
        }
        return entries;
    }

    /**
     * 单条装配行 → 引擎输入。
     */
    public AthleteEntry fromRow(AthleteEntryRowVO row) {
        if (row == null) {
            return null;
        }
        return AthleteEntry.builder()
                .registrationId(row.getRegistrationId())
                .athleteId(row.getAthleteId())
                .athleteName(row.getAthleteName())
                .unitId(row.getUnitId())
                .unitName(row.getUnitName())
                .bibNo(row.getBibNo())
                .seedValue(row.getSeedValue())
                .seedResult(row.getSeedResultRaw())
                .relay(row.getIsRelay() != null && row.getIsRelay() == 1)
                .build();
    }

    /**
     * 组内人员（含冗余展示字段）→ 引擎输入；用于对既有分组重新分道。
     * <p>
     * 注意：{@code heat_member.seed_result} 是「报名最好成绩数值」，属性名
     * {@link com.trackmeet.modules.schedule.vo.HeatMemberVO#getSeedResult()}。
     *
     * @param memberVO 组内人员视图对象（可为空）
     * @return 引擎输入，输入为空返回 null
     */
    public AthleteEntry fromMember(HeatMemberVO member) {
        if (member == null) {
            return null;
        }
        return AthleteEntry.builder()
                .registrationId(member.getRegistrationId())
                .athleteId(member.getAthleteId())
                .athleteName(member.getAthleteName())
                .unitId(member.getUnitId())
                .unitName(member.getUnitName())
                .bibNo(member.getBibNo())
                .seedValue(member.getSeedResult())
                .seedResult(member.getSeedResultRaw())
                .relay(false)
                .build();
    }

    /**
     * 组内人员列表 → 引擎输入列表（保持传入顺序，即组内 order_no 顺序）。
     *
     * @param members 组内人员列表（可为空）
     * @return 引擎输入列表（永不为 null）
     */
    public List<AthleteEntry> fromMembers(List<HeatMemberVO> members) {
        List<AthleteEntry> entries = new ArrayList<>();
        if (members == null) {
            return entries;
        }
        for (HeatMemberVO member : members) {
            AthleteEntry entry = fromMember(member);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }
}
