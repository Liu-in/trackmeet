package com.trackmeet.modules.standing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.standing.dto.CertificateGenerateDTO;
import com.trackmeet.modules.standing.dto.StandingCertificateRow;
import com.trackmeet.modules.standing.entity.Certificate;
import com.trackmeet.modules.standing.vo.CertificateVO;

import java.io.OutputStream;
import java.util.List;

/**
 * 证书服务：批量生成、列表查询、导出与打印计数。
 *
 * @author TrackMeet
 */
public interface CertificateService extends IService<Certificate> {

    /**
     * 按成绩 / 名次批量生成证书。
     * <p>
     * 证书编号规则 {@code <届次编码>-<类型>-<项目>-<名次>-<序号>}，全局唯一
     * （{@code uk_cert_no} 不含 {@code deleted}，序号按库内既有同前缀条数递推）。
     *
     * @param dto 生成入参（meetId 必填）
     * @return 实际生成条数
     */
    int generate(CertificateGenerateDTO dto);

    /**
     * 证书分页查询。
     *
     * @param meetId   届次 ID（必填）
     * @param certType 证书类型（可空）
     * @param unitId   单位 ID（可空）
     * @param keyword  关键字（可空）
     * @param page     页码（从 1 开始）
     * @param size     每页条数
     * @return 分页结果
     */
    PageResult<CertificateVO> pageCertificates(Long meetId, String certType, Long unitId,
                                               String keyword, long page, long size);

    /**
     * 按 ID 列表查询证书明细（导出下载用）。
     *
     * @param meetId 届次 ID（必填）
     * @param ids    证书 ID 列表
     * @return 证书投影列表
     */
    List<StandingCertificateRow> listByIds(Long meetId, List<Long> ids);

    /**
     * 累加打印次数并刷新最近打印时间。
     *
     * @param meetId 届次 ID（必填）
     * @param ids    证书 ID 列表
     * @return 受影响条数
     */
    int markPrinted(Long meetId, List<Long> ids);

    /**
     * 写出证书 Excel 文件流（一行一证书）。
     *
     * @param rows         证书投影列表
     * @param outputStream 输出流
     */
    void writeExcel(List<StandingCertificateRow> rows, OutputStream outputStream);

    /**
     * 构建证书导出文件名。
     *
     * @param meetName 届次名称（可空）
     * @return 文件名（含 .xlsx 后缀）
     */
    String buildFileName(String meetName);

    /**
     * 投影行 → 证书 VO。
     *
     * @param row 投影行
     * @return 证书 VO
     */
    CertificateVO toVO(StandingCertificateRow row);
}
