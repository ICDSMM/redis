package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

// @Component 没用，扫描不到
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    // 只能使用构造函数注入，LoginInterceptor是手动创造不是Spring创造，没工具帮忙做依赖注入，无法使用@Autowired和@Resource注解，可以在生成LoginInterceptor的MvcConfig类里对stringRedisTemplate注入
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 2. 基于token获取redis中用户, entries 返回该键下所有值
            String key = LOGIN_USER_KEY + token;
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
            // 3. 判断用户是否存在
            if (!userMap.isEmpty()) {
                // 4.将查询到的hash数据转为UserDTO对象，并且不忽略强转错误
                UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
                // 5.存在，保存用户信息到ThreadLocal
                UserHolder.saveUser(userDTO);
                // 6.刷新token有效期
                stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
            }
            //  如果 token 无效（userMap 为空），则不保存用户，直接放行
        }
        // 无论有无 token，都放行（让请求继续到达 LoginInterceptor 或 Controller）
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 移除用户(线程)避免内存泄露
        UserHolder.removeUser();
    }
}
