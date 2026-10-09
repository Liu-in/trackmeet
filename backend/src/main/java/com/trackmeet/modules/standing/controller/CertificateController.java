package com.trackmeet.modules.standing.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.standing.dto.CertificateGenerateDTO;
import com.trackmeet.modules.standing.dto.StandingCertificateRow;
import com.trackmeet.modules.standing.dto.StandingMeetRow;
import com.trackmeet.modules.standing.mapper.StandingMapper;
import com.trackmeet.modules.standing.service.CertificateService;
import com.trackmeet.modules.standing.vo.CertificateVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 证书接口：批量生成、列表查询、导出下载与打印计数。
 * <p>
 * 权限编码（均已在 {@code sql/05-init-data.sql} 初始化）：
 * <ul>
 *     <li>{@code POST /api/certificates/generate} → {@code certificate:generate}；</li>
 *     <li>{@code GET /api/certificates} → {@code certificate:list}；</li>
 *     <li>{@code GET /api/certificates/{id}/download} → {@code certificate:list}；</li>
 *     <li>{@code POST /api/certificates/print} → {@code certificate:print}。</li>
 * </ul>
 * 下载采用 Excel（{@code CertificateExcel}，一行一证书）：与项目「服务端 PDF 需内嵌中文字体、暂缓」
 * 的既定决策一致。
 *
 * @author TrackMeet
 */
@Slf4j
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@Validated
public class CertificateController {

    /** Excel 响应类型 */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final CertificateService certificateService;
    private final StandingMapper standingMapper;

    /**
     * 批量生成证书。
     *
     * @param dto 生成入参（meetId 必填；certType=RANK|RESULT|MEDAL|PARTICIPATION；eventId/groupId/unitId/templateId 可空）
     * @return 实际生成条数
     */
    @PostMapping("/generate")
    @PreAuthorize("@perm.has('certificate:generate')")
    @OpLog(module = "STANDING", action = "GENERATE", actionName = "批量生成证书", targetType = "certificate")
    public R<Integer> generate(@Valid @RequestBody CertificateGenerateDTO dto) {
        return R.ok(certificateService.generate(dto));
    }

    /**
     * 证书分页列表。
     *
     * @param meetId   届次 ID（必填）
     * @param certType 证书类型（可空）
     * @param unitId   单位 ID（可空）
     * @param keyword  关键字（证书编号 / 荣誉描述 / 运动员姓名 / 单位名称）
     * @param page     页码（从 1 开始，默认 1）
     * @param size     每页条数（默认 20，上限 200）
     * @return 分页结果
     */
    @GetMapping("")
    @PreAuthorize("@perm.has('certificate:list')")
    public R<PageResult<CertificateVO>> list(@RequestParam("meetId") @NotNull Long meetId,
                                            @RequestParam(value = "certType", required = false) String certType,
                                            @RequestParam(value = "unitId", required = false) Long unitId,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "page", defaultValue = "1") long page,
                                            @RequestParam(value = "size", defaultValue = "20") long size) {
        return R.ok(certificateService.pageCertificates(meetId, certType, unitId, keyword, page, size));
    }

    /**
     * 下载单张证书的 Excel 文件流（单张证书一个 Sheet，含编号/姓名/单位/项目/名次/成绩/日期）。
     * <p>
     * 必须先整体生成到内存再设置响应头，避免生成失败时向客户端输出半截损坏文件。
     *
     * @param id       证书 ID
     * @param meetId   届次 ID（必填）
     * @param response HTTP 响应
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("@perm.has('certificate:list')")
    public void download(@PathVariable("id") Long id,
                         @RequestParam("meetId") @NotNull Long meetId,
                         HttpServletResponse response) {
        List<StandingCertificateRow> rows = certificateService.listByIds(meetId, Collections.singletonList(id));
        if (rows.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "证书不存在");
        }
        StandingMeetRow meet = standingMapper.selectMeetInfo(meetId);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        certificateService.writeExcel(rows, buffer);
        byte[] content = buffer.toByteArray();
        String fileName = certificateService.buildFileName(meet == null ? null : meet.getMeetName());
        writeXlsx(content, fileName, response);
    }

    /**
     * 批量打印：累加 {@code print_count} 并刷新最近打印时间。
     *
     * @param dto 打印入参（meetId 必填；ids 非空）
     * @return 受影响条数
     */
    @PostMapping("/print")
    @PreAuthorize("@perm.has('certificate:print')")
    @OpLog(module = "STANDING", action = "PRINT", actionName = "批量打印证书", targetType = "certificate")
    public R<Integer> print(@Valid @RequestBody CertificatePrintDTO dto) {
        return R.ok(certificateService.markPrinted(dto.getMeetId(), dto.getIds()));
    }

    /**
     * 写出 Excel 文件流（RFC 5987 编码文件名）。
     */
    private void writeXlsx(byte[] content, String fileName, HttpServletResponse response) {
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(content.length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=UTF-8''" + encodedName);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(content);
            outputStream.flush();
        } catch (IOException ex) {
            log.error("输出证书 Excel 失败：{}", fileName, ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "输出证书 Excel 失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 批量打印入参。
     *
     * @author TrackMeet
     */
    @Data
    public static class CertificatePrintDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 届次 ID（必填） */
        @NotNull(message = "届次不能为空")
        private Long meetId;

        /** 证书 ID 列表（非空） */
        private List<Long> ids;
    }
}
