package com.trackmeet.modules.print.pdf;

import com.lowagie.text.Font;
import com.trackmeet.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 字体发现与表格渲染单元测试。
 *
 * <p>字体是否可用取决于运行环境（Windows 开发机 / Docker 镜像 / CI），因此断言对两种情况都成立：
 * 无字体时必须给出可读提示并抛 PDF_FONT_MISSING，有字体时必须产出合法 PDF。</p>
 *
 * @author TrackMeet
 */
class PdfRenderSupportTest {

    @Test
    @DisplayName("列宽自适应：按最长内容分配，CJK 字符按双宽计")
    void shouldEstimateColumnWidths() {
        List<String> headers = Arrays.asList("名次", "姓名", "单位");
        List<List<String>> rows = Arrays.asList(
                Arrays.asList("1", "张三", "第一中学"),
                Arrays.asList("2", "Alexander", "第二中学"));

        float[] widths = PdfTableRenderer.estimateColumnWidths(headers, rows, null);

        assertThat(widths).hasSize(3);
        // 「姓名」列最长内容为 Alexander（9 个 ASCII 字符），宽度应大于「名次」列
        assertThat(widths[1]).isGreaterThan(widths[0]);
        assertThat(widths[2]).isGreaterThanOrEqualTo(8F);
    }

    @Test
    @DisplayName("列宽：显式指定且长度匹配时优先，长度不匹配时忽略")
    void shouldRespectWidthOverride() {
        List<String> headers = Arrays.asList("A", "B");
        List<List<String>> rows = new ArrayList<>();

        float[] override = new float[]{3F, 7F};
        assertThat(PdfTableRenderer.estimateColumnWidths(headers, rows, override)).containsExactly(3F, 7F);

        float[] mismatch = new float[]{1F};
        float[] estimated = PdfTableRenderer.estimateColumnWidths(headers, rows, mismatch);
        assertThat(estimated).hasSize(2);
        assertThat(estimated[0]).isEqualTo(1F);
    }

    @Test
    @DisplayName("列宽：表头为空时按首行推断列数")
    void shouldInferColumnCountFromRows() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("1", "张三", "第一中学", "11\"23"));

        float[] widths = PdfTableRenderer.estimateColumnWidths(null, rows, null);

        assertThat(widths).hasSize(4);
        assertThat(PdfTableRenderer.estimateColumnWidths(null, new ArrayList<>(), null)).isEmpty();
    }

    @Test
    @DisplayName("文本宽度：CJK 双宽、ASCII 单宽、空值 0")
    void shouldMeasureTextWidth() {
        assertThat(PdfTableRenderer.textWidth("姓名")).isEqualTo(4F);
        assertThat(PdfTableRenderer.textWidth("Ab")).isEqualTo(2F);
        assertThat(PdfTableRenderer.textWidth("")).isEqualTo(0F);
        assertThat(PdfTableRenderer.textWidth(null)).isEqualTo(0F);
        assertThat(PdfTableRenderer.textWidth("A张")).isEqualTo(3F);
    }

    @Test
    @DisplayName("候选字体：显式配置优先，且包含容器内置字体路径")
    void shouldBuildCandidatePaths() {
        PdfFontProvider provider = new PdfFontProvider("D:/fonts/custom.ttf");
        List<String> candidates = provider.candidatePaths();

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0)).isEqualTo("D:/fonts/custom.ttf");
        assertThat(candidates).contains("/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf");
        assertThat(candidates).contains("C:/Windows/Fonts/simhei.ttf");
        assertThat(candidates).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("能力信息：始终可读取，且 available 与 fontPath 一致")
    void shouldReportCapability() {
        PdfFontProvider provider = new PdfFontProvider("");
        PdfCapability capability = provider.capability();

        assertThat(capability).isNotNull();
        assertThat(capability.getMessage()).isNotBlank();
        assertThat(capability.getCandidates()).isNotEmpty();
        assertThat(capability.isAvailable()).isEqualTo(provider.available());
        if (capability.isAvailable()) {
            assertThat(capability.getFontPath()).isNotBlank();
            assertThat(capability.getEmbeddedMode()).isEqualTo("IDENTITY_H+EMBEDDED");
        } else {
            assertThat(capability.getFontPath()).isNull();
            assertThat(capability.getMessage()).contains("font-path");
        }
    }

    @Test
    @DisplayName("字体缺失：baseFont 抛 PDF_FONT_MISSING；字体可用时返回可用的 Font")
    void shouldHandleFontAvailability() {
        PdfFontProvider provider = new PdfFontProvider("");
        if (provider.available()) {
            assertThat(provider.baseFont()).isNotNull();
            Font font = provider.font(9F, Font.BOLD);
            assertThat(font).isNotNull();
            assertThat(font.getSize()).isEqualTo(9F);
        } else {
            assertThatThrownBy(provider::baseFont)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("中文字体");
        }
    }

    @Test
    @DisplayName("渲染：有字体时输出合法 PDF（含中文标题与表格），无字体时抛业务异常")
    void shouldRenderPdfOrFailGracefully() {
        PdfFontProvider provider = new PdfFontProvider("");
        PdfTableRenderer renderer = new PdfTableRenderer(provider);
        PdfRenderRequest request = PdfRenderRequest.builder()
                .title("2025 年市中学生田径运动会 秩序册")
                .subtitle("男子甲组 100 米 预赛")
                .metaLines(Arrays.asList("时间：2025-10-18 08:00", "场地：主跑道"))
                .footerNote("TrackMeet 秩序册")
                .signatureLeft("记录员：")
                .signatureMiddle("裁判长：")
                .signatureRight("技术代表：")
                .sections(Arrays.asList(PdfSection.builder()
                        .heading("分组表")
                        .headers(Arrays.asList("组次", "道次", "号码", "姓名", "单位"))
                        .rows(Arrays.asList(
                                Arrays.asList("1", "4", "A-001", "张三", "第一中学"),
                                Arrays.asList("1", "5", "A-002", "李四", "第二中学")))
                        .note("注：8 道场地，按 4、5、3、6、2、7、1、8 顺序分配")
                        .build()))
                .build();

        if (provider.available()) {
            byte[] pdf = renderer.render(request);
            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
            assertThat(pdf.length).isGreaterThan(1000);
        } else {
            assertThatThrownBy(() -> renderer.render(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("中文字体");
        }
    }

    @Test
    @DisplayName("渲染：空请求（无节无标题）不抛异常，输出提示页")
    void shouldRenderEmptyRequest() {
        PdfFontProvider provider = new PdfFontProvider("");
        PdfTableRenderer renderer = new PdfTableRenderer(provider);
        if (!provider.available()) {
            assertThatThrownBy(() -> renderer.render(PdfRenderRequest.builder().build()))
                    .isInstanceOf(BizException.class);
            return;
        }
        byte[] pdf = renderer.render(PdfRenderRequest.builder().build());
        assertThat(pdf).isNotEmpty();
    }
}
