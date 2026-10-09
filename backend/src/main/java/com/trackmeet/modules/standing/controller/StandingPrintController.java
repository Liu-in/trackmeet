package com.trackmeet.modules.standing.controller;

import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.R;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.print.vo.SheetBundleVO;
import com.trackmeet.modules.standing.dto.StandingMeetRow;
import com.trackmeet.modules.standing.dto.StandingQuery;
import com.trackmeet.modules.standing.service.impl.StandingServiceImpl;
import com.trackmeet.modules.standing.service.support.StandingPrintSupport;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 成绩册 / 积分表 / 奖牌榜 / 积分明细表的 A4 打印与 Excel 输出接口。
 * <p>
 * 复用 {@code modules/print/vo/SheetBundleVO} 契约与 {@code PrintController} 的双通道约定：
 * <ol>
 *     <li>{@code format=json}（默认）→ 返回 {@code SheetBundleVO} 列表，前端按 A4 渲染并
 *         {@code window.print()} / 另存 PDF；</li>
 *     <li>{@code format=xlsx} → 直接输出多 Sheet Excel 文件流
 *         （{@code Content-Disposition: attachment;filename*=UTF-8''...}），此时方法返回 {@code void}。</li>
 * </ol>
 * <b>路径与既有 {@code modules/print/controller/PrintController} 不重叠</b>：本类只新增
 * {@code /results}、{@code /standings/team}、{@code /standings/medals}、{@code /points} 四个 GET；
 * 既有类占用 {@code /order-book}、{@code /order-book/schedule}、{@code /heats}、{@code /lanes}、
 * {@code /calling-sheets}、{@code /order-book/export}。
 * <p>
 * 权限编码：成绩册 {@code print:results}；三张榜单与积分明细 {@code print:standings}。
 *
 * @author TrackMeet
 */
@Slf4j
@RestController
@RequestMapping("/api/print")
@RequiredArgsConstructor
@Validated
public class StandingPrintController {

    /** Excel 响应类型 */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StandingServiceImpl standingService;
    private final StandingPrintSupport printSupport;

    /**
     * 成绩册 / 成绩公告：按「项目 × 轮次」拆分多张表，
     * 列 = 名次 / 号码 / 姓名 / 单位 / 成绩 / 风速 / 备注（含纪录标记）。
     *
     * @param query    查询条件（meetId 必填；eventId / groupId / unitId；format=json|xlsx）
     * @param response HTTP 响应（Excel 输出时写文件流）
     * @return 表结构列表（{@code format=xlsx} 时返回 {@code null}，响应体已写出）
     */
    @GetMapping("/results")
    @PreAuthorize("@perm.has('print:results')")
    public R<List<SheetBundleVO>> results(@Valid StandingQuery query, HttpServletResponse response) {
        Long meetId = requireMeetId(query);
        StandingMeetRow meet = standingService.meetInfo(meetId);
        Map<Long, String> groupNames = standingService.groupNames(meetId, query.getGroupId());
        List<SheetBundleVO> bundles = printSupport.buildResultSheets(
                standingService.listResultRows(meetId, query.getEventId(), query.getGroupId(), query.getUnitId()),
                standingService.recordRows(meetId), meet, groupNames);
        if (query.isXlsx()) {
            printSupport.requireNotEmpty(bundles);
            writeXlsx(bundles, fileName(meet, "成绩册"), response);
            return null;
        }
        return R.ok(bundles);
    }

    /**
     * 积分表（团体总分）：按团体口径分为男子团体 / 女子团体 / 总团体多张表。
     *
     * @param query    查询条件（meetId 必填；groupId；scoreType；format）
     * @param response HTTP 响应
     * @return 表结构列表（{@code format=xlsx} 时返回 {@code null}）
     */
    @GetMapping("/standings/team")
    @PreAuthorize("@perm.has('print:standings')")
    public R<List<SheetBundleVO>> standingsTeam(@Valid StandingQuery query, HttpServletResponse response) {
        Long meetId = requireMeetId(query);
        StandingMeetRow meet = standingService.meetInfo(meetId);
        String scoreType = query.getScoreType() == null || query.getScoreType().isBlank()
                ? null : query.normalizedScoreType();
        List<SheetBundleVO> bundles = printSupport.buildTeamSheets(
                standingService.listTeamScoreRows(meetId, query.getGroupId(), scoreType), meet);
        if (query.isXlsx()) {
            printSupport.requireNotEmpty(bundles);
            writeXlsx(bundles, fileName(meet, "积分表"), response);
            return null;
        }
        return R.ok(bundles);
    }

    /**
     * 奖牌榜：单张表。
     *
     * @param query    查询条件（meetId 必填；groupId；format）
     * @param response HTTP 响应
     * @return 表结构列表（{@code format=xlsx} 时返回 {@code null}）
     */
    @GetMapping("/standings/medals")
    @PreAuthorize("@perm.has('print:standings')")
    public R<List<SheetBundleVO>> standingsMedals(@Valid StandingQuery query, HttpServletResponse response) {
        Long meetId = requireMeetId(query);
        StandingMeetRow meet = standingService.meetInfo(meetId);
        List<SheetBundleVO> bundles = printSupport.buildMedalSheets(
                standingService.listMedalRows(meetId, query.getGroupId()), meet);
        if (query.isXlsx()) {
            printSupport.requireNotEmpty(bundles);
            writeXlsx(bundles, fileName(meet, "奖牌榜"), response);
            return null;
        }
        return R.ok(bundles);
    }

    /**
     * 积分明细表：单张表。
     *
     * @param query    查询条件（meetId 必填；groupId / unitId / eventId；format）
     * @param response HTTP 响应
     * @return 表结构列表（{@code format=xlsx} 时返回 {@code null}）
     */
    @GetMapping("/points")
    @PreAuthorize("@perm.has('print:standings')")
    public R<List<SheetBundleVO>> points(@Valid StandingQuery query, HttpServletResponse response) {
        Long meetId = requireMeetId(query);
        StandingMeetRow meet = standingService.meetInfo(meetId);
        List<SheetBundleVO> bundles = printSupport.buildPointSheets(
                standingService.listPointRows(meetId, query.getGroupId(), query.getEventId(), query.getUnitId()),
                meet);
        if (query.isXlsx()) {
            printSupport.requireNotEmpty(bundles);
            writeXlsx(bundles, fileName(meet, "积分明细表"), response);
            return null;
        }
        return R.ok(bundles);
    }

    /**
     * 写出多 Sheet Excel 文件流：先在内存生成完整工作簿再设置响应头，
     * 即使生成失败也不会向客户端输出半截损坏的 xlsx。
     */
    private void writeXlsx(List<SheetBundleVO> bundles, String fileName, HttpServletResponse response) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        printSupport.writeExcel(bundles, buffer);
        byte[] content = buffer.toByteArray();
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(content.length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=UTF-8''" + encodedName);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(content);
            outputStream.flush();
        } catch (IOException ex) {
            log.error("输出榜单 Excel 失败：{}", fileName, ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "输出榜单 Excel 失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 导出文件名：届次名称 + 报表名。
     */
    private String fileName(StandingMeetRow meet, String report) {
        String base = meet == null || meet.getMeetName() == null || meet.getMeetName().isBlank()
                ? "运动会" : meet.getMeetName().trim();
        return base + "-" + report + ".xlsx";
    }

    private Long requireMeetId(StandingQuery query) {
        if (query == null || query.getMeetId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        return query.getMeetId();
    }
}
