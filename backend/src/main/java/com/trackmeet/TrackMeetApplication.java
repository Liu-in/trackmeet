package com.trackmeet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 田径运动会编排软件 - 启动类
 *
 * @author TrackMeet
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
// 说明：一旦存在显式 @MapperScan，MyBatis 的「按 @Mapper 注解自动注册」便会关闭，
// 因此这里扫描整个 modules 根包（递归包含各业务模块的 mapper 子包），
// 避免新增模块时因遗漏扫描包导致 Mapper 注入失败。
@MapperScan("com.trackmeet.modules")
public class TrackMeetApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackMeetApplication.class, args);
    }
}
