package com.trackmeet.modules.athlete.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.athlete.dto.AthleteQuery;
import com.trackmeet.modules.athlete.entity.Athlete;

/**
 * 运动员服务：CRUD 与分页查询。
 *
 * @author TrackMeet
 */
public interface AthleteService extends IService<Athlete> {

    /**
     * 运动员分页列表：支持姓名/证件号/号码模糊、单位、组别、性别、资格状态过滤，按创建时间倒序。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Athlete> pageAthlete(AthleteQuery query);

    /**
     * 运动员详情。
     *
     * @param id 运动员 ID
     * @return 运动员
     */
    Athlete getAthleteDetail(Long id);

    /**
     * 新增运动员：校验届次、必填项与届次内唯一性（身份证号、号码）。
     *
     * @param athlete 运动员
     * @return 新增主键
     */
    Long createAthlete(Athlete athlete);

    /**
     * 修改运动员：校验存在性、届次一致性与届次内唯一性（排除自身）。
     *
     * @param id      运动员 ID
     * @param athlete 运动员
     */
    void updateAthlete(Long id, Athlete athlete);

    /**
     * 删除运动员：先改写号码唯一键（原值_d{id}）再逻辑删除，保证号码可在同届次复用。
     *
     * @param id 运动员 ID
     */
    void removeAthlete(Long id);
}
