package com.aicust.config;

import com.aicust.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity          // ← 显式开启（推荐）
@EnableMethodSecurity(prePostEnabled = true)  // ← 关键！支持 @PreAuthorize 在 ToolService 使用
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                // 1. 禁用 CSRF（JWT 无状态）
                .csrf(csrf -> csrf.disable())

                // 2. 无状态 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. 拦截规则
                .authorizeHttpRequests(auth -> auth
                        // SSE 异步 dispatch 已经在初始请求完成鉴权，二次 dispatch 放行避免响应已提交后误判 403
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(
                                "/", "/index.html", "/favicon.ico",
                                "/admin.html",
                                "/static/**", "/css/**", "/js/**", "/images/**",
                                "/api/auth/**",
                                "/api/digital-human/active",
                                "/error"
                        ).permitAll()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .requestMatchers(
                                "/tool/**",
                                "/api/tool/**",
                                "/api/tools/**"
                        ).authenticated()

                        // 聊天接口显式声明，确保 SSE 异步dispatch 不丢认证
                        .requestMatchers("/api/chat", "/api/agent", "/api/speech/**").authenticated()

                        // 管理后台接口
                        .requestMatchers("/api/admin/**").authenticated()

                        .anyRequest().authenticated()
                )

                // 4. JWT 过滤器（保持不变）
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // 5. 异常友好提示
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> res.sendError(401, "未登录，请先登录"))
                        .accessDeniedHandler((req, res, e) -> res.sendError(403, "权限不足"))
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}