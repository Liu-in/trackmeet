package com.trackmeet.modules.result.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.result.dto.RankGenerateDTO;
import com.trackmeet.modules.result.service.RankService;
import com.trackmeet.modules.result.vo.RankGenerateResultVO;
import com.trackmeet.modules.result.vo.RankRowVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 名次接口：预览与生成。
 * <p>
 * 名次由成绩引擎 {@code com.trackmeet.engine.result.RankResolver#resolveRank} 计算：
 * 无效状态（DNS/DNF/DSQ/NM/PASS/SCRATCH）不参与名次；成绩相同时按平局规则
 * （次优成绩 → 最远成功轮次 → 抽签）比较，仍相同则并列且名次顺延（1、1、3 制）。
 * <p>
 * 权限编码：查询用 {@code result:list}（与接口清单第 8 节一致），生成用 {@code result:approve}
 * （生成名次即审核动作的收口步骤，两者同权限组）。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/results/rank")
@RequiredArgsConstructor
@Validated
public class RankController {

    private final RankService rankService;

    /**
     * 名次预览：调用引擎计算但<b>不</b>回写数据库。
     *
     * @param eventId        项目 ID
     * @param roundId        轮次 ID
     * @param trackAscending 排序方向（可空：径赛 true 升序、田赛 false 降序，缺省按项目类型推断）
     * @param tieBreakOrder  平局规则顺序（可空，取配置）
     * @return 名次行列表
     */
    @GetMapping
    @PreAuthorize("@perm.has('result:list')")
    public R<List<RankRowVO>> preview(@RequestParam("eventId") Long eventId,
                                      @RequestParam("roundId") Long roundId,
                                      @RequestParam(value = "trackAscending", required = false) Boolean trackAscending,
                                      @RequestParam(value = "tieBreakOrder", required = false) String tieBreakOrder) {
        return R.ok(rankService.previewRank(eventId, roundId, trackAscending, tieBreakOrder));
    }

    /**
     * 生成名次并回写 {@code result.rank_no} / {@code result.rank_display}，返回名次表与并列说明。
     */
    @PostMapping("/generate")
    @PreAuthorize("@perm.has('result:approve')")
    @OpLog(module = "RESULT", action = "RANK", actionName = "生成名次", targetType = "result")
    public R<RankGenerateResultVO> generate(@Valid @RequestBody RankGenerateDTO dto) {
        return R.ok(rankService.generateRank(dto.getEventId(), dto.getRoundId(),
                dto.getTrackAscending(), dto.getTieBreakOrder()));
    }
}
