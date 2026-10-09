package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 编排快照保存入参。
 *
 * @author TrackMeet
 */
@Data
public class SnapshotSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（缺省取当前届次上下文） */
    private Long meetId;

    /** 快照名称，如「自动编排 v3」 */
    @NotBlank(message = "快照名称不能为空")
    @Size(max = 100, message = "快照名称长度不能超过 100")
    private String snapshotName;

    /** 快照类型：AUTO / MANUAL / RESTORE，缺省 MANUAL */
    @Size(max = 32, message = "快照类型长度不能超过 32")
    private String snapshotType;

    /** 对应编排批次号（缺省取当前届次最近的编排批次） */
    @Size(max = 64, message = "批次号长度不能超过 64")
    private String batchNo;

    /** 快照范围：ALL / SESSION / EVENT，缺省 ALL */
    @Size(max = 32, message = "快照范围长度不能超过 32")
    private String scopeType;

    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
