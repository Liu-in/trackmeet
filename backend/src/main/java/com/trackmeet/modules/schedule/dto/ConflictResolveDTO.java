package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 冲突处理入参：标记已处理并附处理说明。
 *
 * @author TrackMeet
 */
@Data
public class ConflictResolveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处理说明，如「已调整至下一单元」 */
    @Size(max = 100, message = "处理说明长度不能超过 100")
    private String resolveRemark;
}
