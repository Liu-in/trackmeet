package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 风速录入入参（组级或单人）。
 * <p>
 * 二选一定位目标：传 {@link #resultIds} 时按成绩逐条写入；只传 {@link #heatId} 时按分组
 * 展开为组内全部成绩。写入 {@code wind_reading} 留档并回填
 * {@code result.wind_speed} 与 {@code result.is_wind_legal}（上限取 {@code event_rule_config.wind_json}
 * 的 {@code maxLegal}，缺省 2.0 m/s）。
 *
 * @author TrackMeet
 */
@Data
public class ResultWindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID 列表（与 heatId 二选一） */
    private List<Long> resultIds;

    /** 分组 ID（与 resultIds 二选一；按组批量录入同一次读数） */
    private Long heatId;

    /** 风速读数（m/s），顺风为正、逆风为负 */
    @DecimalMin(value = "-99.99", message = "风速超出取值范围")
    @DecimalMax(value = "99.99", message = "风速超出取值范围")
    private BigDecimal windSpeed;

    /** 风向：HEAD 逆风 / TAIL 顺风 / CROSS 侧风 */
    @Size(max = 16, message = "风向长度不能超过 16")
    private String windDirection;

    /** 风速仪设备编号 */
    @Size(max = 32, message = "设备编号长度不能超过 32")
    private String windDeviceNo;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
