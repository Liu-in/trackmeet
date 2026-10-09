package com.trackmeet.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.system.dto.SysUserQuery;
import com.trackmeet.modules.system.dto.UserSaveDTO;
import com.trackmeet.modules.system.entity.SysRole;
import com.trackmeet.modules.system.entity.SysUser;
import com.trackmeet.modules.system.entity.SysUserRole;
import com.trackmeet.modules.system.mapper.SysRoleMapper;
import com.trackmeet.modules.system.mapper.SysUserMapper;
import com.trackmeet.modules.system.mapper.SysUserRoleMapper;
import com.trackmeet.modules.system.service.SysUserService;
import com.trackmeet.modules.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现。
 * <p>密码一律经 {@link PasswordEncoder}（BCrypt）加密；状态、角色、密码变更时自增 token_version 失效旧令牌。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String GRANT_SOURCE_MANUAL = "MANUAL";

    /** 超级管理员账号 ID，禁止删除 */
    private static final Long ROOT_USER_ID = 1L;

    /** username 列长度，删除时改写唯一键需截断 */
    private static final int USERNAME_MAX_LENGTH = 64;

    private final PasswordEncoder passwordEncoder;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public PageResult<UserVO> pageUsers(SysUserQuery query) {
        SysUserQuery condition = query == null ? new SysUserQuery() : query;
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        String keyword = condition.getKeyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.eq(StringUtils.hasText(condition.getStatus()), SysUser::getStatus, condition.getStatus())
                .eq(condition.getDeptId() != null, SysUser::getDeptId, condition.getDeptId())
                .eq(condition.getUnitId() != null, SysUser::getUnitId, condition.getUnitId())
                .orderByAsc(SysUser::getSortNo)
                .orderByAsc(SysUser::getId);

        Page<SysUser> pageParam = condition.toPage();
        Page<SysUser> entityPage = page(pageParam, wrapper);

        Page<UserVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(toUserVOList(entityPage.getRecords()));
        return PageResult.of(voPage);
    }

    @Override
    public UserVO getUserDetail(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(id);
        List<Long> distinctRoleIds = roleIds == null ? new ArrayList<>()
                : roleIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        vo.setRoleIds(distinctRoleIds);
        vo.setRoleNames(queryRoleNames(distinctRoleIds));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveDTO dto) {
        String username = dto.getUsername().trim();
        if (baseMapper.selectByUsername(username) != null) {
            throw new BizException(ResultCode.CONFLICT, "登录名已存在");
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "密码不能为空");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatarFileId(dto.getAvatarFileId());
        user.setDeptId(dto.getDeptId());
        user.setUnitId(dto.getUnitId());
        user.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_ENABLED);
        user.setIsBuiltin(0);
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setPwdErrorCount(0);
        user.setForcePwdChange(0);
        user.setTokenVersion(0);
        user.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        user.setRemark(dto.getRemark());
        save(user);
        rebuildUserRoles(user.getId(), dto.getRoleIds());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserSaveDTO dto) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String username = dto.getUsername().trim();
        if (!username.equals(user.getUsername())) {
            SysUser exist = baseMapper.selectByUsername(username);
            if (exist != null && !exist.getId().equals(id)) {
                throw new BizException(ResultCode.CONFLICT, "登录名已存在");
            }
            user.setUsername(username);
        }
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatarFileId(dto.getAvatarFileId());
        user.setDeptId(dto.getDeptId());
        user.setUnitId(dto.getUnitId());
        if (dto.getSortNo() != null) {
            user.setSortNo(dto.getSortNo());
        }
        user.setRemark(dto.getRemark());

        boolean statusChanged = StringUtils.hasText(dto.getStatus()) && !dto.getStatus().equals(user.getStatus());
        if (statusChanged) {
            user.setStatus(dto.getStatus());
        }
        boolean rolesChanged = dto.getRoleIds() != null;
        if (statusChanged || rolesChanged) {
            user.setTokenVersion(currentTokenVersion(user) + 1);
        }
        updateById(user);
        if (rolesChanged) {
            rebuildUserRoles(id, dto.getRoleIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (ROOT_USER_ID.equals(id) || (user.getIsBuiltin() != null && user.getIsBuiltin() == 1)) {
            throw new BizException(ResultCode.FORBIDDEN, "内置账号禁止删除");
        }
        userRoleMapper.deleteByUserId(id);
        baseMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getUsername, releaseUniqueKey(user.getUsername(), id, USERNAME_MAX_LENGTH)));
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BizException(ResultCode.PARAM_ERROR, "状态只能为 ENABLED 或 DISABLED");
        }
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        user.setTokenVersion(currentTokenVersion(user) + 1);
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BizException(ResultCode.PARAM_ERROR, "密码不能为空");
        }
        if (rawPassword.length() < 8 || rawPassword.length() > 64) {
            throw new BizException(ResultCode.PARAM_ERROR, "密码长度需为 8-64 位");
        }
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<SysUser> updateWrapper = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, passwordEncoder.encode(rawPassword))
                .set(SysUser::getForcePwdChange, 1)
                .set(SysUser::getPwdUpdateTime, now)
                .set(SysUser::getPwdErrorCount, 0)
                .set(SysUser::getLockUntil, null)
                .set(SysUser::getTokenVersion, currentTokenVersion(user) + 1)
                .set(SysUser::getUpdateTime, now);
        Long operatorId = SecurityUtils.getUserId();
        if (operatorId != null) {
            updateWrapper.set(SysUser::getUpdateBy, operatorId);
        }
        update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        rebuildUserRoles(id, roleIds == null ? new ArrayList<>() : roleIds);
        user.setTokenVersion(currentTokenVersion(user) + 1);
        updateById(user);
    }

    /**
     * 重建用户角色关联：先按 user_id 物理删除，再按顺序批量插入（首个为主角色）。
     */
    private void rebuildUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        List<Long> distinctRoleIds = roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!distinctRoleIds.isEmpty()) {
            List<SysRole> roles = listRolesByIds(distinctRoleIds);
            if (roles.size() != distinctRoleIds.size()) {
                throw new BizException(ResultCode.PARAM_ERROR, "存在无效的角色 ID");
            }
        }
        userRoleMapper.deleteByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = SecurityUtils.getUserId();
        int index = 0;
        for (Long roleId : distinctRoleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setGrantSource(GRANT_SOURCE_MANUAL);
            relation.setGrantBy(operatorId);
            relation.setGrantTime(now);
            relation.setIsPrimary(index == 0 ? 1 : 0);
            relation.setIsEffective(1);
            userRoleMapper.insert(relation);
            index++;
        }
    }

    private List<String> queryRoleNames(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return listRolesByIds(roleIds).stream()
                .map(SysRole::getRoleName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<SysRole> listRolesByIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));
    }

    private int currentTokenVersion(SysUser user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    /**
     * 逻辑删除时释放业务唯一键（docs/12-01 第 4 节）：追加 {@code _d{id}} 后缀，
     * 避免与删除后重新启用的同名记录在唯一索引上冲突。
     */
    private static String releaseUniqueKey(String original, Long id, int maxLength) {
        String suffix = "_d" + id;
        String base = original == null ? "" : original;
        int keep = Math.max(0, maxLength - suffix.length());
        if (base.length() > keep) {
            base = base.substring(0, keep);
        }
        return base + suffix;
    }

    private List<UserVO> toUserVOList(List<SysUser> users) {
        List<UserVO> voList = new ArrayList<>();
        if (users == null || users.isEmpty()) {
            return voList;
        }
        for (SysUser user : users) {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            voList.add(vo);
        }
        fillRoleInfo(voList);
        return voList;
    }

    /**
     * 批量填充角色的 ID 与名称，避免逐行查询。
     */
    private void fillRoleInfo(List<UserVO> voList) {
        List<Long> userIds = voList.stream()
                .map(UserVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds)
                .orderByAsc(SysUserRole::getId));
        if (relations.isEmpty()) {
            return;
        }
        List<Long> roleIds = relations.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysRole> roleMap = new HashMap<>();
        for (SysRole role : listRolesByIds(roleIds)) {
            roleMap.put(role.getId(), role);
        }
        Map<Long, List<Long>> userRoleIdMap = new HashMap<>();
        Map<Long, List<String>> userRoleNameMap = new HashMap<>();
        for (SysUserRole relation : relations) {
            SysRole role = roleMap.get(relation.getRoleId());
            if (role == null) {
                continue;
            }
            userRoleIdMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(role.getId());
            userRoleNameMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(role.getRoleName());
        }
        for (UserVO vo : voList) {
            vo.setRoleIds(userRoleIdMap.getOrDefault(vo.getId(), new ArrayList<>()));
            vo.setRoleNames(userRoleNameMap.getOrDefault(vo.getId(), new ArrayList<>()));
        }
    }
}
