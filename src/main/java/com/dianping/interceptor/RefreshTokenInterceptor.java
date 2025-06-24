package com.dianping.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.dianping.dto.UserDTO;
import com.dianping.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dianping.utils.RedisConstants.LOGIN_USER_KEY;
import static com.dianping.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)) {
            return true;
        }
        //基于token获取redis的用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3.判断是否存在用户
        if(userMap.isEmpty()) {
            return true;
        }
        //4. 将查询到的用户数据保存到ThreadLocal
        UserDTO userTDO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userTDO);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清除强引用，防止内存泄漏
        UserHolder.removeUser();
    }
}
