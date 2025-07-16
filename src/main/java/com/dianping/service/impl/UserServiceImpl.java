package com.dianping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.dto.LoginFormDTO;
import com.dianping.dto.Result;
import com.dianping.dto.UserDTO;
import com.dianping.entity.User;
import com.dianping.mapper.UserMapper;
import com.dianping.service.IUserService;
import com.dianping.utils.RegexUtils;
import com.dianping.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dianping.utils.RedisConstants.*;
import static com.dianping.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session){
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return  Result.fail("手机号格式错误！");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomString(6);
        //4.保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.保存验证码到session
        //session.setAttribute("code", code);
        //5.发送验证码,模拟发送
        log.debug("发送短信验证码成功：{}", code);
        //6.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回报错信息
            return Result.fail("手机号格式错误！");
        }

        //3.从session中获取验证码
//        Object cacheCode = session.getAttribute("code");
        //3.从redis获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(!code.equals(cacheCode)){
            //4.如果不一致则报错
            return Result.fail("验证码错误！");
        }
        //5.一致，根据手机号获取用户信息
        User user = query().eq("phone", phone).one();

        //6.判断用户是否存在
        if(user == null){
            //不存在则创建用户
            user = createUserWithPhone(phone);
        }

        //7.保存用户到redis
        //7.1 随机生成token，作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        log.info("token:{}", token);
        //7.2 将user转成HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //7.3 存储到redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.4 设置token的有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户到数据库
        save(user);
        return user;
    }
}
