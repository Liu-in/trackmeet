package com.trackmeet.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.RequestUtils;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.auth.dto.CaptchaVO;
import com.trackmeet.modules.auth.dto.ChangePasswordDTO;
import com.trackmeet.modules.auth.dto.LoginDTO;
import com.trackmeet.modules.auth.dto.LoginVO;
import com.trackmeet.modules.auth.dto.MenuNodeVO;
import com.trackmeet.modules.auth.dto.UserInfoVO;
import com.trackmeet.modules.auth.service.AuthService;
import com.trackmeet.modules.auth.service.CaptchaService;
import com.trackmeet.modules.system.entity.SysLoginLog;
import com.trackmeet.modules.system.entity.SysMenu;
import com.trackmeet.modules.system.entity.SysUser;
import com.trackmeet.modules.system.mapper.SysLoginLogMapper;
import com.trackmeet.modules.system.mapper.SysMenuMapper;
import com.trackmeet.modules.system.mapper.SysUserMapper;
import com.trackmeet.security.JwtTokenProvider;
import com.trackmeet.security.LoginUser;
import com.trackmeet.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务实现。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 连续输错锁定阈值 */
    private static final int MAX_PWD_ERROR_COUNT = 5;
    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 10L;

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final CaptchaService captchaService;

    @Value("${trackmeet.captcha.enabled:false}")
    private boolean captchaEnabled;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO loginDTO, HttpServletRequest request) {
        if (captchaEnabled && !captchaService.validate(loginDTO.getCaptchaKey(), loginDTO.getCaptchaCode())) {
            saveLoginLog(null, loginDTO.getUsername(), "LOGIN", 0, "验证码错误", request);
            throw new BizException(ResultCode.CAPTCHA_ERROR);
        }

        SysUser user = userMapper.selectByUsername(loginDTO.getUsername());
        if (user == null) {
            saveLoginLog(null, loginDTO.getUsername(), "LOGIN", 0, "用户不存在", request);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
            saveLoginLog(user.getId(), user.getUsername(), "LOGIN", 0, "账号已停用", request);
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            saveLoginLog(user.getId(), user.getUsername(), "LOGIN", 0, "账号锁定中", request);
            throw new BizException(ResultCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            int errorCount = increasePasswordError(user);
            saveLoginLog(user.getId(), user.getUsername(), "LOGIN", 0,
                    errorCount >= MAX_PWD_ERROR_COUNT ? "密码错误，账号已锁定" : "密码错误", request);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        clearPasswordError(user.getId(), RequestUtils.getClientIp(request));
        saveLoginLog(user.getId(), user.getUsername(), "LOGIN", 1, null, request);

        LoginUser loginUser = userDetailsService.loadUserById(user.getId());
        loginUser.setLoginTime(LocalDateTime.now());
        return buildLoginVO(loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)
                || !jwtTokenProvider.validate(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        LoginUser loginUser = userDetailsService.loadUserById(userId);
        if (!loginUser.isEnabled()) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        Integer tokenVersion = jwtTokenProvider.getTokenVersion(refreshToken);
        if (loginUser.getTokenVersion() != null && !loginUser.getTokenVersion().equals(tokenVersion)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "令牌已失效，请重新登录");
        }
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUserId(userId);
        loginLog.setUsername(loginUser.getUsername());
        loginLog.setLoginType("REFRESH");
        loginLog.setSuccess(1);
        loginLog.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(loginLog);
        return buildLoginVO(loginUser);
    }

    @Override
    public void logout(String authorizationHeader, HttpServletRequest request) {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        String token = jwtTokenProvider.resolveToken(authorizationHeader);
        if (StringUtils.hasText(token)) {
            jwtTokenProvider.blacklist(token);
        }
        saveLoginLog(loginUser == null ? null : loginUser.getId(),
                loginUser == null ? null : loginUser.getUsername(), "LOGOUT", 1, null, request);
    }

    @Override
    public UserInfoVO currentUserInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return buildUserInfo(loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = userMapper.selectById(loginUser.getId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "原密码不正确");
        }
        if (passwordEncoder.matches(changePasswordDTO.getNewPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, user.getId())
                .set(SysUser::getPassword, passwordEncoder.encode(changePasswordDTO.getNewPassword()))
                .set(SysUser::getPwdUpdateTime, LocalDateTime.now())
                .set(SysUser::getForcePwdChange, 0)
                .set(SysUser::getPwdErrorCount, 0)
                .setSql("token_version = token_version + 1");
        userMapper.update(null, wrapper);
    }

    @Override
    public CaptchaVO captcha() {
        return captchaService.generate();
    }

    private LoginVO buildLoginVO(LoginUser loginUser) {
        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(jwtTokenProvider.createAccessToken(
                loginUser.getId(), loginUser.getUsername(), loginUser.getTokenVersion()));
        loginVO.setRefreshToken(jwtTokenProvider.createRefreshToken(
                loginUser.getId(), loginUser.getUsername(), loginUser.getTokenVersion()));
        loginVO.setTokenType("Bearer");
        loginVO.setExpiresIn(jwtTokenProvider.getAccessTokenExpireSeconds());
        loginVO.setUserInfo(buildUserInfo(loginUser));
        return loginVO;
    }

    private UserInfoVO buildUserInfo(LoginUser loginUser) {
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(loginUser.getId());
        userInfo.setUsername(loginUser.getUsername());
        userInfo.setRealName(loginUser.getRealName());
        userInfo.setDeptId(loginUser.getDeptId());
        userInfo.setUnitId(loginUser.getUnitId());
        userInfo.setStatus(loginUser.getStatus());
        userInfo.setForcePwdChange(loginUser.getForcePwdChange());
        userInfo.setRoles(loginUser.getRoles() == null ? new ArrayList<>() : loginUser.getRoles());
        if (loginUser.getPermissions() != null) {
            userInfo.setPermissions(loginUser.getPermissions());
        }
        userInfo.setMenus(loadMenus(loginUser));
        return userInfo;
    }

    private List<MenuNodeVO> loadMenus(LoginUser loginUser) {
        List<SysMenu> menus = loginUser.isSuperAdmin()
                ? menuMapper.selectAllEnabled()
                : menuMapper.selectMenusByUserId(loginUser.getId());
        return assembleMenuTree(menus == null ? new ArrayList<>() : menus);
    }

    /**
     * 平铺菜单组装为树：一次遍历建索引，parentId 为 0 或父节点不可见的作为根。
     */
    private List<MenuNodeVO> assembleMenuTree(List<SysMenu> menus) {
        Map<Long, MenuNodeVO> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            nodeMap.put(menu.getId(), toMenuNode(menu));
        }
        List<MenuNodeVO> roots = new ArrayList<>();
        for (MenuNodeVO node : nodeMap.values()) {
            Long parentId = node.getParentId();
            MenuNodeVO parent = (parentId == null || parentId == 0L) ? null : nodeMap.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private MenuNodeVO toMenuNode(SysMenu menu) {
        MenuNodeVO node = new MenuNodeVO();
        node.setId(menu.getId());
        node.setParentId(menu.getParentId());
        node.setMenuName(menu.getMenuName());
        node.setMenuType(menu.getMenuType());
        node.setPath(menu.getPath());
        node.setComponent(menu.getComponent());
        node.setIcon(menu.getIcon());
        node.setPermCode(menu.getPermCode());
        node.setVisible(menu.getVisible());
        node.setKeepAlive(menu.getKeepAlive());
        node.setSortNo(menu.getSortNo());
        return node;
    }

    private int increasePasswordError(SysUser user) {
        int errorCount = (user.getPwdErrorCount() == null ? 0 : user.getPwdErrorCount()) + 1;
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, user.getId()).set(SysUser::getPwdErrorCount, errorCount);
        if (errorCount >= MAX_PWD_ERROR_COUNT) {
            wrapper.set(SysUser::getLockUntil, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        }
        userMapper.update(null, wrapper);
        return errorCount;
    }

    private void clearPasswordError(Long userId, String loginIp) {
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, userId)
                .set(SysUser::getPwdErrorCount, 0)
                .set(SysUser::getLockUntil, null)
                .set(SysUser::getLastLoginTime, LocalDateTime.now())
                .set(SysUser::getLastLoginIp, loginIp);
        userMapper.update(null, wrapper);
    }

    private void saveLoginLog(Long userId, String username, String loginType, int success,
                              String failReason, HttpServletRequest request) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setLoginType(loginType);
            loginLog.setSuccess(success);
            loginLog.setFailReason(failReason);
            loginLog.setLoginTime(LocalDateTime.now());
            if (request != null) {
                loginLog.setIp(RequestUtils.getClientIp(request));
                String ua = RequestUtils.getUserAgent(request);
                loginLog.setUa(ua);
                loginLog.setOs(RequestUtils.getOs(ua));
                loginLog.setBrowser(RequestUtils.getBrowser(ua));
                loginLog.setDeviceType(RequestUtils.getDeviceType(ua));
            }
            loginLogMapper.insert(loginLog);
        } catch (Exception ex) {
            log.warn("写入登录日志失败: {}", ex.getMessage());
        }
    }
}
