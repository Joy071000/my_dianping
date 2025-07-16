package com.dianping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.dto.Result;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Follow;
import com.dianping.mapper.FollowMapper;
import com.dianping.service.IFollowService;
import com.dianping.service.IUserService;
import com.dianping.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result isFollow(Long followUserId) {
        // 1. 获取登陆用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询是否关注
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        // 3. 判断
        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取登陆用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 2. 判断到底是关注还是取关
        if(isFollow){
            // 3. 关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                // 把关注用户的id，放入redis的set集合
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }
        else{
            // 4. 取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if(isSuccess){
                // 把关注用户从redis移除
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followCommons(Long id) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        // 2. 求交集
        Set<String> intersection = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersection == null || intersection.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 3. 解析id集合
        List<Long> ids = intersection.stream().map(Long::valueOf).toList();
        // 4. 查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(users);
    }
}
