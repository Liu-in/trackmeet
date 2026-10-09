package com.trackmeet.modules.meet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.meet.entity.Venue;

import java.util.List;

/**
 * 场地服务。
 *
 * @author TrackMeet
 */
public interface VenueService extends IService<Venue> {

    /**
     * 按届次查询场地（可按类型过滤）。
     */
    List<Venue> listByMeet(Long meetId, String venueType);

    /**
     * 新增场地（场地编码届次内唯一）。
     */
    Long createVenue(Venue venue);

    /**
     * 修改场地。
     */
    void updateVenue(Long id, Venue venue);

    /**
     * 删除场地（逻辑删除，删除前改写场地编码唯一键）。
     */
    void removeVenue(Long id);
}
