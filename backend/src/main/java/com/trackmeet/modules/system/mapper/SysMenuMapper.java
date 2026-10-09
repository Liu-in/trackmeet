package com.trackmeet.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 Mapper。
 *
 * @author TrackMeet
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户可见菜单（经权限授权链路，仅启用且可见节点）。
     */
    @Select("SELECT DISTINCT m.* FROM sys_menu m "
            + "INNER JOIN sys_permission p ON p.menu_id = m.id "
            + "INNER JOIN sys_role_permission rp ON rp.permission_id = p.id "
            + "INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.status = 'ENABLED' AND m.visible = 1 "
            + "AND p.deleted = 0 AND rp.deleted = 0 AND rp.is_effective = 1 "
            + "AND ur.deleted = 0 AND ur.is_effective = 1 ORDER BY m.sort_no ASC")
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询全部启用菜单（超级管理员使用）。
     */
    @Select("SELECT * FROM sys_menu WHERE deleted = 0 AND status = 'ENABLED' ORDER BY sort_no ASC")
    List<SysMenu> selectAllEnabled();
}
