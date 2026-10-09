package com.trackmeet.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackmeet.modules.system.entity.SysUser;
import com.trackmeet.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 占位口令初始化：
 * 初始化脚本（05-init-data.sql / 06-demo-data.sql）中的账号口令为占位符 INIT_PLACEHOLDER，
 * 首次启动时由本类统一用 BCrypt 加密写入默认口令，避免在 SQL 中硬编码哈希值。
 * 幂等：口令已初始化过的账号会被跳过（查询条件即「口令 = 占位符」）。
 * 生产环境：5-init 脚本中的 admin 亦会被初始化，请首次登录后立即修改口令。
 *
 * @author TrackMeet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAccountInitializer implements ApplicationRunner {

    /** 与 sql/05-init-data.sql、sql/06-demo-data.sql 中的占位符保持一致 */
    private static final String PASSWORD_PLACEHOLDER = "INIT_PLACEHOLDER";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${trackmeet.default-password:Admin@123}")
    private String defaultPassword;

    @Override
    public void run(ApplicationArguments args) {
        try {
            LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
            query.eq(SysUser::getPassword, PASSWORD_PLACEHOLDER);
            List<SysUser> users = userMapper.selectList(query);
            if (users == null || users.isEmpty()) {
                return;
            }
            String encoded = passwordEncoder.encode(defaultPassword);
            for (SysUser user : users) {
                LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<>();
                update.eq(SysUser::getId, user.getId())
                        .set(SysUser::getPassword, encoded)
                        .set(SysUser::getPwdUpdateTime, LocalDateTime.now())
                        .set(SysUser::getForcePwdChange, 1)
                        .set(SysUser::getPwdErrorCount, 0)
                        .set(SysUser::getLockUntil, null);
                userMapper.update(null, update);
                log.warn("账号 [{}] 的口令已初始化（默认口令见 trackmeet.default-password），请首次登录后立即修改", user.getUsername());
            }
            log.warn("本次共初始化 {} 个账号的初始口令", users.size());
        } catch (Exception ex) {
            log.warn("初始口令初始化跳过（可能尚未执行建表脚本）: {}", ex.getMessage());
        }
    }
}
