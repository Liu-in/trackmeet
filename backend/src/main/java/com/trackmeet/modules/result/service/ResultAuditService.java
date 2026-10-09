package com.trackmeet.modules.result.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.result.entity.ResultAudit;
import com.trackmeet.modules.result.vo.ResultAuditVO;

import java.util.List;
import java.util.Map;

/**
 * 成绩留痕服务：成绩提交、审核、驳回、修改、撤销的完整留痕链，
 * 保证「谁 / 何时 / 改了什么」可追溯（对 {@code result_audit} 表）。
 * <p>
 * 留痕快照 {@code before_json} / {@code after_json} 由服务层序列化成绩（含各次试跳）后写入，
 * 便于前端按字段对比展示原值 / 新值。
 *
 * @author TrackMeet
 */
public interface ResultAuditService extends IService<ResultAudit> {

    /**
     * 查询某条成绩的留痕链（操作时间倒序，最新在前）。
     *
     * @param resultId 成绩 ID
     * @return 留痕视图列表
     */
    List<ResultAuditVO> listByResult(Long resultId);

    /**
     * 写入一条留痕。
     *
     * @param resultId 成绩 ID
     * @param action   动作：SUBMIT/APPROVE/REJECT/MODIFY/REVOKE
     * @param before   变更前快照（可为空）
     * @param after    变更后快照（可为空）
     * @param reason   原因（修改原因 / 驳回原因 / 撤销原因）
     */
    void record(Long resultId, String action, Map<String, Object> before, Map<String, Object> after, String reason);

    /**
     * 按实体对象写入留痕（自动序列化变更前后快照）。
     *
     * @param beforeResult 变更前成绩实体（新增时为空）
     * @param afterResult  变更后成绩实体（删除时为空）
     * @param action       动作：SUBMIT/APPROVE/REJECT/MODIFY/REVOKE
     * @param reason       原因
     */
    void recordEntities(Object beforeResult, Object afterResult, String action, String reason);

    /**
     * 拍成绩快照（成绩字段 + 各次试跳），供留痕与前端对比展示。
     *
     * @param result 成绩实体，可为空
     * @return 快照 Map（空值返回空 Map）
     */
    Map<String, Object> snapshot(Object result);
}
