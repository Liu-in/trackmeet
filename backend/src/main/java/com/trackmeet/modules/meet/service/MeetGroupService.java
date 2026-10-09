package com.trackmeet.modules.meet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.meet.dto.MeetGroupQuery;
import com.trackmeet.modules.meet.entity.MeetGroup;

import java.util.List;

/**
 * 组别服务：分页查询、详情、增改删。
 *
 * @author TrackMeet
 */
public interface MeetGroupService extends IService<MeetGroup> {

    /**
     * 分页查询组别（meetId、gender、keyword）。
     */
    PageResult<MeetGroup> pageQuery(MeetGroupQuery query);

    /**
     * 组别详情，不存在时抛 404。
     */
    MeetGroup getDetail(Long id);

    /**
     * 查询指定届次的全部组别（按排序号升序）。
     */
    List<MeetGroup> listByMeetId(Long meetId);

    /**
     * 新增组别（校验届次内 group_code 唯一），返回主键。
     */
    Long create(MeetGroup group);

    /**
     * 修改组别（不允许变更所属届次）。
     */
    void update(Long id, MeetGroup group);

    /**
     * 删除组别：先改写 group_code 释放唯一键，再逻辑删除。
     */
    void delete(Long id);
}
