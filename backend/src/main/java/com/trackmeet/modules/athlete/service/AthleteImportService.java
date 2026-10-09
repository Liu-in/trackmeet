package com.trackmeet.modules.athlete.service;

import com.trackmeet.modules.athlete.dto.AthleteImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * 运动员 Excel 导入服务。
 *
 * @author TrackMeet
 */
public interface AthleteImportService {

    /**
     * 写出导入模板（含「运动员导入模板」与「填写说明」两个 Sheet）。
     *
     * @param outputStream 输出流（由 Controller 写入 HttpServletResponse）
     */
    void writeTemplate(OutputStream outputStream);

    /**
     * 导入运动员数据。
     *
     * @param file    Excel 文件
     * @param meetId  届次 ID（为空时取请求上下文中的届次）
     * @param dryRun  true 仅校验不落库
     * @return 导入结果（含成功/失败计数、错误明细、错误报告 Base64）
     */
    AthleteImportResultVO importAthletes(MultipartFile file, Long meetId, boolean dryRun);
}
