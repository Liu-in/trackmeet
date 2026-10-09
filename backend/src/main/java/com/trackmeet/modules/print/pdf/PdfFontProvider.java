package com.trackmeet.modules.print.pdf;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PDF 中文字体提供者：按「显式配置 → 系统常见字体 → 内嵌资源」顺序发现并加载可嵌入的 TrueType 字体。
 *
 * <p>中文 PDF 必须内嵌字体（PDF 内置的 STSong-Light 等 CJK 字体在多数阅读器中不可用），
 * 因此这里只接受真实字体文件，并要求为 <b>.ttf</b> 或 <b>.ttc（支持 {@code 文件,索引} 写法）</b>。</p>
 *
 * <p>候选顺序：</p>
 * <ol>
 *   <li>{@code trackmeet.pdf.font-path}（可为文件路径或 {@code xxx.ttc,0}）；</li>
 *   <li>Windows：{@code simhei.ttf} / {@code simkai.ttf} / {@code simfang.ttf} / {@code Deng.ttf} / {@code msyh.ttc,0}；</li>
 *   <li>Linux（Docker 已安装 fonts-droid-fallback / 常见 CJK 包）：{@code DroidSansFallbackFull.ttf}、文泉驿、Noto CJK、AR PL；</li>
 *   <li>macOS：{@code PingFang.ttc,0}。</li>
 * </ol>
 *
 * <p>找不到字体时不抛异常（{@link #available()} 返回 false），仅在真正渲染时由
 * {@link #baseFont()} 抛 {@link ResultCode#PDF_FONT_MISSING}，从而让前端可禁用 PDF 按钮并给出配置提示。</p>
 *
 * @author TrackMeet
 */
@Slf4j
@Component
public class PdfFontProvider {

    /** 常见系统中文字体候选（按优先级） */
    private static final List<String> SYSTEM_CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simkai.ttf",
            "C:/Windows/Fonts/simfang.ttf",
            "C:/Windows/Fonts/Deng.ttf",
            "C:/Windows/Fonts/msyh.ttc,0",
            "C:/Windows/Fonts/simsun.ttc,0",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc,0",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/truetype/arphic/uming.ttc,0",
            "/System/Library/Fonts/PingFang.ttc,0"
    );

    /** 内嵌资源候选（classpath:fonts/*.ttf，可选） */
    private static final List<String> RESOURCE_CANDIDATES = List.of(
            "fonts/NotoSansSC-Regular.ttf",
            "fonts/SourceHanSansCN-Regular.ttf"
    );

    private final String configuredFontPath;
    private final List<String> attempted = Collections.synchronizedList(new ArrayList<>());

    private volatile BaseFont baseFont;
    private volatile String loadedPath;
    private volatile String loadedName;
    private volatile boolean resolved;

    public PdfFontProvider(@Value("${trackmeet.pdf.font-path:}") String configuredFontPath) {
        this.configuredFontPath = configuredFontPath == null ? "" : configuredFontPath.trim();
    }

    /**
     * 是否可用。
     */
    public boolean available() {
        return resolve() != null;
    }

    /**
     * 能力信息（不抛异常，供 /pdf/capability 返回）。
     */
    public PdfCapability capability() {
        BaseFont font = resolve();
        PdfCapability.PdfCapabilityBuilder builder = PdfCapability.builder()
                .available(font != null)
                .fontPath(loadedPath)
                .fontName(loadedName)
                .embeddedMode(font == null ? null : "IDENTITY_H+EMBEDDED")
                .candidates(new ArrayList<>(attempted));
        if (font == null) {
            builder.message("未找到可嵌入的中文字体：请在 application.yml 配置 trackmeet.pdf.font-path 指向 .ttf/.ttc 文件"
                    + "（Windows 可用 C:/Windows/Fonts/simhei.ttf，Docker 镜像已内置 /usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf），"
                    + "或改用浏览器 A4 打印另存为 PDF。");
        } else {
            builder.message("PDF 可用，已加载字体：" + loadedName);
        }
        return builder.build();
    }

    /**
     * 获取字体（不可用时抛业务异常）。
     */
    public BaseFont baseFont() {
        BaseFont font = resolve();
        if (font == null) {
            throw new BizException(ResultCode.PDF_FONT_MISSING, capability().getMessage());
        }
        return font;
    }

    /**
     * 构造指定字号的字体。
     *
     * @param size  字号（磅）
     * @param style {@link Font#NORMAL} / {@link Font#BOLD} 等
     */
    public Font font(float size, int style) {
        return new Font(baseFont(), size, style);
    }

    /**
     * 候选路径（配置项在最前，便于排查）。
     */
    public List<String> candidatePaths() {
        List<String> candidates = new ArrayList<>();
        if (!configuredFontPath.isEmpty()) {
            candidates.add(configuredFontPath);
        }
        candidates.addAll(SYSTEM_CANDIDATES);
        for (String resource : RESOURCE_CANDIDATES) {
            candidates.add("classpath:" + resource);
        }
        return candidates;
    }

    private BaseFont resolve() {
        if (resolved) {
            return baseFont;
        }
        synchronized (this) {
            if (resolved) {
                return baseFont;
            }
            for (String candidate : candidatePaths()) {
                attempted.add(candidate);
                BaseFont loaded = load(candidate);
                if (loaded != null) {
                    baseFont = loaded;
                    loadedPath = candidate;
                    loadedName = safeFontName(loaded, candidate);
                    resolved = true;
                    log.info("PDF 中文字体已加载：{}", candidate);
                    return baseFont;
                }
            }
            resolved = true;
            log.warn("未找到可嵌入的中文字体，PDF 导出不可用；已尝试 {} 个候选路径", attempted.size());
            return null;
        }
    }

    /**
     * 加载单个候选路径：文件不存在或不是有效字体时返回 null（不抛异常）。
     */
    private BaseFont load(String candidate) {
        try {
            if (candidate.startsWith("classpath:")) {
                String resource = candidate.substring("classpath:".length());
                if (PdfFontProvider.class.getClassLoader().getResource(resource) == null) {
                    return null;
                }
                return BaseFont.createFont(resource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
            String filePath = candidate;
            int comma = candidate.lastIndexOf(',');
            if (comma > 0) {
                filePath = candidate.substring(0, comma);
            }
            File file = new File(filePath);
            if (!file.isFile() || file.length() == 0L) {
                return null;
            }
            return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (DocumentException | IOException | RuntimeException ex) {
            log.debug("字体候选不可用：{}（{}）", candidate, ex.getMessage());
            return null;
        }
    }

    private String safeFontName(BaseFont font, String candidate) {
        try {
            String[][] families = font.getFullFontName();
            if (families != null && families.length > 0 && families[0].length > 3) {
                return families[0][3];
            }
        } catch (RuntimeException ignored) {
            // 部分字体不返回名称信息，忽略
        }
        return candidate;
    }
}
