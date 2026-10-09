package com.trackmeet.modules.athlete.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.athlete.dto.RegistrationApproveDTO;
import com.trackmeet.modules.athlete.dto.RegistrationBatchDTO;
import com.trackmeet.modules.athlete.dto.RegistrationCheckDTO;
import com.trackmeet.modules.athlete.dto.RegistrationQuery;
import com.trackmeet.modules.athlete.dto.RegistrationRejectDTO;
import com.trackmeet.modules.athlete.dto.RegistrationSaveDTO;
import com.trackmeet.modules.athlete.entity.Registration;
import com.trackmeet.modules.athlete.vo.RegistrationCheckResultVO;
import com.trackmeet.modules.athlete.vo.RegistrationStatVO;
import com.trackmeet.modules.athlete.vo.RegistrationVO;
import com.trackmeet.modules.athlete.vo.RegistrationWarningVO;

import java.util.Collection;
import java.util.List;

/**
 * 报名服务：报名增删改、限制校验、审核、统计预警、报名截止锁定与限报计数器维护。
 *
 * @author TrackMeet
 */
public interface RegistrationService extends IService<Registration> {

    /**
     * 报名分页列表（联表投影出运动员姓名、单位名、项目名、组别名）。
     */
    PageResult<RegistrationVO> pageRegistration(RegistrationQuery query);

    /**
     * 报名详情（含接力队员明细），不存在抛 404。
     */
    RegistrationVO getRegistrationDetail(Long id);

    /**
     * 新增单条报名（个人/接力）：先校验，存在 ERROR 则抛业务异常；返回报名 ID。
     */
    Long createRegistration(RegistrationSaveDTO dto);

    /**
     * 批量报名（一个单位一次提交一个项目的多名运动员；接力时 athleteIds 为一支队的队员）。
     *
     * @return 新增的报名 ID 列表
     */
    List<Long> batchRegistration(RegistrationBatchDTO dto);

    /**
     * 修改报名：更新种子成绩与接力队员，并重跑限制校验。
     */
    void updateRegistration(Long id, RegistrationSaveDTO dto);

    /**
     * 取消报名：状态置 CANCELLED 并逻辑删除，同时释放唯一键以便重新报名。
     */
    void cancelRegistration(Long id);

    /**
     * 批量限制校验（不落库）。
     */
    RegistrationCheckResultVO check(RegistrationCheckDTO dto);

    /**
     * 已落库报名的校验快照与问题清单。
     */
    RegistrationCheckResultVO getCheckResult(RegistrationQuery query);

    /**
     * 批量审核通过。
     *
     * @return 审核通过的记录数
     */
    int approve(RegistrationApproveDTO dto);

    /**
     * 退回报名（附退回原因）。
     */
    void reject(Long id, RegistrationRejectDTO dto);

    /**
     * 报名统计：dimension = UNIT | EVENT | GROUP。
     */
    List<RegistrationStatVO> statistics(String dimension);

    /**
     * 报名不足预警（已通过报名数低于预警线的项目）。
     */
    List<RegistrationWarningVO> warnings();

    /**
     * 截止并锁定报名：届次状态 REG_OPEN → REG_CLOSED，非法状态抛 MEET_STATUS_ERROR。
     * 届次取当前上下文（请求头 X-Meet-Id 或 meetId 参数）。
     */
    void lockRegistration();

    /**
     * 截止并锁定报名（显式指定届次，仍会与上下文交叉校验）。
     */
    void lockRegistration(Long meetId);

    /**
     * 重算「单位 × 项目」限报计数与相关运动员项数计数（幂等：先物理删除再插入）。
     */
    void refreshCounters(Long meetId, Long unitId, Long eventId);

    /**
     * 重算限报计数，并额外重算指定运动员的项数计数（用于接力换人后清理旧队员计数）。
     */
    void refreshCounters(Long meetId, Long unitId, Long eventId, Collection<Long> extraAthleteIds);
}
