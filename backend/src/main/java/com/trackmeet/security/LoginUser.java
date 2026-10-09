package com.trackmeet.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录用户主体：承载用户基础信息、角色编码与权限编码。
 *
 * @author TrackMeet
 */
@Getter
@Setter
public class LoginUser implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 密文 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 账号状态：ENABLED / DISABLED */
    private String status;

    /** 部门 ID */
    private Long deptId;

    /** 关联参赛单位 ID */
    private Long unitId;

    /** 令牌版本 */
    private Integer tokenVersion;

    /** 1 需要强制改密 */
    private Integer forcePwdChange;

    /** 角色编码集合 */
    private List<String> roles = new ArrayList<>();

    /** 权限编码集合 */
    private Set<String> permissions = new LinkedHashSet<>();

    /** 登录时间 */
    private LocalDateTime loginTime;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        if (permissions != null) {
            for (String permission : permissions) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ENABLED".equalsIgnoreCase(status);
    }

    /**
     * 是否超级管理员。
     */
    public boolean isSuperAdmin() {
        return roles != null && roles.contains("SUPER_ADMIN");
    }
}
