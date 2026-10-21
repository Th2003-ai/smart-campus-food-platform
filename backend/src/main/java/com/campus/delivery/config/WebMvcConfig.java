package com.campus.delivery.config;

import com.campus.delivery.security.AuthInterceptor;
import com.campus.delivery.security.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 层配置：拦截器、跨域、静态资源（上传文件与接口文档）。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${campus.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${campus.file.local-path:./uploads}")
    private String localFilePath;

    /** 公开接口白名单：无需登录即可访问 */
    private static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/auth/register",
            "/shops/**",
            "/dishes/**",
            "/rank/**",
            // 注意：不要在这里加 "/reviews"。excludePathPatterns 只能按路径排除，
            // 加了会把 POST /reviews（学生提交评价）也放行，导致控制器拿不到登录态而抛 401。
            // 「评价列表公开、提交需登录」由 AuthInterceptor#PUBLIC_GET_PATHS 按方法处理。
            "/error",
            "/static/**",
            "/ws/**",
            // 接口文档
            "/doc.html",
            "/webjars/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 顺序很重要：先鉴权（写入 UserContext），再限流（按用户ID计数时要用到）
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_PATHS)
                .order(1);
        // 限流对全路径生效（包括登录这类公开接口），具体是否限流由 @RateLimit 决定
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地存储的上传文件（生产环境改用对象存储后本段可移除）
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:" + ensureTrailingSlash(localFilePath));

        // Knife4j / springdoc 静态资源
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    private String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }
}
