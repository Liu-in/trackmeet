package com.trackmeet.modules.standing.service.support;

import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.modules.standing.dto.StandingAttemptRow;
import com.trackmeet.modules.standing.dto.StandingResultRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 装配器：「库内成绩投影」→ 引擎 {@link ResultRecord}。
 * <p>
 * 本类只做字段搬运与取值口径统一，<b>不含任何计算逻辑</b>（算法一律来自
 * {@code com.trackmeet.engine}）：
 * <ul>
 *     <li>{@code rankNo} 只取 {@code result.rank_no}（由成绩模块的 RankResolver 回写）；</li>
 *     <li>{@code status} 取 {@code result.status}；</li>
 *     <li>{@code windLegal} 取 {@code result.is_wind_legal}；</li>
 *     <li>{@code recordCandidate} 取 {@code result.is_record_candidate}；</li>
 *     <li>{@code recordType} 取 {@code result.record_type}；</li>
 *     <li>{@code relay} 优先取 {@code registration.is_relay}，为空时回落 {@code event.is_relay}；</li>
 *     <li>{@code gender} 取 {@code event.gender}（团体总分 MALE/FEMALE/TOTAL 口径的输入）；</li>
 *     <li>{@code attempts} 取 {@code result_attempt.attempt_value}，失败/免跳为 {@code null} 占位。</li>
 * </ul>
 *
 * @author TrackMeet
 */
@Component
public class ResultRecordAssembler {

    /**
     * 单条成绩投影 → 引擎成绩记录。
     *
     * @param row      成绩投影行
     * @param attempts 该成绩的试跳成绩值列表（按轮次顺序，可为空）
     * @return 引擎成绩记录，{@code row} 为空时返回 {@code null}
     */
    public ResultRecord toRecord(StandingResultRow row, List<BigDecimal> attempts) {
        if (row == null) {
            return null;
        }
        ResultRecord record = new ResultRecord();
        record.setRegistrationId(row.getRegistrationId());
        record.setAthleteId(row.getAthleteId());
        record.setAthleteName(row.getAthleteName());
        record.setUnitId(row.getUnitId());
        record.setGroupId(row.getGroupId());
        record.setGender(row.getGender());
        record.setResultRaw(row.getResultRaw());
        record.setResultValue(row.getResultValue());
        record.setResultUnit(row.getResultUnit());
        record.setRankNo(row.getRankNo());
        record.setRankDisplay(row.getRankDisplay());
        record.setStatus(row.getStatus());
        record.setWindSpeed(row.getWindSpeed());
        record.setWindLegal(row.getWindLegal() != null && row.getWindLegal() == 1);
        record.setRelay(resolveRelay(row));
        record.setRecordCandidate(row.getRecordCandidate() != null && row.getRecordCandidate() == 1);
        record.setRecordType(row.getRecordType());
        record.setAttempts(attempts == null ? new ArrayList<>() : attempts);
        return record;
    }

    /**
     * 批量装配：把成绩投影行按 {@code resultId} 关联试跳成绩后转为引擎模型。
     *
     * @param rows     成绩投影行列表
     * @param attempts 试跳投影行列表（可空）
     * @return 引擎成绩记录列表（与 {@code rows} 同序）
     */
    public List<ResultRecord> toRecords(List<StandingResultRow> rows, List<StandingAttemptRow> attempts) {
        List<ResultRecord> records = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return records;
        }
        Map<Long, List<BigDecimal>> attemptMap = groupAttempts(attempts);
        for (StandingResultRow row : rows) {
            records.add(toRecord(row, attemptMap.get(row.getResultId())));
        }
        return records;
    }

    /**
     * 试跳投影 → 成绩 ID 的有序成绩值列表（失败与免跳以 {@code null} 占位，保持轮次顺序）。
     *
     * @param attempts 试跳投影行列表（可空）
     * @return 成绩 ID → 成绩值列表
     */
    public Map<Long, List<BigDecimal>> groupAttempts(List<StandingAttemptRow> attempts) {
        Map<Long, List<StandingAttemptRow>> grouped = new HashMap<>();
        if (attempts != null) {
            for (StandingAttemptRow attempt : attempts) {
                if (attempt == null || attempt.getResultId() == null) {
                    continue;
                }
                grouped.computeIfAbsent(attempt.getResultId(), key -> new ArrayList<>()).add(attempt);
            }
        }
        Map<Long, List<BigDecimal>> values = new HashMap<>(grouped.size());
        for (Map.Entry<Long, List<StandingAttemptRow>> entry : grouped.entrySet()) {
            List<StandingAttemptRow> list = entry.getValue();
            list.sort(Comparator.comparing(row -> row.getAttemptNo() == null ? Integer.MAX_VALUE : row.getAttemptNo()));
            List<BigDecimal> target = new ArrayList<>(list.size());
            for (StandingAttemptRow row : list) {
                boolean failed = (row.getSuccess() != null && row.getSuccess() == 0)
                        || (row.getPass() != null && row.getPass() == 1);
                target.add(failed ? null : row.getAttemptValue());
            }
            values.put(entry.getKey(), target);
        }
        return values;
    }

    /**
     * 接力判定：优先 {@code registration.is_relay}，为空时回落 {@code event.is_relay}。
     *
     * @param row 成绩投影行
     * @return {@code true} 表示接力项目
     */
    private boolean resolveRelay(StandingResultRow row) {
        if (row.getRegistrationRelay() != null) {
            return row.getRegistrationRelay() == 1;
        }
        return row.getEventRelay() != null && row.getEventRelay() == 1;
    }
}
