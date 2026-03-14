package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// @Component 没用，扫描不到
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 判断是否需要做拦截（ThreadLocal里是否有用户）
        if(UserHolder.getUser() == null){
            // 没有，需要拦截，设置拦截状态
            response.setStatus(401);
            // 拦截
            return false;
        }
        // 有用户则放行
        return true;
    }

}
