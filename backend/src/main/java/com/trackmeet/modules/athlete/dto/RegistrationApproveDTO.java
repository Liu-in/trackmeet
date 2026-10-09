package com.trackmeet.modules.athlete.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量审核通过请求。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationApproveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待审核通过的报名 ID 列表 */
    @NotEmpty(message = "报名 ID 列表不能为空")
    private List<Long> ids;
}
