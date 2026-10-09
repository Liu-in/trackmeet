package com.trackmeet.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.system.entity.SysImportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入导出任务 Mapper。
 *
 * @author TrackMeet
 */
@Mapper
public interface SysImportTaskMapper extends BaseMapper<SysImportTask> {
}
