package cn.minims.desk.framework.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 把 Spring 容器暴露给非容器管理的对象。
 *
 * <p>Quartz 自己实例化 Job，无法直接注入 Service；这里只用于 Job 内部取回执行器，
 * 业务代码禁止用它绕过依赖注入。
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static <T> T bean(Class<T> type) {
        ApplicationContext current = context;
        if (current == null) {
            throw new IllegalStateException("application context is not ready");
        }
        return current.getBean(type);
    }
}
