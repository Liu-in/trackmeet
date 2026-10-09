package com.trackmeet.modules.print.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.print.dto.PrintTemplateQuery;
import com.trackmeet.modules.print.dto.PrintTemplateSaveDTO;
import com.trackmeet.modules.print.entity.PrintTemplate;
import com.trackmeet.modules.print.mapper.PrintTemplateMapper;
import com.trackmeet.modules.print.service.PrintTemplateService;
import com.trackmeet.modules.print.vo.PrintTemplateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 打印模板服务实现。
 * <p>
 * 关键规则：
 * <ol>
 *     <li><b>多租户</b>：入参 {@code meetId} 与 {@code MeetContext.get()} 同时存在且不一致时抛 {@code FORBIDDEN}；
 *         查询条件显式带 {@code meet_id}（与多租户拦截器叠加不冲突）；</li>
 *     <li><b>唯一键</b>：{@code uk_meet_template_code (meet_id, template_code)} 不含 {@code deleted}，
 *         因此保存前用 {@code selectByTemplateCode} 预校验，删除时先把编码改写为「原值_d{id}」再 {@code removeById}；</li>
 *     <li><b>默认模板唯一</b>：{@code is_default = 1} 时先 {@code clearDefaultFlag} 把同 {@code report_type}
 *         的旧默认置 0，再保存，保证「同一报表类型至多一个默认模板」。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Slf4j
@Service
public class PrintTemplateServiceImpl extends ServiceImpl<PrintTemplateMapper, PrintTemplate>
        implements PrintTemplateService {

    /** 逻辑删除时释放唯一键的后缀前缀 */
    private static final String DELETED_SUFFIX = "_d";

    /** 默认 1（显示）/ 0（不显示）标记的默认值 */
    private static final int FLAG_ON = 1;
    private static final int FLAG_OFF = 0;

    @Override
    public PageResult<PrintTemplateVO> pageTemplates(PrintTemplateQuery query) {
        PrintTemplateQuery condition = query == null ? new PrintTemplateQuery() : query;
        Long meetId = resolveMeetId(condition.getMeetId(), true);
        String keyword = StringUtils.hasText(condition.getKeyword()) ? condition.getKeyword().trim() : null;

        LambdaQueryWrapper<PrintTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrintTemplate::getMeetId, meetId)
                .eq(StringUtils.hasText(condition.getReportType()), PrintTemplate::getReportType,
                        normalizeReportType(condition.getReportType()))
                .eq(StringUtils.hasText(condition.getStatus()), PrintTemplate::getStatus,
                        condition.getStatus().trim().toUpperCase())
                .eq(condition.getIsDefault() != null, PrintTemplate::getIsDefault, condition.getIsDefault());
        if (keyword != null) {
            wrapper.and(w -> w.like(PrintTemplate::getTemplateCode, keyword)
                    .or().like(PrintTemplate::getTemplateName, keyword));
        }
        wrapper.orderByDesc(PrintTemplate::getIsDefault).orderByDesc(PrintTemplate::getId);

        Page<PrintTemplate> page = page(condition.toPage(), wrapper);
        List<PrintTemplateVO> records = new ArrayList<>(page.getRecords().size());
        for (PrintTemplate entity : page.getRecords()) {
            records.add(toVO(entity));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PrintTemplateVO getTemplate(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "模板 ID 不能为空");
        }
        PrintTemplate entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "打印模板不存在");
        }
        checkMeetContext(entity.getMeetId());
        return toVO(entity);
    }

    @Override
    public PrintTemplateVO getDefaultTemplate(Long meetId, String reportType) {
        Long resolvedMeetId = resolveMeetId(meetId, true);
        if (!StringUtils.hasText(reportType)) {
            throw new BizException(ResultCode.PARAM_ERROR, "报表类型不能为空");
        }
        PrintTemplate entity = baseMapper.selectDefaultTemplate(resolvedMeetId, normalizeReportType(reportType));
        return entity == null ? null : toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PrintTemplateSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "模板参数不能为空");
        }
        Long meetId = resolveMeetId(dto.getMeetId(), true);
        String templateCode = dto.getTemplateCode().trim();
        if (baseMapper.selectByTemplateCode(meetId, templateCode) != null) {
            throw new BizException(ResultCode.CONFLICT, "模板编码已存在：" + templateCode);
        }
        String reportType = normalizeReportType(dto.getReportType());
        PrintTemplate entity = new PrintTemplate();
        entity.setMeetId(meetId);
        applySave(entity, dto, templateCode, reportType);
        if (entity.defaultFlag()) {
            // 先清除旧默认，保证同一 report_type 至多一个 is_default = 1
            baseMapper.clearDefaultFlag(meetId, reportType);
        }
        save(entity);
        log.info("新增打印模板：meetId={}, code={}, reportType={}, isDefault={}",
                meetId, templateCode, reportType, entity.getIsDefault());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PrintTemplateSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "模板参数不能为空");
        }
        PrintTemplate entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "打印模板不存在");
        }
        checkMeetContext(entity.getMeetId());
        String templateCode = dto.getTemplateCode().trim();
        PrintTemplate exists = baseMapper.selectByTemplateCode(entity.getMeetId(), templateCode);
        if (exists != null && !exists.getId().equals(id)) {
            throw new BizException(ResultCode.CONFLICT, "模板编码已存在：" + templateCode);
        }
        String reportType = normalizeReportType(dto.getReportType());
        applySave(entity, dto, templateCode, reportType);
        entity.setId(id);
        if (entity.defaultFlag()) {
            baseMapper.clearDefaultFlag(entity.getMeetId(), reportType);
            entity.setIsDefault(FLAG_ON);
        }
        if (!updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "打印模板已被他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PrintTemplate entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "打印模板不存在");
        }
        checkMeetContext(entity.getMeetId());
        // 唯一键 uk_meet_template_code 不含 deleted：先改写编码释放原值，再做逻辑删除
        String released = releaseUniqueKey(entity.getTemplateCode(), id);
        baseMapper.rewriteTemplateCode(entity.getMeetId(), id, released);
        removeById(id);
        log.info("删除打印模板：meetId={}, id={}, 编码改写为 {}", entity.getMeetId(), id, released);
    }

    @Override
    public PrintTemplate resolveTemplate(Long meetId, Long templateId, String templateReportType) {
        if (templateId != null) {
            PrintTemplate entity = baseMapper.selectTemplateById(meetId, templateId);
            if (entity == null) {
                throw new BizException(ResultCode.NOT_FOUND, "打印模板不存在或不属于当前届次：" + templateId);
            }
            return entity;
        }
        if (!StringUtils.hasText(templateReportType) || meetId == null) {
            return null;
        }
        return baseMapper.selectDefaultTemplate(meetId, normalizeReportType(templateReportType));
    }

    @Override
    public PrintTemplateVO toVO(PrintTemplate entity) {
        PrintTemplateVO vo = new PrintTemplateVO();
        if (entity == null) {
            return vo;
        }
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    // ==================================================================================
    // 内部工具
    // ==================================================================================

    /**
     * 把入参写入实体（新增与修改共用；{@code meet_id} 保持不变）。
     */
    private void applySave(PrintTemplate entity, PrintTemplateSaveDTO dto, String templateCode, String reportType) {
        entity.setTemplateCode(templateCode);
        entity.setTemplateName(dto.getTemplateName().trim());
        entity.setReportType(reportType);
        entity.setHeaderHtml(dto.getHeaderHtml());
        entity.setFooterHtml(dto.getFooterHtml());
        entity.setLogoFileId(dto.getLogoFileId());
        entity.setSignatureHtml(dto.getSignatureHtml());
        entity.setPageSize(StringUtils.hasText(dto.getPageSize())
                ? dto.getPageSize().trim().toUpperCase() : PrintTemplate.PAGE_SIZE_A4);
        entity.setOrientation(StringUtils.hasText(dto.getOrientation())
                ? dto.getOrientation().trim().toUpperCase() : PrintTemplate.ORIENTATION_PORTRAIT);
        entity.setFontSize(dto.getFontSize() == null ? PrintTemplate.DEFAULT_FONT_SIZE : dto.getFontSize());
        entity.setShowPageNo(dto.getShowPageNo() == null ? FLAG_ON : (dto.getShowPageNo() == FLAG_ON ? FLAG_ON : FLAG_OFF));
        entity.setShowPrintTime(dto.getShowPrintTime() == null
                ? FLAG_ON : (dto.getShowPrintTime() == FLAG_ON ? FLAG_ON : FLAG_OFF));
        entity.setIsDefault(dto.getIsDefault() != null && dto.getIsDefault() == FLAG_ON ? FLAG_ON : FLAG_OFF);
        entity.setExtraJson(dto.getExtraJson());
        entity.setStatus(StringUtils.hasText(dto.getStatus())
                ? dto.getStatus().trim().toUpperCase() : PrintTemplate.STATUS_ENABLED);
        entity.setRemark(dto.getRemark());
    }

    /**
     * 报表类型归一化：去空白 + 大写（{@code print_template.report_type} 口径）。
     */
    private String normalizeReportType(String reportType) {
        return reportType == null ? null : reportType.trim().toUpperCase();
    }

    /**
     * 解析并校验届次：入参与 {@link MeetContext} 同时存在且不一致时抛 {@code FORBIDDEN}。
     *
     * @param requestMeetId 入参届次（可空）
     * @param required      是否必须解析到届次
     * @return 生效届次
     */
    private Long resolveMeetId(Long requestMeetId, boolean required) {
        Long contextMeetId = MeetContext.get();
        if (requestMeetId != null && contextMeetId != null && !requestMeetId.equals(contextMeetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "请求届次与当前届次上下文不一致，禁止跨届次操作打印模板");
        }
        Long meetId = requestMeetId != null ? requestMeetId : contextMeetId;
        if (meetId == null && required) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少届次参数 meetId（或请求头 X-Meet-Id）");
        }
        return meetId;
    }

    /**
     * 多租户校验：实体所属届次必须与 {@link MeetContext#get()} 一致。
     */
    private void checkMeetContext(Long meetId) {
        Long contextMeetId = MeetContext.get();
        if (contextMeetId != null && meetId != null && !contextMeetId.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该届次数据");
        }
    }

    /**
     * 生成改写后的唯一键：原值 + {@code _d}{id}，超长时截断原值以保证不超出列长度。
     */
    private String releaseUniqueKey(String original, Long id) {
        String suffix = DELETED_SUFFIX + id;
        String base = original == null ? "" : original;
        int keep = Math.max(0, PrintTemplate.TEMPLATE_CODE_MAX_LENGTH - suffix.length());
        if (base.length() > keep) {
            base = base.substring(0, keep);
        }
        return base + suffix;
    }
}
