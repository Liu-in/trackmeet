package com.trackmeet.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.trackmeet.common.context.MeetContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * MyBatis-Plus 配置：
 * 1. 多租户（meet_id）—— 业务表自动追加届次条件，系统表与全局表忽略；
 * 2. 分页；
 * 3. 乐观锁（version）；
 * 4. 全表更新/删除攻击防护。
 *
 * @author TrackMeet
 */
@Configuration
public class MybatisPlusConfig {

    /** 届次隔离列 */
    private static final String TENANT_COLUMN = "meet_id";

    /** 全局表（无 meet_id 列），多租户拦截时忽略 */
    private static final Set<String> GLOBAL_TABLES = Set.of(
            "meet",
            "referee_duty",
            "allround_score_table",
            "event_schedule_rule"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户：未指定届次上下文（请求未携带 X-Meet-Id）时不追加条件
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long meetId = MeetContext.get();
                return new LongValue(meetId == null ? 0L : meetId);
            }

            @Override
            public String getTenantIdColumn() {
                return TENANT_COLUMN;
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (!MeetContext.hasMeet()) {
                    return true;
                }
                if (tableName == null) {
                    return true;
                }
                String name = tableName.replace("`", "").trim().toLowerCase();
                if (name.startsWith("sys_")) {
                    return true;
                }
                return GLOBAL_TABLES.contains(name);
            }
        }));

        // 分页
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 防止全表更新与删除
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
