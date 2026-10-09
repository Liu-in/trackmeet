package com.trackmeet.modules.athlete.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.athlete.dto.AthleteImportRow;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 运动员导入监听器：逐行收集数据，跳过空行，限制最大行数。
 * 不做业务校验（校验由 Service 统一完成，便于一次性返回全部错误）。
 *
 * @author TrackMeet
 */
public class AthleteImportListener extends AnalysisEventListener<AthleteImportRow> {

    /** 单次导入最大行数 */
    private static final int MAX_ROWS = 5000;

    @Getter
    private final List<AthleteImportRow> rows = new ArrayList<>();

    /** 与 rows 一一对应的 Excel 行号（1 基，含表头） */
    @Getter
    private final List<Integer> rowNumbers = new ArrayList<>();

    @Override
    public void invoke(AthleteImportRow data, AnalysisContext context) {
        if (data == null || isEmptyRow(data)) {
            return;
        }
        if (rows.size() >= MAX_ROWS) {
            throw new BizException(ResultCode.IMPORT_FORMAT_ERROR, "单次导入不能超过 " + MAX_ROWS + " 行，请拆分文件");
        }
        rows.add(data);
        rowNumbers.add(context.readRowHolder().getRowIndex() + 1);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 数据已在 invoke 中收集，无需额外处理
    }

    private boolean isEmptyRow(AthleteImportRow row) {
        return !StringUtils.hasText(row.getAthleteName())
                && !StringUtils.hasText(row.getIdCard())
                && !StringUtils.hasText(row.getUnitCode())
                && !StringUtils.hasText(row.getGroupCode())
                && !StringUtils.hasText(row.getGender())
                && !StringUtils.hasText(row.getBirthDate());
    }
}
