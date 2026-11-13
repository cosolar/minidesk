package cn.minims.desk.framework.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计埋点。默认切面会自动记录所有写操作，需要更精确的模块名 / 动作名时才加此注解。
 *
 * <p>{@code ignoreParams = true} 用于密码、密钥等高敏接口：仍记录「谁在什么时候做了什么」，
 * 但不落任何请求参数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 模块名，如 console-user / task / backup；留空则按 Controller 路径推断 */
    String module() default "";

    /** 动作名，如 create / reset-password；留空则按 HTTP 方法推断 */
    String action() default "";

    /** 操作对象类型 */
    String targetType() default "";

    /** 完全不记录请求参数 */
    boolean ignoreParams() default false;
}
