package com.trackmeet.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 脚本切分器单元测试（EXE 安装包首次启动自动建库依赖该切分逻辑）。
 *
 * @author TrackMeet
 */
class SqlScriptSplitterTest {

    @Test
    @DisplayName("按分号切分多条语句并丢弃空语句")
    void shouldSplitStatements() {
        List<String> statements = SqlScriptSplitter.split(
                "CREATE TABLE a (id INT);\n\nINSERT INTO a VALUES (1);\n   ;\nSELECT 1;");

        assertThat(statements).hasSize(3);
        assertThat(statements.get(0)).isEqualTo("CREATE TABLE a (id INT)");
        assertThat(statements.get(1)).isEqualTo("INSERT INTO a VALUES (1)");
        assertThat(statements.get(2)).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("字符串内的分号不切分（含转义与双写引号）")
    void shouldNotSplitInsideQuotes() {
        List<String> statements = SqlScriptSplitter.split(
                "INSERT INTO t VALUES ('a;b', \"c;d\", 'it''s;ok', 'esc\\';x');\nSELECT 2;");

        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).contains("'a;b'").contains("\"c;d\"").contains("'it''s;ok'");
        assertThat(statements.get(1)).isEqualTo("SELECT 2");
    }

    @Test
    @DisplayName("行注释与块注释中的分号被忽略，且注释内容不进入语句")
    void shouldIgnoreComments() {
        List<String> statements = SqlScriptSplitter.split(
                "-- 注释里有分号 ; 不应切分\n"
                        + "# 另一种注释 ; 同样忽略\n"
                        + "/* 块注释 ; 也忽略 */\n"
                        + "SET NAMES utf8mb4; -- 尾部注释 ;\n"
                        + "CREATE DATABASE IF NOT EXISTS `trackmeet`;\n"
                        + "USE `trackmeet`;");

        assertThat(statements).hasSize(3);
        assertThat(statements.get(0)).isEqualTo("SET NAMES utf8mb4");
        assertThat(statements.get(1)).isEqualTo("CREATE DATABASE IF NOT EXISTS `trackmeet`");
        assertThat(statements.get(2)).isEqualTo("USE `trackmeet`");
    }

    @Test
    @DisplayName("反引号标识符内的分号不切分")
    void shouldKeepBacktickIdentifier() {
        List<String> statements = SqlScriptSplitter.split("SELECT `weird;name` FROM `t;1`;");
        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).isEqualTo("SELECT `weird;name` FROM `t;1`");
    }

    @Test
    @DisplayName("末尾无分号的语句同样返回；BOM 由调用方剥离")
    void shouldReturnTrailingStatement() {
        List<String> statements = SqlScriptSplitter.split("SELECT 1;\nSELECT 2");
        assertThat(statements).containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("边界：null、空串、仅注释、仅空白均返回空列表")
    void shouldHandleBoundaryCases() {
        assertThat(SqlScriptSplitter.split(null)).isEmpty();
        assertThat(SqlScriptSplitter.split("")).isEmpty();
        assertThat(SqlScriptSplitter.split("   \n\t ")).isEmpty();
        assertThat(SqlScriptSplitter.split("-- 只有注释\n/* 还是注释 */")).isEmpty();
    }

    @Test
    @DisplayName("真实脚本片段：建表 + 注释 + JSON 值可正确切分")
    void shouldSplitRealisticScript() {
        String script = "-- 建表\n"
                + "CREATE TABLE `event` (\n"
                + "  `id` BIGINT NOT NULL,\n"
                + "  `remark` VARCHAR(200) NULL COMMENT '备注；含分号'\n"
                + ") ENGINE=InnoDB;\n"
                + "INSERT INTO `event` (`id`, `config_json`) VALUES (1, CAST('{\"a\":1}' AS JSON));\n";

        List<String> statements = SqlScriptSplitter.split(script);

        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).startsWith("CREATE TABLE `event`").contains("COMMENT '备注；含分号'");
        assertThat(statements.get(1)).startsWith("INSERT INTO `event`");
    }
}
