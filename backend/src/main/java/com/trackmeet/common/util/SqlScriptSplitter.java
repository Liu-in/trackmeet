package com.trackmeet.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 脚本语句切分器（供安装包首次启动自动建库使用）。
 *
 * <p>按 MySQL 语法切分：</p>
 * <ul>
 *   <li>识别 {@code --}、{@code #} 行注释与 {@code /* *}{@code /} 块注释；</li>
 *   <li>识别单引号 / 双引号字符串（含 {@code \} 转义与 {@code ''} 双写转义）与反引号标识符；</li>
 *   <li>仅在「非注释、非字符串」状态下的分号处切分；</li>
 *   <li>丢弃空白语句，保留语句原文（首尾去空白），不改变语句内容。</li>
 * </ul>
 *
 * @author TrackMeet
 */
public final class SqlScriptSplitter {

    private SqlScriptSplitter() {
    }

    /**
     * 切分 SQL 脚本。
     *
     * @param script 脚本内容（可为 null）
     * @return 语句列表（不含空语句）
     */
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return statements;
        }
        StringBuilder current = new StringBuilder();
        int index = 0;
        int length = script.length();
        while (index < length) {
            char character = script.charAt(index);
            char next = index + 1 < length ? script.charAt(index + 1) : '\0';

            // 行注释：-- 或 #
            if ((character == '-' && next == '-') || character == '#') {
                int lineEnd = script.indexOf('\n', index);
                index = lineEnd < 0 ? length : lineEnd + 1;
                current.append('\n');
                continue;
            }
            // 块注释
            if (character == '/' && next == '*') {
                int end = script.indexOf("*/", index + 2);
                index = end < 0 ? length : end + 2;
                current.append(' ');
                continue;
            }
            // 字符串与标识符
            if (character == '\'' || character == '"' || character == '`') {
                int end = scanQuoted(script, index, character);
                current.append(script, index, end);
                index = end;
                continue;
            }
            // 语句分隔
            if (character == ';') {
                addStatement(statements, current);
                index++;
                continue;
            }
            current.append(character);
            index++;
        }
        addStatement(statements, current);
        return statements;
    }

    /**
     * 扫描带引号的片段，返回结束位置（不含结束引号之后）。
     */
    private static int scanQuoted(String script, int start, char quote) {
        int index = start + 1;
        int length = script.length();
        while (index < length) {
            char character = script.charAt(index);
            if (character == '\\' && quote != '`') {
                index += 2;
                continue;
            }
            if (character == quote) {
                // '' / "" 双写转义
                if (index + 1 < length && script.charAt(index + 1) == quote) {
                    index += 2;
                    continue;
                }
                return index + 1;
            }
            index++;
        }
        return length;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }
}
