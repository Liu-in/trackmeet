package com.trackmeet.modules.athlete.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.athlete.dto.AthleteImportResultVO;
import com.trackmeet.modules.athlete.service.AthleteImportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 运动员 Excel 导入接口。
 * 权限编码：athlete:import。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/athletes")
@RequiredArgsConstructor
@Validated
public class AthleteImportController {

    private final AthleteImportService athleteImportService;

    /**
     * 下载运动员导入模板（含填写说明 Sheet 与示例行）。
     */
    @GetMapping("/import-template")
    @PreAuthorize("@perm.has('athlete:import')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = URLEncoder.encode("运动员导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);
        athleteImportService.writeTemplate(response.getOutputStream());
        response.flushBuffer();
    }

    /**
     * 导入运动员。
     *
     * @param file   Excel 文件（模板见 /api/athletes/import-template）
     * @param meetId 届次 ID（也可通过请求头 X-Meet-Id 或参数 meetId 传递）
     * @param dryRun true 仅校验不落库（用于「先校验后导入」）
     */
    @PostMapping("/import")
    @PreAuthorize("@perm.has('athlete:import')")
    @OpLog(module = "ATHLETE", action = "IMPORT", actionName = "运动员Excel导入", targetType = "athlete")
    public R<AthleteImportResultVO> importAthletes(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "meetId", required = false) Long meetId,
                                                   @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return R.ok(athleteImportService.importAthletes(file, meetId, dryRun));
    }
}
