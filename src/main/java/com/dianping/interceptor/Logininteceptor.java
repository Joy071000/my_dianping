package com.dianping.interceptor;

import com.dianping.dto.UserDTO;
import com.dianping.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class Logininteceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        HttpSession session = request.getSession();
//        Object user = session.getAttribute("user");
//        if(user == null) {
//            response.setStatus(401);
//            return false;
//        }
//        UserHolder.saveUser((UserDTO) user);
//        return true;
        //获取用户
        if(UserHolder.getUser() == null) {
            //不存在用户，拦截
            response.setStatus(401);
            return false;
        }
        //存在用户放行
        return true;
    }

}
