package com.hmdp.config;

import com.hmdp.log.TraceIdInterceptor;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.Objects;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // 由Spring进行构建的类可以做依赖注入

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TraceIdInterceptor traceIdInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // 链路 traceId 最先执行，保证后续拦截器、Controller、AOP 均可从 MDC 取值
        if (traceIdInterceptor == null) {
            throw new IllegalArgumentException("traceIdInterceptor cannot be null");
        }
        registry.addInterceptor(traceIdInterceptor).addPathPatterns("/**").order(Ordered.HIGHEST_PRECEDENCE);
        // 用order设定执行先后顺序，order数值越小越先执行，默认按照添加顺序执行
        // 默认拦截所有请求，token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/voucher/**",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**"
                ).order(1);

        WebMvcConfigurer.super.addInterceptors(registry);
    }
}
