package com.trackmeet.modules.schedule.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.schedule.dto.ConflictResolveDTO;
import com.trackmeet.modules.schedule.entity.ConflictLog;
import com.trackmeet.modules.schedule.vo.ConflictVO;
import com.trackmeet.modules.schedule.dto.ConflictQuery;

import java.util.List;

/**
 * 冲突检测服务：兼项 / 场地 / 裁判 / 间隔四类冲突的检测、查询与处理。
 *
 * @author TrackMeet
 */
public interface ConflictsService extends IService<ConflictLog> {

    /**
     * 执行冲突检测。
     * <p>
     * 先清理检测范围内 {@code resolved=0} 的旧记录，再插入新记录，
     * 最后回填 {@code schedule.conflict_flag / conflict_count}。
     *
     * @param query 检测范围（meetId / sessionId / eventId）
     * @return 本次检测出的冲突清单
     */
    List<ConflictVO> detect(ConflictQuery query);

    /**
     * 冲突分页查询（conflictType / level / resolved / sessionId / eventId / scheduleId）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ConflictVO> pageConflict(ConflictQuery query);

    /**
     * 标记冲突已处理。
     *
     * @param id  冲突记录 ID
     * @param dto 入参（resolveRemark）
     */
    void resolve(Long id, ConflictResolveDTO dto);
}
