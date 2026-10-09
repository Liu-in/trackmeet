package com.trackmeet.modules.athlete.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 运动员导入结果。
 *
 * @author TrackMeet
 */
@Data
public class AthleteImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否为校验模式（true 表示只校验不落库） */
    private Boolean dryRun = Boolean.FALSE;

    /** 解析到的数据行数 */
    private Integer totalCount = 0;

    /** 成功导入行数（dryRun 时为校验通过行数） */
    private Integer successCount = 0;

    /** 失败行数 */
    private Integer failCount = 0;

    /** 自动生成号码的行数 */
    private Integer autoBibCount = 0;

    /** 导入任务 ID（sys_import_task.id，dryRun 时为空） */
    private Long taskId;

    /** 结果说明 */
    private String message;

    /** 错误明细（最多返回前 200 条，完整明细见 errorFileBase64） */
    private List<AthleteImportErrorVO> errors = new ArrayList<>();

    /** 错误报告 Excel（Base64，仅在有错误时返回；前端可解码下载） */
    private String errorFileBase64;

    /** 错误报告文件名 */
    private String errorFileName;
}
