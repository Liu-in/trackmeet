package com.trackmeet.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 富文本白名单净化器单元测试：覆盖公告/大屏最常见的存储型 XSS 注入向量。
 *
 * @author TrackMeet
 */
class HtmlSanitizerTest {

    @Test
    @DisplayName("script 标签连内容一起删除")
    void shouldRemoveScriptBlock() {
        assertThat(HtmlSanitizer.sanitize("<p>成绩公布</p><script>alert(1)</script>"))
                .isEqualTo("<p>成绩公布</p>");
        assertThat(HtmlSanitizer.sanitize("<script src=\"http://evil/x.js\"></script>")).isEmpty();
    }

    @Test
    @DisplayName("删除事件属性（onerror/onload/onclick）")
    void shouldRemoveEventAttributes() {
        assertThat(HtmlSanitizer.sanitize("<img src=x onerror=alert(1)>")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("<p onclick=\"steal()\">点击</p>")).isEqualTo("<p>点击</p>");
        assertThat(HtmlSanitizer.sanitize("<p ONMOUSEOVER='x()'>a</p>")).isEqualTo("<p>a</p>");
    }

    @Test
    @DisplayName("删除危险元素：iframe/svg/math/object/embed/form/input/style")
    void shouldRemoveDangerousElements() {
        assertThat(HtmlSanitizer.sanitize("<iframe src=\"http://evil\"></iframe>")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("<svg onload=alert(1)></svg>")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("<math><mtext>x</mtext></math>")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("<object data=\"x\"></object><embed src=\"y\">")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("<style>body{display:none}</style>正文")).isEqualTo("正文");
        assertThat(HtmlSanitizer.sanitize("<form action=\"/x\"><input name=\"a\"></form>")).isEmpty();
    }

    @Test
    @DisplayName("删除 javascript:/data: 协议链接，保留安全链接")
    void shouldStripUnsafeUrls() {
        assertThat(HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">点我</a>")).isEqualTo("<a>点我</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\"JaVaScRiPt:alert(1)\">x</a>")).isEqualTo("<a>x</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\"/notice/1\" title=\"详情\">详情</a>"))
                .isEqualTo("<a href=\"/notice/1\" title=\"详情\" rel=\"noopener noreferrer\">详情</a>");
    }

    @Test
    @DisplayName("保留白名单标签与纯文本")
    void shouldKeepAllowedTags() {
        String html = "<p>男子甲组 <strong>100 米</strong> 预赛</p><ul><li>记录 11\"23</li></ul><br/>";
        String sanitized = HtmlSanitizer.sanitize(html);
        assertThat(sanitized).contains("<strong>100 米</strong>").contains("<ul>").contains("<li>").contains("<br/>");
        assertThat(sanitized).doesNotContain("&lt;");
    }

    @Test
    @DisplayName("非白名单标签被丢弃但保留文本内容")
    void shouldDropUnknownTagsKeepText() {
        assertThat(HtmlSanitizer.sanitize("<marquee>公告</marquee>")).isEqualTo("公告");
        assertThat(HtmlSanitizer.sanitize("<custom-tag data-x=\"1\">内容</custom-tag>")).isEqualTo("内容");
    }

    @Test
    @DisplayName("拼接绕过：<scr<script>ipt> 反复净化后不再含可执行标签")
    void shouldDefeatNestedBypass() {
        String sanitized = HtmlSanitizer.sanitize("<scr<script>ipt>alert(1)</scr</script>ipt>");
        assertThat(sanitized).doesNotContain("<script");
        assertThat(sanitized).doesNotContain("<scr");
    }

    @Test
    @DisplayName("空值与空白：原样返回；净化后空白判定")
    void shouldHandleBlankInput() {
        assertThat(HtmlSanitizer.sanitize(null)).isNull();
        assertThat(HtmlSanitizer.sanitize("")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("   ")).isEqualTo("   ");
        assertThat(HtmlSanitizer.isBlankAfterSanitize("<p class=\"x\"> &nbsp; </p>")).isTrue();
        assertThat(HtmlSanitizer.isBlankAfterSanitize("<p>有内容</p>")).isFalse();
        assertThat(HtmlSanitizer.isBlankAfterSanitize("<script>alert(1)</script>")).isTrue();
    }

    @Test
    @DisplayName("保留普通中文正文与引号、尖括号文本")
    void shouldKeepPlainText() {
        assertThat(HtmlSanitizer.sanitize("成绩 11\"23，破赛会纪录！")).isEqualTo("成绩 11\"23，破赛会纪录！");
        assertThat(HtmlSanitizer.sanitize("<p>a &gt; b</p>")).isEqualTo("<p>a &gt; b</p>");
    }
}
