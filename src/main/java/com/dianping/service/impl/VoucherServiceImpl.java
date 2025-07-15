package com.dianping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.dto.Result;
import com.dianping.entity.SeckillVoucher;
import com.dianping.entity.Voucher;
import com.dianping.mapper.VoucherMapper;
import com.dianping.service.ISeckillVoucherService;
import com.dianping.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.dianping.utils.RedisConstants.SECKILL_STOCK_KEY;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        //保存优惠券
        save(voucher);
        //保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        //保存秒杀库存到Redis中
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
    }

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);

        return Result.ok(vouchers);
    }
}
