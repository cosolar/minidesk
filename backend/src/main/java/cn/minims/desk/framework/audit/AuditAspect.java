package cn.minims.desk.framework.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import cn.minims.desk.framework.tenant.TenantContext;

/**
 * 写操作审计切面：所有 Controller 的 POST / PUT / PATCH / DELETE 自动落 sys_oper_log。
 *
 * <p>参数会按字段名脱敏后再存，密码、令牌、API Key、验证码一律替换成 ***；
 * 审计写入失败只告警，绝不影响业务请求。日志只增不改。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "password", "passwd", "pwd", "token", "secret", "apikey", "key", "code",
            "credential", "authorization", "accesskey", "privatekey", "hash", "signature");
    private static final Set<RequestMethod> WRITE_METHODS = Set.of(
            RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditAspect(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Around("execution(* cn.minims.desk.modules..controller..*.*(..))")
    public Object record(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        AuditLog audit = AnnotatedElementUtils.findMergedAnnotation(method, AuditLog.class);
        if (!isWrite(mapping) && audit == null) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        long begin = System.currentTimeMillis();
        String result = "success";
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            result = "failed";
            errorMsg = truncate(safeMessage(ex), 500);
            throw ex;
        } finally {
            long cost = System.currentTimeMillis() - begin;
            try {
                insert(method, mapping, audit, args, result, errorMsg, cost);
            } catch (RuntimeException ex) {
                log.warn("write audit log failed, method={}", method.getName(), ex);
            }
        }
    }

    private void insert(Method method, RequestMapping mapping, AuditLog audit, Object[] args,
                        String result, String errorMsg, long cost) {
        Long userId = TenantContext.currentUserIdOrNull();
        String username = userId == null ? guessUsername(args) : loadUsername(userId);
        HttpServletRequest request = currentRequest();
        Class<?> controller = method.getDeclaringClass();
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String path = classMapping != null && classMapping.value().length > 0
                ? classMapping.value()[0] : controller.getSimpleName();
        String module = audit != null && StringUtils.hasText(audit.module())
                ? audit.module() : moduleOf(path, method);
        String action = audit != null && StringUtils.hasText(audit.action())
                ? audit.action() : actionOf(mapping, method);
        String targetType = audit == null ? "" : audit.targetType();
        String targetId = findTargetId(method, args);
        String params = audit != null && audit.ignoreParams()
                ? null : truncate(describe(method, args), 2000);

        jdbc.update("""
                        insert into sys_oper_log
                            (user_id, username, module, action, target_type, target_id, params,
                             result, error_msg, ip, user_agent, cost_ms, trace_id, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId == null ? 0L : userId, username, truncate(module, 32), truncate(action, 32),
                StringUtils.hasText(targetType) ? truncate(targetType, 32) : null,
                targetId, params, result, errorMsg,
                request == null ? null : truncate(clientIp(request), 64),
                request == null ? null : truncate(request.getHeader("User-Agent"), 500),
                cost, MDC.get("traceId"), LocalDateTime.now().format(DATETIME));
    }

    private boolean isWrite(RequestMapping mapping) {
        if (mapping == null) {
            return false;
        }
        return Arrays.stream(mapping.method()).anyMatch(WRITE_METHODS::contains);
    }

    private String moduleOf(String path, Method method) {
        String clean = path.replaceFirst("^/api/", "").replaceAll("^/+|/+$", "");
        String[] parts = clean.split("/");
        String prefix = parts.length == 0 || parts[0].isBlank()
                ? method.getDeclaringClass().getSimpleName() : parts[0];
        return parts.length > 1 && !parts[1].isBlank() && !parts[1].contains("{")
                ? prefix + "-" + parts[1] : prefix;
    }

    private String actionOf(RequestMapping mapping, Method method) {
        String name = method.getName().replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
        if (name.contains("reset") || name.contains("trigger") || name.contains("restore")
                || name.contains("logout") || name.contains("register")) {
            return name;
        }
        if (mapping != null) {
            for (RequestMethod item : mapping.method()) {
                return switch (item) {
                    case POST -> "create";
                    case PUT, PATCH -> "update";
                    case DELETE -> "delete";
                    default -> name;
                };
            }
        }
        return name;
    }

    private String describe(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        StringBuilder text = new StringBuilder(128);
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            Object value = args[i];
            if (value == null || isSkipped(value)) {
                continue;
            }
            String name = parameters[i].getName();
            if (isSensitive(name)) {
                append(text, name, "***");
                continue;
            }
            if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                    || value instanceof Enum<?> || value instanceof Temporal) {
                append(text, name, String.valueOf(value));
                continue;
            }
            try {
                JsonNode node = objectMapper.valueToTree(value);
                mask(node);
                append(text, name, truncate(objectMapper.writeValueAsString(node), 800));
            } catch (Exception ex) {
                append(text, name, "[unserializable]");
            }
        }
        return text.length() == 0 ? null : text.toString();
    }

    private void mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey())) {
                    entry.setValue(TextNode.valueOf("***"));
                } else {
                    mask(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode array) {
            for (JsonNode item : array) {
                mask(item);
            }
        }
    }

    private boolean isSensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (String word : SENSITIVE_WORDS) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSkipped(Object value) {
        return value instanceof HttpServletRequest || value instanceof HttpServletResponse
                || value instanceof MultipartFile || value instanceof MultipartFile[]
                || value instanceof org.springframework.validation.BindingResult;
    }

    private String findTargetId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
            if (pathVariable != null && args[i] != null) {
                return truncate(String.valueOf(args[i]), 64);
            }
            if (args[i] instanceof Map<?, ?> map && map.containsKey("id")) {
                return truncate(String.valueOf(map.get("id")), 64);
            }
        }
        return null;
    }

    private String guessUsername(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Map<?, ?> map) {
                for (String key : new String[]{"username", "email"}) {
                    if (map.get(key) instanceof String value && StringUtils.hasText(value)) {
                        return truncate(value, 64);
                    }
                }
            }
        }
        return "anonymous";
    }

    private String loadUsername(Long userId) {
        try {
            String name = jdbc.queryForObject("select username from sys_user where id = ?",
                    String.class, userId);
            return StringUtils.hasText(name) ? name : "unknown";
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void append(StringBuilder text, String name, String value) {
        if (text.length() > 0) {
            text.append(", ");
        }
        text.append(name).append('=').append(value);
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
