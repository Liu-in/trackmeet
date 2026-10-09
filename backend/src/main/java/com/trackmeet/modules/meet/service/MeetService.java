package com.trackmeet.modules.meet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.meet.dto.MeetQuery;
import com.trackmeet.modules.meet.entity.Meet;
import com.trackmeet.modules.meet.entity.MeetGroup;

import java.util.List;

/**
 * 运动会（届次）服务：分页查询、增改删、状态流转与届次下组别管理。
 *
 * @author TrackMeet
 */
public interface MeetService extends IService<Meet> {

    /**
     * 分页查询运动会（keyword 模糊匹配名称/届次标识/简称）。
     */
    PageResult<Meet> pageQuery(MeetQuery query);

    /**
     * 运动会详情，不存在时抛 404。
     */
    Meet getDetail(Long id);

    /**
     * 新增运动会（状态固定为 DRAFT），返回主键。
     */
    Long create(Meet meet);

    /**
     * 修改运动会（状态不在此接口变更）。
     */
    void update(Long id, Meet meet);

    /**
     * 删除运动会（仅 DRAFT / ARCHIVED 允许）。
     */
    void delete(Long id);

    /**
     * 状态流转：只允许按状态机顺序前进一步，ARCHIVED 为终态。
     */
    void changeStatus(Long id, String targetStatus);

    /**
     * 查询指定届次下的组别列表（不分页）。
     */
    List<MeetGroup> listGroups(Long meetId);

    /**
     * 在指定届次下新增组别（自动设置 meetId），返回主键。
     */
    Long addGroup(Long meetId, MeetGroup group);
}
