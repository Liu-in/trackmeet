package com.trackmeet.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper。
 *
 * @author TrackMeet
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询用户拥有的全部权限编码（经角色授权且生效）。
     */
    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p "
            + "INNER JOIN sys_role_permission rp ON rp.permission_id = p.id "
            + "INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId} AND p.deleted = 0 AND p.status = 'ENABLED' "
            + "AND rp.deleted = 0 AND rp.is_effective = 1 AND ur.deleted = 0 AND ur.is_effective = 1")
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);
}
