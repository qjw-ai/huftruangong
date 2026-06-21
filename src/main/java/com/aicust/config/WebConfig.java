package com.aicust.config;

import com.aicust.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final ThreadPoolTaskExecutor taskExecutor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor, ThreadPoolTaskExecutor taskExecutor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(taskExecutor);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**") // 拦截所有 API
                // 👇✅ 关键修复：排除 auth 相关接口，防止死循环
                .excludePathPatterns(
                        "/api/auth/**",      // 排除登录、验证码
                        "/api/auth/captcha", // 显式写出来（双重保险）
                        "/error",            // 排除错误页
                        "/static/**"         // 排除静态资源
                );
    }
}
