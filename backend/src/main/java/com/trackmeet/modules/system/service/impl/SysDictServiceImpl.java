package com.trackmeet.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.system.dto.DictItemSaveDTO;
import com.trackmeet.modules.system.dto.DictTypeQuery;
import com.trackmeet.modules.system.dto.DictTypeSaveDTO;
import com.trackmeet.modules.system.entity.SysDictItem;
import com.trackmeet.modules.system.entity.SysDictType;
import com.trackmeet.modules.system.mapper.SysDictItemMapper;
import com.trackmeet.modules.system.mapper.SysDictTypeMapper;
import com.trackmeet.modules.system.service.SysDictService;
import com.trackmeet.modules.system.vo.DictItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典管理服务实现：字典类型与字典项同域维护。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class SysDictServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements SysDictService {

    private static final String STATUS_ENABLED = "ENABLED";

    /** dict_type / item_value 列长度，删除时改写唯一键需截断 */
    private static final int DICT_CODE_MAX_LENGTH = 64;

    private final SysDictItemMapper dictItemMapper;

    @Override
    public PageResult<SysDictType> pageTypes(DictTypeQuery query) {
        DictTypeQuery condition = query == null ? new DictTypeQuery() : query;
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        String keyword = condition.getKeyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysDictType::getDictType, keyword)
                    .or().like(SysDictType::getDictName, keyword));
        }
        wrapper.eq(StringUtils.hasText(condition.getDictGroup()), SysDictType::getDictGroup, condition.getDictGroup())
                .eq(StringUtils.hasText(condition.getStatus()), SysDictType::getStatus, condition.getStatus())
                .orderByAsc(SysDictType::getSortNo)
                .orderByAsc(SysDictType::getId);

        Page<SysDictType> pageParam = condition.toPage();
        return PageResult.of(page(pageParam, wrapper));
    }

    @Override
    public SysDictType getType(Long id) {
        SysDictType dictType = getById(id);
        if (dictType == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        return dictType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createType(DictTypeSaveDTO dto) {
        String dictTypeCode = dto.getDictType().trim();
        if (existsDictType(dictTypeCode, null)) {
            throw new BizException(ResultCode.CONFLICT, "字典类型编码已存在");
        }
        SysDictType dictType = new SysDictType();
        dictType.setDictType(dictTypeCode);
        dictType.setDictName(dto.getDictName());
        dictType.setDictGroup(dto.getDictGroup());
        dictType.setIsSystem(dto.getIsSystem() == null ? 0 : dto.getIsSystem());
        dictType.setIsGlobal(dto.getIsGlobal() == null ? 1 : dto.getIsGlobal());
        dictType.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_ENABLED);
        dictType.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        dictType.setExtraJson(dto.getExtraJson());
        dictType.setRemark(dto.getRemark());
        save(dictType);
        return dictType.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long id, DictTypeSaveDTO dto) {
        SysDictType dictType = getById(id);
        if (dictType == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        String dictTypeCode = dto.getDictType().trim();
        if (!dictTypeCode.equals(dictType.getDictType()) && existsDictType(dictTypeCode, id)) {
            throw new BizException(ResultCode.CONFLICT, "字典类型编码已存在");
        }
        dictType.setDictType(dictTypeCode);
        dictType.setDictName(dto.getDictName());
        dictType.setDictGroup(dto.getDictGroup());
        if (dto.getIsSystem() != null) {
            dictType.setIsSystem(dto.getIsSystem());
        }
        if (dto.getIsGlobal() != null) {
            dictType.setIsGlobal(dto.getIsGlobal());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            dictType.setStatus(dto.getStatus());
        }
        if (dto.getSortNo() != null) {
            dictType.setSortNo(dto.getSortNo());
        }
        dictType.setExtraJson(dto.getExtraJson());
        dictType.setRemark(dto.getRemark());
        updateById(dictType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        SysDictType dictType = getById(id);
        if (dictType == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        if (dictType.getIsSystem() != null && dictType.getIsSystem() == 1) {
            throw new BizException(ResultCode.FORBIDDEN, "系统内置字典禁止删除");
        }
        List<SysDictItem> items = dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, dictType.getDictType()));
        for (SysDictItem item : items) {
            dictItemMapper.update(null, new LambdaUpdateWrapper<SysDictItem>()
                    .eq(SysDictItem::getId, item.getId())
                    .set(SysDictItem::getItemValue,
                            releaseUniqueKey(item.getItemValue(), item.getId(), DICT_CODE_MAX_LENGTH)));
            dictItemMapper.deleteById(item.getId());
        }
        baseMapper.update(null, new LambdaUpdateWrapper<SysDictType>()
                .eq(SysDictType::getId, id)
                .set(SysDictType::getDictType,
                        releaseUniqueKey(dictType.getDictType(), id, DICT_CODE_MAX_LENGTH)));
        removeById(id);
    }

    @Override
    public List<DictItemVO> listItemsByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型编码不能为空");
        }
        List<SysDictItem> items = dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, dictType)
                .eq(SysDictItem::getStatus, STATUS_ENABLED)
                .orderByAsc(SysDictItem::getSortNo)
                .orderByAsc(SysDictItem::getId));
        List<DictItemVO> voList = new ArrayList<>(items.size());
        for (SysDictItem item : items) {
            DictItemVO vo = new DictItemVO();
            BeanUtils.copyProperties(item, vo);
            voList.add(vo);
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createItem(DictItemSaveDTO dto) {
        String dictTypeCode = dto.getDictType().trim();
        validateDictType(dictTypeCode);
        if (existsItemValue(dictTypeCode, dto.getItemValue(), null)) {
            throw new BizException(ResultCode.CONFLICT, "同一字典类型下存储码已存在");
        }
        SysDictItem item = new SysDictItem();
        item.setDictType(dictTypeCode);
        item.setItemLabel(dto.getItemLabel());
        item.setItemValue(dto.getItemValue());
        item.setParentValue(dto.getParentValue());
        item.setCssClass(dto.getCssClass());
        item.setIsDefault(dto.getIsDefault() == null ? 0 : dto.getIsDefault());
        item.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        item.setExtraJson(dto.getExtraJson());
        item.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_ENABLED);
        item.setRemark(dto.getRemark());
        dictItemMapper.insert(item);
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long id, DictItemSaveDTO dto) {
        SysDictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        String dictTypeCode = dto.getDictType().trim();
        validateDictType(dictTypeCode);
        if (existsItemValue(dictTypeCode, dto.getItemValue(), id)) {
            throw new BizException(ResultCode.CONFLICT, "同一字典类型下存储码已存在");
        }
        item.setDictType(dictTypeCode);
        item.setItemLabel(dto.getItemLabel());
        item.setItemValue(dto.getItemValue());
        item.setParentValue(dto.getParentValue());
        item.setCssClass(dto.getCssClass());
        if (dto.getIsDefault() != null) {
            item.setIsDefault(dto.getIsDefault());
        }
        if (dto.getSortNo() != null) {
            item.setSortNo(dto.getSortNo());
        }
        item.setExtraJson(dto.getExtraJson());
        if (StringUtils.hasText(dto.getStatus())) {
            item.setStatus(dto.getStatus());
        }
        item.setRemark(dto.getRemark());
        dictItemMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        SysDictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        dictItemMapper.update(null, new LambdaUpdateWrapper<SysDictItem>()
                .eq(SysDictItem::getId, id)
                .set(SysDictItem::getItemValue,
                        releaseUniqueKey(item.getItemValue(), id, DICT_CODE_MAX_LENGTH)));
        dictItemMapper.deleteById(id);
    }

    /**
     * 逻辑删除时释放业务唯一键（docs/12-01 第 4 节）：追加 {@code _d{id}} 后缀。
     */
    private static String releaseUniqueKey(String original, Long id, int maxLength) {
        String suffix = "_d" + id;
        String base = original == null ? "" : original;
        int keep = Math.max(0, maxLength - suffix.length());
        if (base.length() > keep) {
            base = base.substring(0, keep);
        }
        return base + suffix;
    }

    private void validateDictType(String dictTypeCode) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictTypeCode));
        if (count == null || count == 0L) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型不存在：" + dictTypeCode);
        }
    }

    private boolean existsDictType(String dictTypeCode, Long excludeId) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictTypeCode)
                .ne(excludeId != null, SysDictType::getId, excludeId);
        return count(wrapper) > 0;
    }

    private boolean existsItemValue(String dictTypeCode, String itemValue, Long excludeId) {
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, dictTypeCode)
                .eq(SysDictItem::getItemValue, itemValue)
                .ne(excludeId != null, SysDictItem::getId, excludeId);
        Long total = dictItemMapper.selectCount(wrapper);
        return total != null && total > 0;
    }
}
