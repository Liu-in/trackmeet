package com.trackmeet.modules.result.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.result.dto.ResultAttemptDTO;
import com.trackmeet.modules.result.entity.ResultAttempt;

import java.util.List;

/**
 * 田赛试跳试掷服务（对 {@code result_attempt} 表）。
 * <p>
 * 试跳明细是田赛名次平局比较（次优成绩 / 最远成功轮次）与成绩复核的依据；
 * 每次新增或修改后回写「最好一次」标记，并同步所属成绩的最好成绩。
 *
 * @author TrackMeet
 */
public interface ResultAttemptService extends IService<ResultAttempt> {

    /**
     * 新增单次试跳/试掷（同成绩内试跳序号唯一）。
     *
     * @param dto 试跳入参
     * @return 试跳记录 ID
     */
    Long saveAttempt(ResultAttemptDTO dto);

    /**
     * 修改单次试跳/试掷。
     *
     * @param id  试跳记录 ID
     * @param dto 试跳入参
     */
    void updateAttempt(Long id, ResultAttemptDTO dto);

    /**
     * 删除单次试跳/试掷（逻辑删除）。
     *
     * @param id 试跳记录 ID
     */
    void removeAttempt(Long id);

    /**
     * 查询某条成绩的全部试跳明细（按试跳总序号升序）。
     *
     * @param resultId 成绩 ID
     * @return 试跳明细列表
     */
    List<ResultAttempt> listByResult(Long resultId);

    /**
     * 按成绩 ID 批量重建试跳明细（先物理删除后按序插入，保证唯一键不冲突）。
     *
     * @param resultId 成绩 ID
     * @param dtos     试跳明细（可为空，为空表示清空）
     */
    void replaceAttempts(Long resultId, List<ResultAttemptDTO> dtos);
}
