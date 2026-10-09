package com.trackmeet.modules.print.support;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 多份 PDF 合并工具（证书批量输出「每张证书一页」）。
 * <p>
 * 现有 {@code PdfTableRenderer} 只能把若干 {@code PdfSection} 顺序排版到同一份文档中，
 * <b>不提供「每节独立分页」能力</b>（该渲染器由上层实现，本模块不修改它）。
 * 因此证书报表按「一张证书渲染一份单页 PDF → 逐份合并」的方式组合实现：
 * <ol>
 *     <li>每张证书调用 {@link com.trackmeet.modules.print.pdf.PdfTableRenderer#render} 生成单页 PDF；</li>
 *     <li>用 OpenPDF 的 {@link PdfCopy} 逐页追加为一份文档（页序即证书顺序）。</li>
 * </ol>
 * 合并失败时抛出 {@link BizException}，由调用方降级为「单文档多节」输出，保证接口不因合并异常而失败。
 *
 * @author TrackMeet
 */
public final class PrintPdfMerger {

    private PrintPdfMerger() {
    }

    /**
     * 按顺序合并多份 PDF（单份时原样返回）。
     *
     * @param documents PDF 字节列表
     * @return 合并后的 PDF 字节
     * @throws BizException 参数为空或合并失败
     */
    public static byte[] merge(List<byte[]> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "待合并的 PDF 不能为空");
        }
        if (documents.size() == 1) {
            return documents.get(0);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        List<PdfReader> readers = new ArrayList<>(documents.size());
        boolean closed = false;
        try {
            PdfCopy copy = new PdfCopy(document, output);
            document.open();
            for (byte[] content : documents) {
                if (content == null || content.length == 0) {
                    continue;
                }
                PdfReader reader = new PdfReader(content);
                readers.add(reader);
                int pages = reader.getNumberOfPages();
                for (int page = 1; page <= pages; page++) {
                    copy.addPage(copy.getImportedPage(reader, page));
                }
            }
            document.close();
            closed = true;
            return output.toByteArray();
        } catch (Exception ex) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "合并 PDF 失败：" + ex.getMessage(), ex);
        } finally {
            if (!closed) {
                try {
                    document.close();
                } catch (Exception ignored) {
                    // 关闭失败无需处理，异常已在主流程抛出
                }
            }
            for (PdfReader reader : readers) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // 释放失败无需处理
                }
            }
        }
    }
}
