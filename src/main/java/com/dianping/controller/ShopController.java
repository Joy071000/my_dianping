package com.dianping.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.dto.Result;
import com.dianping.entity.Shop;
import com.dianping.service.IShopService;
import com.dianping.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop")
public class ShopController {
    @Resource
    public IShopService shopService;

    /**
     * 根据商户id查商户信息
     * @param id 商铺id
     * @return 商铺信息
     */
    @GetMapping("/{id}")
    public Result getShopById(@PathVariable("id") Long id){
        return shopService.queryById(id);
    }

//    /**
//     * 新增商铺信息
//     * @param shop 商铺数据
//     * @return 商铺id
//     */
//    @PostMapping
//    public Result saveShop(@RequestBody Shop shop){
//        shopService.save(shop);
//        return Result.ok(shop.getId());
//    }
//
    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PostMapping("/update")
    public Result updateShop(@RequestBody Shop shop){
        //写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分野查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ){
        return shopService.queryShopByType(typeId, current, x, y);
    }


    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        //根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StringUtils.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //返回数据
        return Result.ok(page.getRecords());
    }
}
