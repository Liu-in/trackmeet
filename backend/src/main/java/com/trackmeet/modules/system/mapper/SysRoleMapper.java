package com.trackmeet.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper。
 *
 * @author TrackMeet
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询用户的有效角色。
     */
    @Select("SELECT r.* FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND ur.deleted = 0 AND ur.is_effective = 1 "
            + "AND r.deleted = 0 AND r.status = 'ENABLED' ORDER BY r.sort_no ASC")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的有效角色编码。
     */
    @Select("SELECT r.role_code FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND ur.deleted = 0 AND ur.is_effective = 1 "
            + "AND r.deleted = 0 AND r.status = 'ENABLED' ORDER BY r.sort_no ASC")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
