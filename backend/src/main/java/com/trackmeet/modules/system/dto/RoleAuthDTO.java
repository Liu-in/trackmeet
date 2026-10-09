package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色授权入参：{@code {"permissionIds":[1,2]}}。
 *
 * @author TrackMeet
 */
@Data
public class RoleAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 权限 ID 集合，空数组表示回收该角色全部权限 */
    @NotNull(message = "权限 ID 列表不能为空")
    private List<Long> permissionIds = new ArrayList<>();
}
