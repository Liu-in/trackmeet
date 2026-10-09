package com.trackmeet.security;

import com.trackmeet.modules.system.entity.SysUser;
import com.trackmeet.modules.system.mapper.SysPermissionMapper;
import com.trackmeet.modules.system.mapper.SysRoleMapper;
import com.trackmeet.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 用户认证信息加载：用户名/ID 两条入口，供登录与令牌鉴权使用。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return buildLoginUser(user);
    }

    /**
     * 按用户 ID 加载（JWT 鉴权链路使用）。
     */
    public LoginUser loadUserById(Long userId) throws UsernameNotFoundException {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + userId);
        }
        return buildLoginUser(user);
    }

    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPassword(user.getPassword());
        loginUser.setRealName(user.getRealName());
        loginUser.setStatus(user.getStatus());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setUnitId(user.getUnitId());
        loginUser.setTokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        loginUser.setForcePwdChange(user.getForcePwdChange() == null ? 0 : user.getForcePwdChange());

        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        loginUser.setRoles(roles == null ? new ArrayList<>() : roles);

        List<String> permissions = permissionMapper.selectPermCodesByUserId(user.getId());
        loginUser.setPermissions(new LinkedHashSet<>(permissions == null ? new ArrayList<>() : permissions));
        return loginUser;
    }
}
