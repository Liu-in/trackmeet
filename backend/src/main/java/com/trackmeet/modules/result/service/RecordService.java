package com.trackmeet.modules.result.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.result.dto.RecordConfirmDTO;
import com.trackmeet.modules.result.dto.RecordQuery;
import com.trackmeet.modules.result.dto.RecordSaveDTO;
import com.trackmeet.modules.result.entity.Record;
import com.trackmeet.modules.result.vo.RecordVO;

import java.util.List;

/**
 * 纪录服务（对 {@code record} 表）：纪录库维护 + 破纪录认定 + 破纪录候选扫描。
 * <p>
 * 破纪录认定回写 {@code result.is_record_confirmed} 与 {@code result.record_id}；
 * 超风速成绩（{@code is_wind_legal = 0}）不允许认定，抛 {@code WIND_ILLEGAL}（风速超限，纪录不予认定），
 * 但保留 {@code is_record_candidate} 候选位以便现场复核。
 *
 * @author TrackMeet
 */
public interface RecordService extends IService<Record> {

    /**
     * 纪录分页列表。
     *
     * @param query 查询条件
     * @return 纪录视图分页
     */
    PageResult<RecordVO> pageRecord(RecordQuery query);

    /**
     * 新增纪录。
     *
     * @param dto 纪录入参
     * @return 纪录 ID
     */
    Long createRecord(RecordSaveDTO dto);

    /**
     * 修改纪录（不允许跨届次迁移）。
     *
     * @param id  纪录 ID
     * @param dto 纪录入参
     */
    void updateRecord(Long id, RecordSaveDTO dto);

    /**
     * 删除纪录（被成绩引用时拒绝删除）。
     *
     * @param id 纪录 ID
     */
    void removeRecord(Long id);

    /**
     * 破纪录认定 / 取消认定。
     *
     * @param dto 认定入参
     */
    void confirmRecord(RecordConfirmDTO dto);

    /**
     * 查看某项目某轮次的破纪录候选（与现行纪录对标）。
     *
     * @param eventId 项目 ID
     * @param roundId 轮次 ID（可空）
     * @return 候选成绩视图列表
     */
    List<com.trackmeet.modules.result.vo.ResultVO> listRecordCandidates(Long eventId, Long roundId);

    /**
     * 扫描并回写破纪录候选标记（{@code result.is_record_candidate} / {@code record_id} / {@code record_type}）。
     *
     * @param eventId 项目 ID
     * @param roundId 轮次 ID（可空）
     * @return 命中的候选条数
     */
    int scanRecordCandidates(Long eventId, Long roundId);
}
