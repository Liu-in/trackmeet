package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 分组/田赛顺序的种子装配行（跨模块取数：registration × athlete × unit）。
 *
 * @author TrackMeet
 */
@Data
public class AthleteEntryRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long registrationId;
    private Long athleteId;
    private String athleteName;
    private Long unitId;
    private String unitName;
    private String bibNo;
    private BigDecimal seedValue;
    private String seedResultRaw;
    private String seedResultUnit;
    private String seedSource;
    private Integer isRelay;
    private Long groupId;
    private String relayName;
}
