package com.dianping.controller;


import cn.hutool.core.bean.BeanUtil;
import com.dianping.dto.LoginFormDTO;
import com.dianping.dto.Result;
import com.dianping.dto.UserDTO;
import com.dianping.entity.User;
import com.dianping.service.IUserInfoService;
import com.dianping.service.IUserService;
import com.dianping.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author linchaohai
 * @since 2025-6-23
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;

//    @Resource
//    private IUserInfoService userInfoService;

    /**
     * 发送短信验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session){
        //发送短信并保存验证码到session
        return userService.sendCode(phone, session);
    }

    /**
     * 登陆功能
     * @param loginFormDTO 登陆参数，包含手机号、密码；或者是手机号、验证码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFormDTO, HttpSession session){
        //实现登陆功能
        return userService.login(loginFormDTO, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        //TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        //获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

//    @GetMapping("/info/{id}")
//    public Result info(@PathVariable("id") Long userId){
//        //查询详情
//        User user = userService.getById(userId);
//        if(user == null){
//            return Result.ok();
//        }
//        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        return Result.ok(userDTO);
//    }
//
//    @GetMapping("/{id}")
//    public Result queryUserById(@PathVariable("id") Long userId){
//        User user = userService.getById(userId);
//        if(user == null){
//            return Result.ok();
//        }
//        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        return Result.ok(userDTO);
//    }

//    @PostMapping("/sign")
//    public Result sign(){
//        return userService.sign();
//    }
//
//    @GetMapping("/sign/count")
//    public Result signCount(){
//        return userService.signCount();
//    }
}
