package com.trackmeet.modules.schedule.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.schedule.dto.HeatMemberMoveDTO;
import com.trackmeet.modules.schedule.dto.HeatMemberSaveDTO;
import com.trackmeet.modules.schedule.dto.HeatsGenerateDTO;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.vo.HeatGenerateResultVO;
import com.trackmeet.modules.schedule.vo.HeatMemberVO;
import com.trackmeet.modules.schedule.vo.HeatVO;

import java.util.List;

/**
 * 分组服务：自动分组分道、分组查询、组内人员增删改与锁定。
 *
 * @author TrackMeet
 */
public interface HeatsService extends IService<Heat> {

    /**
     * 生成分组分道（{@code apply=false} 仅预览，不写库）。
     * <p>
     * 锁定保护：{@code heat.is_locked=1} 的既有分组整组保留（成员与道次原样），
     * 仅对未锁定组的选手重新分组，锁定组成员不进入重排池。
     *
     * @param dto 入参
     * @return 分组与道次结果
     */
    HeatGenerateResultVO generate(HeatsGenerateDTO dto);

    /**
     * 日程下的分组列表（含组内人员、道次与田赛出场顺序）。
     *
     * @param scheduleId 日程 ID
     * @return 分组视图列表
     */
    List<HeatVO> listHeats(Long scheduleId);

    /**
     * 分组实体存在性校验（不存在抛 404，跨届次抛 403）。
     *
     * @param id 分组 ID
     * @return 分组实体
     */
    Heat getEntity(Long id);

    /**
     * 组内人员列表（按 order_no 升序）。
     *
     * @param heatId 分组 ID
     * @return 人员视图列表
     */
    List<HeatMemberVO> listMembers(Long heatId);

    /**
     * 添加人员到分组（幂等：同一报名记录已在组内时仅调整顺序号）。
     *
     * @param heatId 分组 ID
     * @param dto    入参（registrationId / orderNo）
     * @return 组内人员主键
     */
    Long addMember(Long heatId, HeatMemberSaveDTO dto);

    /**
     * 从分组移除人员（同时清理其道次与田赛出场顺序）。
     *
     * @param heatId  分组 ID
     * @param memberId 组内人员 ID
     */
    void removeMember(Long heatId, Long memberId);

    /**
     * 组间拖拽移动成员（换组后重排目标组顺序号）。
     *
     * @param dto 入参（memberId / targetHeatId / targetOrderNo）
     */
    void moveMember(HeatMemberMoveDTO dto);

    /**
     * 锁定/解锁分组。
     *
     * @param id       分组 ID
     * @param isLocked 0 未锁定 / 1 已锁定（锁定后重新生成分组时跳过本组）
     */
    void lockHeat(Long id, Integer isLocked);
}
