package com.trackmeet.modules.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户信息（含角色、权限、菜单）。
 *
 * @author TrackMeet
 */
@Data
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Long unitId;
    private String status;
    private Integer forcePwdChange;
    private List<String> roles = new ArrayList<>();
    private Set<String> permissions = new LinkedHashSet<>();
    private List<MenuNodeVO> menus = new ArrayList<>();
}
