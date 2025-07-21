package com.dianping.controller;

import com.dianping.dto.Result;
import com.dianping.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId){
        return voucherOrderService.seckillVoucher(voucherId);
    }

}
