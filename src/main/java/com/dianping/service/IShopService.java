package com.dianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.dto.Result;
import com.dianping.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result update(Shop shop);

    Result queryById(Long id);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
