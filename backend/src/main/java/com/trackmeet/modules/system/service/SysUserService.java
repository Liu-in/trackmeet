package com.trackmeet.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.system.dto.SysUserQuery;
import com.trackmeet.modules.system.dto.UserSaveDTO;
import com.trackmeet.modules.system.entity.SysUser;
import com.trackmeet.modules.system.vo.UserVO;

import java.util.List;

/**
 * 用户管理服务。
 *
 * @author TrackMeet
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户分页列表。
     */
    PageResult<UserVO> pageUsers(SysUserQuery query);

    /**
     * 用户详情（含 roleIds、roleNames）。
     */
    UserVO getUserDetail(Long id);

    /**
     * 新增用户，返回新用户 ID。
     */
    Long createUser(UserSaveDTO dto);

    /**
     * 修改用户资料（不修改密码），可同时重建角色关联。
     */
    void updateUser(Long id, UserSaveDTO dto);

    /**
     * 逻辑删除用户，内置账号禁止删除。
     */
    void deleteUser(Long id);

    /**
     * 启用 / 停用用户，并失效旧令牌。
     */
    void updateStatus(Long id, String status);

    /**
     * 重置密码：BCrypt 加密、强制改密、令牌版本自增、清零错误次数。
     */
    void resetPassword(Long id, String rawPassword);

    /**
     * 分配角色：先物理删除原关联再批量插入，并失效旧令牌。
     */
    void assignRoles(Long id, List<Long> roleIds);
}
