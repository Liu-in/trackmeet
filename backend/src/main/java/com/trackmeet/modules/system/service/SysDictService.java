package com.trackmeet.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.system.dto.DictItemSaveDTO;
import com.trackmeet.modules.system.dto.DictTypeQuery;
import com.trackmeet.modules.system.dto.DictTypeSaveDTO;
import com.trackmeet.modules.system.entity.SysDictType;
import com.trackmeet.modules.system.vo.DictItemVO;

import java.util.List;

/**
 * 字典管理服务：字典类型以 {@code SysDictType} 为聚合根，字典项通过 Mapper 操作。
 *
 * @author TrackMeet
 */
public interface SysDictService extends IService<SysDictType> {

    /**
     * 字典类型分页列表（条件：keyword、dictGroup、status）。
     */
    PageResult<SysDictType> pageTypes(DictTypeQuery query);

    /**
     * 字典类型详情。
     */
    SysDictType getType(Long id);

    /**
     * 新增字典类型，返回新类型 ID。
     */
    Long createType(DictTypeSaveDTO dto);

    /**
     * 修改字典类型。
     */
    void updateType(Long id, DictTypeSaveDTO dto);

    /**
     * 删除字典类型（系统内置禁止删除），同时逻辑删除其下全部字典项。
     */
    void deleteType(Long id);

    /**
     * 按字典类型编码列出启用字典项，按 sort_no 升序（登录即可访问）。
     */
    List<DictItemVO> listItemsByType(String dictType);

    /**
     * 新增字典项，返回新字典项 ID。
     */
    Long createItem(DictItemSaveDTO dto);

    /**
     * 修改字典项。
     */
    void updateItem(Long id, DictItemSaveDTO dto);

    /**
     * 删除字典项（逻辑删除）。
     */
    void deleteItem(Long id);
}
