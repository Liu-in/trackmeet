package com.trackmeet.modules.result.service;

import com.trackmeet.engine.model.TieBreakRule;
import com.trackmeet.modules.result.vo.RankGenerateResultVO;
import com.trackmeet.modules.result.vo.RankRowVO;

import java.util.List;

/**
 * 名次服务：装配成绩引擎输入 → 调用 {@code com.trackmeet.engine.result.RankResolver} → 回写名次。
 * <p>
 * 名次结果落在 {@code result.rank_no} 与 {@code result.rank_display}；
 * 无效状态（DNS/DNF/DSQ/NM/PASS/SCRATCH）不参与名次，名次置空并排在有效成绩之后。
 * <p>
 * 说明：本接口不做成绩实体 CRUD（成绩增删改由 {@link ResultService} 承担），
 * 仅负责名次计算与回写，因此不继承 {@code IService}。
 *
 * @author TrackMeet
 */
public interface RankService {

    /**
     * 名次预览：调用引擎计算但<b>不</b>回写数据库。
     *
     * @param eventId        项目 ID
     * @param roundId        轮次 ID
     * @param trackAscending 排序方向：true 升序（径赛）/ false 降序（田赛）；为空按项目类型推断
     * @param tieBreakOrder  平局规则顺序（可空，空取配置）
     * @return 名次行列表（含并列与田赛平局比较字段）
     */
    List<RankRowVO> previewRank(Long eventId, Long roundId, Boolean trackAscending, String tieBreakOrder);

    /**
     * 生成名次并回写数据库，返回含并列说明的结果视图。
     *
     * @param eventId        项目 ID
     * @param roundId        轮次 ID
     * @param trackAscending 排序方向：true 升序（径赛）/ false 降序（田赛）；为空按项目类型推断
     * @param tieBreakOrder  平局规则顺序（可空，空取配置）
     * @return 名次生成结果视图
     */
    RankGenerateResultVO generateRank(Long eventId, Long roundId, Boolean trackAscending, String tieBreakOrder);

    /**
     * 清理某一轮全部成绩的名次（成绩改值 / 驳回 / 撤销审核时联动，避免名次与成绩不一致）。
     *
     * @param eventId 项目 ID
     * @param roundId 轮次 ID
     * @return 受影响行数
     */
    int clearRank(Long eventId, Long roundId);

    /**
     * 项目排序方向：径赛 / 接力为 true（成绩越小越好），田赛与全能为 false。
     *
     * @param eventId 项目 ID
     * @return 排序方向
     */
    boolean resolveTrackAscending(Long eventId);

    /**
     * 解析平局规则：轮次级 → 项目级 → 引擎默认（次优成绩 → 最远成功轮次 → 允许并列）。
     *
     * @param roundId       轮次 ID
     * @param eventId       项目 ID
     * @param explicitOrder 显式指定的规则顺序（可空）
     * @return 平局规则
     */
    TieBreakRule resolveTieBreakRule(Long roundId, Long eventId, String explicitOrder);
}
