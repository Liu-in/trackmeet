package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户分配角色入参：{@code {"roleIds":[1,2]}}。
 *
 * @author TrackMeet
 */
@Data
public class UserRoleAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色 ID 集合，空数组表示清空该用户全部角色 */
    @NotNull(message = "角色 ID 列表不能为空")
    private List<Long> roleIds = new ArrayList<>();
}
