package com.trackmeet.modules.event.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.event.dto.EventBatchGenerateDTO;
import com.trackmeet.modules.event.dto.EventQuery;
import com.trackmeet.modules.event.dto.EventSaveDTO;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.vo.EventBatchGenerateResultVO;
import com.trackmeet.modules.event.vo.EventDetailVO;
import com.trackmeet.modules.event.vo.EventVO;

/**
 * 项目服务：项目 CRUD、详情聚合与批量生成标准项目。
 *
 * @author TrackMeet
 */
public interface EventService extends IService<Event> {

    /**
     * 项目分页查询：支持 groupId、eventType、gender、subType、status、keyword（名称 / 编码）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EventVO> pageEvent(EventQuery query);

    /**
     * 项目详情聚合：项目 + 轮次列表（含轮次级规则）+ 项目级规则（含来源标记）+ 届次默认规则。
     *
     * @param id 项目 ID
     * @return 详情视图
     */
    EventDetailVO getDetail(Long id);

    /**
     * 新增项目：校验 {@code event_code} 届次内唯一；默认 {@code status=ENABLED}、{@code round_count=1}。
     *
     * @param dto 入参
     * @return 新增主键
     */
    Long createEvent(EventSaveDTO dto);

    /**
     * 修改项目：校验存在性与 {@code event_code} 届次内唯一（排除自身），不允许跨届次修改。
     *
     * @param id  项目 ID
     * @param dto 入参
     */
    void updateEvent(Long id, EventSaveDTO dto);

    /**
     * 删除项目：项目下存在未取消报名或被日程引用时抛 {@code CONFLICT}；
     * 通过校验后级联物理删除项目级规则与轮次级规则。
     *
     * @param id 项目 ID
     */
    void deleteEvent(Long id);

    /**
     * 批量生成标准项目：组别列表 × 性别 × 模板列表做笛卡尔积；
     * 已存在「同名 + 同组别 + 同性别」的项目跳过并记入 {@code skippedNames}。
     *
     * @param dto 入参
     * @return 创建 / 跳过统计结果
     */
    EventBatchGenerateResultVO batchGenerate(EventBatchGenerateDTO dto);

    /**
     * 项目实体存在性校验（不存在抛 404）。
     *
     * @param id 项目 ID
     * @return 项目实体
     */
    Event getEntity(Long id);

    /**
     * 按项目 ID 查询其所属届次 ID（项目不存在抛 404）。
     *
     * @param id 项目 ID
     * @return 届次 ID
     */
    Long getMeetIdByEventId(Long id);
}
