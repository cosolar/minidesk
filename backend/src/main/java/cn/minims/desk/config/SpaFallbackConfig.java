package cn.minims.desk.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 单 Jar 模式下的前端路由转发。
 *
 * <p>只在 classpath 里真的有 static/index.html 时生效，因此前后端分离开发
 * （Vite dev server / preview）完全不受影响。这里列出具体路由而不是 /** 兜底，
 * 是为了避免把 /api/** 的 404 和带扩展名的静态资源误转发成首页。
 */
@Configuration
@ConditionalOnResource(resources = "classpath:static/index.html")
public class SpaFallbackConfig implements WebMvcConfigurer {

    private static final String[] SPA_ROUTES = {
            "/login", "/register", "/403", "/todo", "/calendar", "/note",
            "/reminders", "/search", "/stats", "/profile", "/settings",
            "/console", "/console/**",
            "/work", "/work/**", "/study", "/study/**", "/life", "/life/**",
    };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String route : SPA_ROUTES) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
