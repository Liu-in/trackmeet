package com.trackmeet.modules.result.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.result.dto.ResultApproveDTO;
import com.trackmeet.modules.result.dto.ResultAttemptDTO;
import com.trackmeet.modules.result.dto.ResultBatchSaveDTO;
import com.trackmeet.modules.result.dto.ResultQuery;
import com.trackmeet.modules.result.dto.ResultRejectDTO;
import com.trackmeet.modules.result.dto.ResultSaveDTO;
import com.trackmeet.modules.result.dto.ResultStatusDTO;
import com.trackmeet.modules.result.dto.ResultWindDTO;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.vo.EntrySheetRowVO;
import com.trackmeet.modules.result.vo.ResultVO;

import java.util.List;

/**
 * 成绩服务（对 {@code result} 表）。
 * <p>
 * 覆盖成绩录入、修改、特殊状态、风速、审核流（提交 / 通过 / 驳回 / 撤销）与设备导入，
 * 每次写操作都往 {@code result_audit} 写留痕；审核通过后 {@code is_locked = 1}，
 * 未撤销审核前禁止直接改值或改状态。
 * <p>
 * 成绩双列：{@code result_raw}（显示原文）+ {@code result_value}（可比较数值，径赛秒 / 田赛米 / 全能分）。
 *
 * @author TrackMeet
 */
public interface ResultService extends IService<Result> {

    /**
     * 成绩分页列表。
     *
     * @param query 查询条件（eventId/roundId/heatId/unitId/auditStatus/status/keyword）
     * @return 成绩视图分页
     */
    PageResult<ResultVO> pageResult(ResultQuery query);

    /**
     * 成绩录入表：按日程条目（或项目 + 轮次）列出组次 / 道次 / 号码 / 姓名 / 单位 / 种子成绩 / 已有成绩 / 状态。
     *
     * @param scheduleId 日程条目 ID（与 eventId + roundId 二选一）
     * @param eventId    项目 ID
     * @param roundId    轮次 ID
     * @return 录入表行列表
     */
    List<EntrySheetRowVO> entrySheet(Long scheduleId, Long eventId, Long roundId);

    /**
     * 录入成绩（同一日程条目内同一报名唯一，重复录入按修改处理）。
     *
     * @param dto 成绩入参
     * @return 成绩 ID
     */
    Long createResult(ResultSaveDTO dto);

    /**
     * 批量录入（一组一次提交）。
     *
     * @param dto 批量入参
     * @return 写入的成绩 ID 列表
     */
    List<Long> batchSaveResult(ResultBatchSaveDTO dto);

    /**
     * 修改成绩（已审核锁定抛 {@code RESULT_LOCKED}）。
     *
     * @param id  成绩 ID
     * @param dto 成绩入参
     */
    void updateResult(Long id, ResultSaveDTO dto);

    /**
     * 田赛单次试跳/试掷录入（新增或按试跳序号覆盖）。
     *
     * @param dto 试跳入参
     * @return 试跳记录 ID
     */
    Long saveAttempt(ResultAttemptDTO dto);

    /**
     * 田赛单次试跳/试掷修改。
     *
     * @param id  试跳记录 ID
     * @param dto 试跳入参
     * @return 试跳记录 ID
     */
    Long updateAttempt(Long id, ResultAttemptDTO dto);

    /**
     * 批量标记特殊状态（DNS/DNF/DSQ/NM/PASS/SCRATCH）。
     * <p>
     * 标记 {@code DSQ} 时 {@code foulType} 必填，否则抛 {@code PARAM_ERROR}；
     * 无效状态会把 {@code result_value} 与名次置空。
     *
     * @param dto 状态入参
     * @return 受影响行数
     */
    int updateStatus(ResultStatusDTO dto);

    /**
     * 风速录入（组级或单人）：写 {@code wind_reading} 并回填 {@code result.wind_speed/is_wind_legal}。
     *
     * @param dto 风速入参
     * @return 受影响成绩条数
     */
    int updateWind(ResultWindDTO dto);

    /**
     * 提交审核（DRAFT/REJECTED → SUBMITTED）。
     *
     * @param ids 成绩 ID 列表
     * @return 受影响行数
     */
    int submit(List<Long> ids);

    /**
     * 审核通过（→ APPROVED，{@code is_locked = 1}），并自动触发生成名次。
     *
     * @param dto 审核入参
     * @return 受影响行数
     */
    int approve(ResultApproveDTO dto);

    /**
     * 审核驳回（→ REJECTED，写入驳回原因）。
     *
     * @param dto 驳回入参
     * @return 受影响行数
     */
    int reject(ResultRejectDTO dto);

    /**
     * 撤销审核（APPROVED → SUBMITTED，{@code is_locked = 0}），并清理本轮名次。
     *
     * @param id     成绩 ID
     * @param reason 撤销原因
     */
    void revokeApproval(Long id, String reason);

    /**
     * 查询某条成绩的修改留痕（谁 / 何时 / 改了什么）。
     *
     * @param resultId 成绩 ID
     * @return 留痕视图列表（操作时间倒序）
     */
    List<com.trackmeet.modules.result.vo.ResultAuditVO> listAudits(Long resultId);

    /**
     * 设备成绩文件导入（CSV/Excel）。
     * <p>
     * 当前为接口占位：返回明确的「尚未开放」业务异常，避免静默失败；
     * 导入批次留档表 {@code result_import_log} 与解析映射待后续迭代实现。
     *
     * @param scheduleId 目标日程条目 ID
     * @param overwrite  是否覆盖已有成绩
     * @return 成功导入条数（占位实现恒为 0）
     */
    int importResults(Long scheduleId, boolean overwrite);
}
