package com.trackmeet.modules.athlete.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 报名限制校验结果：通过/未通过条数与问题清单。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationCheckResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 无 ERROR 项的报名条数 */
    private Integer passCount = 0;

    /** 存在 ERROR 项的报名条数（含未校验的记录） */
    private Integer failCount = 0;

    /** 问题清单 */
    private List<RegistrationCheckItemVO> items = new ArrayList<>();
}
