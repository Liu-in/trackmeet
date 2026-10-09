package com.trackmeet.modules.print.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 PDF 表格渲染器：A4/A3、页眉页脚、页码、表头跨页重复、签名区、列宽自适应。
 *
 * <p>面向「秩序册 / 成绩公告 / 检录表 / 分道表 / 积分表 / 奖牌榜 / 证书」等报表的统一出图能力：
 * 调用方只需把数据组织成 {@link PdfRenderRequest}（若干 {@link PdfSection}），
 * 无需关心分页与排版细节。中文字体由 {@link PdfFontProvider} 提供，缺失时抛
 * {@link ResultCode#PDF_FONT_MISSING}。</p>
 *
 * @author TrackMeet
 */
@Component
@RequiredArgsConstructor
public class PdfTableRenderer {

    private static final DateTimeFormatter PRINT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final float MARGIN_LEFT = 42F;
    private static final float MARGIN_RIGHT = 42F;
    private static final float MARGIN_TOP = 48F;
    private static final float MARGIN_BOTTOM = 48F;

    private static final Color HEADER_BACKGROUND = new Color(238, 242, 247);
    private static final Color BORDER_COLOR = new Color(150, 156, 165);
    private static final Color META_COLOR = new Color(90, 96, 105);

    private final PdfFontProvider fontProvider;

    /**
     * 渲染 PDF。
     *
     * @param request 渲染请求
     * @return PDF 字节
     */
    public byte[] render(PdfRenderRequest request) {
        if (!fontProvider.available()) {
            throw new BizException(ResultCode.PDF_FONT_MISSING, fontProvider.capability().getMessage());
        }
        PdfRenderRequest safeRequest = request == null ? PdfRenderRequest.builder().build() : request;
        float baseSize = safeRequest.getBaseFontSize() == null ? 9F : safeRequest.getBaseFontSize();
        boolean landscape = isLandscape(safeRequest);
        Rectangle pageSize = resolvePageSize(safeRequest.getPageSize(), landscape);

        Document document = new Document(pageSize, MARGIN_LEFT, MARGIN_RIGHT, MARGIN_TOP, MARGIN_BOTTOM);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new FooterEvent(fontProvider, safeRequest, () -> document.getPageNumber()));
            document.open();
            writeHeader(document, safeRequest, baseSize);
            List<PdfSection> sections = safeRequest.getSections() == null ? new ArrayList<>() : safeRequest.getSections();
            if (sections.isEmpty()) {
                document.add(paragraph("暂无数据", fontProvider.font(baseSize + 1, Font.NORMAL), Element.ALIGN_CENTER, 12F));
            }
            for (PdfSection section : sections) {
                writeSection(document, section, baseSize);
            }
            writeSignature(document, safeRequest, baseSize);
            document.close();
        } catch (DocumentException ex) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "生成 PDF 失败：" + ex.getMessage());
        }
        return output.toByteArray();
    }

    /**
     * 列宽自适应：按各列最长内容估算（中日韩字符按 2 个宽度计），最小值 1。
     */
    public static float[] estimateColumnWidths(List<String> headers, List<List<String>> rows, float[] override) {
        int columnCount = headers == null ? 0 : headers.size();
        if (columnCount == 0 && rows != null && !rows.isEmpty() && rows.get(0) != null) {
            columnCount = rows.get(0).size();
        }
        if (columnCount <= 0) {
            return new float[0];
        }
        if (override != null && override.length == columnCount) {
            return override.clone();
        }
        float[] widths = new float[columnCount];
        for (int column = 0; column < columnCount; column++) {
            float max = headers != null && column < headers.size() ? textWidth(headers.get(column)) : 1F;
            if (rows != null) {
                for (List<String> row : rows) {
                    if (row != null && column < row.size()) {
                        max = Math.max(max, textWidth(row.get(column)));
                    }
                }
            }
            widths[column] = Math.max(1F, max);
        }
        return widths;
    }

    /**
     * 文本显示宽度：CJK 与全角标点按 2 计。
     */
    public static float textWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0F;
        }
        float width = 0F;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            width += isWide(character) ? 2F : 1F;
        }
        return width;
    }

    private static boolean isWide(char character) {
        return character >= 0x1100 && (character <= 0x115F
                || (character >= 0x2E80 && character <= 0xA4CF)
                || (character >= 0xAC00 && character <= 0xD7A3)
                || (character >= 0xF900 && character <= 0xFAFF)
                || (character >= 0xFE30 && character <= 0xFE6F)
                || (character >= 0xFF00 && character <= 0xFF60)
                || (character >= 0xFFE0 && character <= 0xFFE6));
    }

    private void writeHeader(Document document, PdfRenderRequest request, float baseSize) throws DocumentException {
        if (notBlank(request.getTitle())) {
            document.add(paragraph(request.getTitle(), fontProvider.font(baseSize + 7, Font.BOLD), Element.ALIGN_CENTER, 6F));
        }
        if (notBlank(request.getSubtitle())) {
            document.add(paragraph(request.getSubtitle(), fontProvider.font(baseSize + 2, Font.NORMAL), Element.ALIGN_CENTER, 4F));
        }
        Font metaFont = fontProvider.font(baseSize - 0.5F, Font.NORMAL);
        metaFont.setColor(META_COLOR);
        if (request.getMetaLines() != null) {
            for (String line : request.getMetaLines()) {
                document.add(paragraph(line, metaFont, Element.ALIGN_CENTER, 2F));
            }
        }
        document.add(spacer(baseSize));
    }

    private void writeSection(Document document, PdfSection section, float baseSize) throws DocumentException {
        if (section == null) {
            return;
        }
        float fontSize = section.getFontSize() == null ? baseSize : section.getFontSize();
        if (notBlank(section.getHeading())) {
            Paragraph heading = paragraph(section.getHeading(), fontProvider.font(fontSize + 2.5F, Font.BOLD), Element.ALIGN_LEFT, 4F);
            heading.setSpacingBefore(10F);
            document.add(heading);
        }
        if (section.getMetaLines() != null) {
            Font metaFont = fontProvider.font(fontSize - 0.5F, Font.NORMAL);
            metaFont.setColor(META_COLOR);
            for (String line : section.getMetaLines()) {
                document.add(paragraph(line, metaFont, Element.ALIGN_LEFT, 2F));
            }
        }
        if (section.getHeaders() != null && !section.getHeaders().isEmpty()) {
            document.add(buildTable(section, fontSize));
        } else if (section.getRows() != null) {
            for (List<String> row : section.getRows()) {
                document.add(paragraph(row == null || row.isEmpty() ? "" : row.get(0),
                        fontProvider.font(fontSize, Font.NORMAL), Element.ALIGN_LEFT, 2F));
            }
        }
        if (notBlank(section.getNote())) {
            Font noteFont = fontProvider.font(fontSize - 0.5F, Font.NORMAL);
            noteFont.setColor(META_COLOR);
            Paragraph note = paragraph(section.getNote(), noteFont, Element.ALIGN_LEFT, 4F);
            note.setSpacingBefore(2F);
            document.add(note);
        }
    }

    private PdfPTable buildTable(PdfSection section, float fontSize) throws DocumentException {
        float[] widths = estimateColumnWidths(section.getHeaders(), section.getRows(), section.getColumnWidths());
        PdfPTable table = new PdfPTable(widths.length);
        table.setWidthPercentage(100F);
        table.setWidths(widths);
        table.setHeaderRows(section.isRepeatHeader() ? 1 : 0);
        Font headerFont = fontProvider.font(fontSize, Font.BOLD);
        Font cellFont = fontProvider.font(fontSize, Font.NORMAL);
        for (String header : section.getHeaders()) {
            table.addCell(cell(header, headerFont, Element.ALIGN_CENTER, true));
        }
        List<List<String>> rows = section.getRows() == null ? new ArrayList<>() : section.getRows();
        if (rows.isEmpty()) {
            PdfPCell empty = cell("暂无数据", cellFont, Element.ALIGN_CENTER, false);
            empty.setColspan(Math.max(1, section.getHeaders().size()));
            table.addCell(empty);
            return table;
        }
        int index = 0;
        for (List<String> row : rows) {
            index++;
            for (int column = 0; column < widths.length; column++) {
                String value = row != null && column < row.size() ? row.get(column) : "";
                table.addCell(cell(value == null ? "-" : value, cellFont,
                        column == 0 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT, index % 2 == 0));
            }
        }
        return table;
    }

    private PdfPCell cell(String text, Font font, int alignment, boolean shaded) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(3.5F);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderWidth(0.4F);
        cell.setBorderColor(BORDER_COLOR);
        if (shaded) {
            cell.setBackgroundColor(HEADER_BACKGROUND);
        }
        return cell;
    }

    private void writeSignature(Document document, PdfRenderRequest request, float baseSize) throws DocumentException {
        if (!notBlank(request.getSignatureLeft()) && !notBlank(request.getSignatureMiddle())
                && !notBlank(request.getSignatureRight())) {
            return;
        }
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100F);
        table.setSpacingBefore(18F);
        Font font = fontProvider.font(baseSize + 0.5F, Font.NORMAL);
        for (String text : new String[]{request.getSignatureLeft(), request.getSignatureMiddle(), request.getSignatureRight()}) {
            PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPaddingTop(10F);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        document.add(table);
    }

    private Paragraph paragraph(String text, Font font, int alignment, float spacingAfter) {
        Paragraph paragraph = new Paragraph(text == null ? "" : text, font);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(spacingAfter);
        paragraph.setLeading(font.getSize() * 1.45F);
        return paragraph;
    }

    private Paragraph spacer(float baseSize) {
        Paragraph paragraph = new Paragraph(" ", fontProvider.font(baseSize, Font.NORMAL));
        paragraph.setSpacingAfter(4F);
        return paragraph;
    }

    private boolean isLandscape(PdfRenderRequest request) {
        if ("LANDSCAPE".equalsIgnoreCase(request.getOrientation())) {
            return true;
        }
        if (request.getSections() != null) {
            for (PdfSection section : request.getSections()) {
                if (section != null && section.isLandscape()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Rectangle resolvePageSize(String pageSize, boolean landscape) {
        Rectangle rectangle = "A3".equalsIgnoreCase(pageSize) ? PageSize.A3 : PageSize.A4;
        return landscape ? rectangle.rotate() : rectangle;
    }

    private static boolean notBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * 页脚事件：打印时间 + 页脚说明 + 页码。
     */
    private static class FooterEvent extends PdfPageEventHelper {

        private final PdfFontProvider fontProvider;
        private final PdfRenderRequest request;
        private final java.util.function.IntSupplier pageNumberSupplier;

        FooterEvent(PdfFontProvider fontProvider, PdfRenderRequest request,
                    java.util.function.IntSupplier pageNumberSupplier) {
            this.fontProvider = fontProvider;
            this.request = request;
            this.pageNumberSupplier = pageNumberSupplier;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (!fontProvider.available()) {
                return;
            }
            Font footerFont = fontProvider.font(7.5F, Font.NORMAL);
            footerFont.setColor(META_COLOR);
            PdfContentByte canvas = writer.getDirectContent();
            float left = document.left();
            float right = document.right();
            float bottom = document.bottom() - 18F;
            String leftText = request.getFooterNote() == null ? "" : request.getFooterNote();
            if (request.isShowPrintTime()) {
                String time = "打印时间：" + LocalDateTime.now().format(PRINT_TIME_FORMAT);
                leftText = leftText.isEmpty() ? time : leftText + "    " + time;
            }
            if (!leftText.isEmpty()) {
                com.lowagie.text.pdf.ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                        new Phrase(leftText, footerFont), left, bottom, 0);
            }
            if (request.isShowPageNumber()) {
                com.lowagie.text.pdf.ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                        new Phrase("第 " + pageNumberSupplier.getAsInt() + " 页", footerFont), right, bottom, 0);
            }
        }
    }
}
