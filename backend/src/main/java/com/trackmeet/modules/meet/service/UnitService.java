package com.trackmeet.modules.meet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.meet.dto.UnitQuery;
import com.trackmeet.modules.meet.entity.Unit;

/**
 * 参赛单位服务：分页查询、详情、增改删。
 *
 * @author TrackMeet
 */
public interface UnitService extends IService<Unit> {

    /**
     * 分页查询参赛单位（meetId、keyword）。
     */
    PageResult<Unit> pageQuery(UnitQuery query);

    /**
     * 单位详情，不存在时抛 404。
     */
    Unit getDetail(Long id);

    /**
     * 新增单位（校验届次内 unit_code 唯一），返回主键。
     */
    Long create(Unit unit);

    /**
     * 修改单位（不允许变更所属届次）。
     */
    void update(Long id, Unit unit);

    /**
     * 删除单位：先改写 unit_code 释放唯一键，再逻辑删除。
     */
    void delete(Long id);
}
