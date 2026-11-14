package cn.minims.desk.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 全局登录校验。
 * 登录判断集中在路由层，业务方法不再逐个 checkLogin。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/register-config",
            "/api/auth/email-code",
            "/api/auth/register-email",
            "/api/site",
            "/api/site/branding/*",
            "/api/share/note/**",
            "/api/ping",
            "/api/files/*/raw"
    };

    /**
     * JWT 无状态模式：token 自带有效期与 loginId，服务端不存会话，重启不掉登录。
     * 代价是无法在服务端强制下线（个人工具可接受）。
     */
    @Bean
    public StpLogic stpLogicJwt() {
        return new StpLogicJwtForStateless();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter.match("/api/**")
                        .notMatch(WHITE_LIST)
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/**");
    }
}
