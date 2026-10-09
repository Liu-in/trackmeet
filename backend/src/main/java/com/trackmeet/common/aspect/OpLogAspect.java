package com.trackmeet.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.util.RequestUtils;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.system.entity.SysOperationLog;
import com.trackmeet.modules.system.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：记录模块、动作、参数摘要、耗时、结果与操作人。
 * 日志写入失败不影响业务主流程。
 *
 * @author TrackMeet
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            error = throwable;
            throw throwable;
        } finally {
            try {
                saveLog(joinPoint, opLog, error, System.currentTimeMillis() - start);
            } catch (Exception ex) {
                log.warn("写入操作日志失败: {}", ex.getMessage());
            }
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OpLog opLog, Throwable error, long costMs) {
        HttpServletRequest request = currentRequest();
        SysOperationLog entity = new SysOperationLog();
        entity.setUserId(SecurityUtils.getUserId());
        entity.setUsername(SecurityUtils.getUsername());
        entity.setModule(opLog.module());
        entity.setAction(opLog.action());
        entity.setActionName(opLog.actionName());
        entity.setTargetType(opLog.targetType());
        entity.setTargetId(resolveTargetId(joinPoint.getArgs()));
        entity.setParamsSummary(opLog.recordParams() ? buildParamsSummary(joinPoint.getArgs()) : null);
        entity.setCostMs((int) costMs);
        entity.setResult(error == null ? "SUCCESS" : "FAIL");
        entity.setErrorMsg(error == null ? null : RequestUtils.truncate(error.getMessage(), 2000));
        entity.setOperateTime(LocalDateTime.now());
        entity.setIsArchived(0);
        if (request != null) {
            entity.setRequestId(RequestUtils.getRequestId(request));
            entity.setIp(RequestUtils.getClientIp(request));
            String ua = RequestUtils.getUserAgent(request);
            entity.setUa(ua);
            entity.setOs(RequestUtils.getOs(ua));
            entity.setBrowser(RequestUtils.getBrowser(ua));
        }
        operationLogMapper.insert(entity);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 取第一个 Long 型参数作为目标 ID（约定：新增/修改的 DTO 含 id，或路径变量 id）。
     */
    private Long resolveTargetId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long value) {
                return value;
            }
        }
        return null;
    }

    private String buildParamsSummary(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> serializable = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null
                    || arg instanceof MultipartFile
                    || arg instanceof jakarta.servlet.ServletRequest
                    || arg instanceof jakarta.servlet.ServletResponse) {
                continue;
            }
            serializable.add(arg);
        }
        if (serializable.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(serializable);
            return RequestUtils.truncate(json, 500);
        } catch (Exception ex) {
            return RequestUtils.truncate(String.valueOf(serializable), 500);
        }
    }
}
