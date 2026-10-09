package com.trackmeet.config;

import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SqlScriptSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 首次启动自动建库与初始化数据（供 EXE 安装包「一键安装」使用）。
 *
 * <p>启用方式（默认关闭，不影响 Docker/开发环境）：</p>
 * <pre>
 * java -jar trackmeet-backend.jar --trackmeet.init-db=true --trackmeet.init-scripts-dir=sql
 * </pre>
 *
 * <p>行为：</p>
 * <ol>
 *   <li>按「服务器地址」直连 MySQL（不指定库名），凭据取 {@code spring.datasource.username/password}；</li>
 *   <li>建立 {@code sys_schema_history} 记录表，已执行过的脚本自动跳过（**可重复执行**）；</li>
 *   <li>按文件名顺序执行脚本目录下的 {@code *.sql}（脚本内的 {@code CREATE DATABASE} / {@code USE} 原样执行）；</li>
 *   <li>{@code trackmeet.init-db-skip-demo=true} 时跳过演示/验收数据（{@code 06-*}、{@code 10-*}）。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "trackmeet.init-db", havingValue = "true")
public class DatabaseInitializer implements ApplicationRunner {

    private static final String HISTORY_TABLE = "sys_schema_history";
    private static final List<String> DEMO_PREFIXES = Arrays.asList("06-", "10-");

    @Value("${trackmeet.init-scripts-dir:sql}")
    private String scriptsDir;

    @Value("${trackmeet.init-db-host:${DB_HOST:localhost}}")
    private String host;

    @Value("${trackmeet.init-db-port:${DB_PORT:3306}}")
    private int port;

    @Value("${spring.datasource.username:trackmeet}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${trackmeet.init-db-skip-demo:false}")
    private boolean skipDemo;

    @Value("${trackmeet.init-db-fail-fast:true}")
    private boolean failFast;

    @Override
    public void run(ApplicationArguments args) {
        File directory = new File(scriptsDir);
        if (!directory.isDirectory()) {
            String message = "数据库初始化脚本目录不存在：" + directory.getAbsolutePath()
                    + "（可用 --trackmeet.init-scripts-dir 指定）";
            if (failFast) {
                throw new BizException(ResultCode.SYSTEM_ERROR, message);
            }
            log.warn(message);
            return;
        }
        List<File> scripts = collectScripts(directory);
        if (scripts.isEmpty()) {
            log.warn("数据库初始化脚本目录为空：{}", directory.getAbsolutePath());
            return;
        }

        log.info("开始数据库初始化：{} 个脚本，目录 {}", scripts.size(), directory.getAbsolutePath());
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password)) {
            prepareHistoryTable(connection);
            int executed = 0;
            int skipped = 0;
            for (File script : scripts) {
                String name = script.getName();
                if (skipDemo && isDemoScript(name)) {
                    log.info("跳过演示数据脚本：{}", name);
                    skipped++;
                    continue;
                }
                if (isApplied(connection, name)) {
                    log.info("跳过已执行脚本：{}", name);
                    skipped++;
                    continue;
                }
                executeScript(connection, script);
                markApplied(connection, name);
                executed++;
            }
            log.info("数据库初始化完成：执行 {} 个、跳过 {} 个", executed, skipped);
        } catch (SQLException ex) {
            String message = "数据库初始化失败：" + ex.getMessage() + "（请检查 MySQL 是否已启动、账号口令与端口是否正确）";
            if (failFast) {
                throw new BizException(ResultCode.SYSTEM_ERROR, message);
            }
            log.error(message, ex);
        }
    }

    private String serverUrl() {
        return String.format(
                "jdbc:mysql://%s:%d/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=true",
                host, port);
    }

    private List<File> collectScripts(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> scripts = new ArrayList<>(Arrays.asList(files));
        scripts.sort((first, second) -> first.getName().compareToIgnoreCase(second.getName()));
        return scripts;
    }

    private boolean isDemoScript(String name) {
        for (String prefix : DEMO_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void prepareHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `" + HISTORY_TABLE + "` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                    + "`script_name` VARCHAR(200) NOT NULL,"
                    + "`applied_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (`id`), UNIQUE KEY `uk_script_name` (`script_name`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库初始化脚本执行记录'");
        }
    }

    private boolean isApplied(Connection connection, String name) {
        // 记录表位于目标库中：库或表尚不存在时视为未执行
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM `" + HISTORY_TABLE + "` WHERE `script_name` = ? LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    private void markApplied(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO `" + HISTORY_TABLE + "` (`script_name`) VALUES (?)")) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException ex) {
            log.warn("写入脚本执行记录失败（忽略）：{} - {}", name, ex.getMessage());
        }
    }

    private void executeScript(Connection connection, File script) throws SQLException {
        String content;
        try {
            content = new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "读取脚本失败：" + script.getName() + " - " + ex.getMessage());
        }
        List<String> statements = SqlScriptSplitter.split(stripBom(content));
        log.info("执行脚本 {}（{} 条语句）", script.getName(), statements.size());
        try (Statement statement = connection.createStatement()) {
            int index = 0;
            for (String sql : statements) {
                index++;
                try {
                    statement.execute(sql);
                } catch (SQLException ex) {
                    throw new SQLException("脚本 " + script.getName() + " 第 " + index + " 条语句失败："
                            + ex.getMessage() + " | SQL 片段：" + abbreviate(sql), ex);
                }
            }
        }
    }

    private String stripBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private String abbreviate(String sql) {
        String single = sql.replaceAll("\\s+", " ").trim();
        return single.length() <= 120 ? single : single.substring(0, 120) + "...";
    }

    /**
     * 供诊断使用：当前生效的脚本目录。
     */
    public String getScriptsDir() {
        File directory = new File(scriptsDir);
        try {
            return directory.getCanonicalPath();
        } catch (Exception ex) {
            return directory.getAbsolutePath();
        }
    }
}
