package com.trackmeet.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，由 {@code OpLogAspect} 写入 sys_operation_log。
 *
 * @author TrackMeet
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /** 业务模块，如 MEET、ATHLETE、SYSTEM */
    String module();

    /** 动作码，如 ADD、EDIT、DELETE、AUDIT */
    String action();

    /** 动作中文名，如 新增运动会 */
    String actionName() default "";

    /** 目标类型，如 meet、athlete */
    String targetType() default "";

    /** 是否记录请求参数摘要 */
    boolean recordParams() default true;
}
