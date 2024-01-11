package com.person.mryx.payment.controller;


import com.person.mryx.common.result.Result;
import com.person.mryx.common.result.ResultCodeEnum;
import com.person.mryx.enums.PaymentType;
import com.person.mryx.payment.service.PaymentInfoService;
import com.person.mryx.payment.service.WeiXinService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@ApiOperation("订单支付")
@RestController
@RequestMapping("/api/payment/weixin")
public class WeiXinController {

    @Autowired
    private WeiXinService weixinPayService;

    @Autowired
    private PaymentInfoService paymentInfoService;

    @ApiOperation(value = "下单 小程序支付")
    @GetMapping("/createJsapi/{orderNo}")
    public Result createJsapi(
            @ApiParam(name = "orderNo", value = "订单No", required = true)
            @PathVariable("orderNo") String orderNo) {
        return Result.ok(weixinPayService.createJsapi(orderNo));
    }

    @ApiOperation(value = "查询支付状态")
    @GetMapping("/queryPayStatus/{orderNo}")
    public Result queryPayStatus(
            @ApiParam(name = "orderNo", value = "订单No", required = true)
            @PathVariable("orderNo") String orderNo) {
        //调用查询接口
        Map<String, String> resultMap = weixinPayService.queryPayStatus(orderNo, PaymentType.WEIXIN.name());
        log.info("resultMap={}",resultMap);
        if (resultMap == null) {//出错
            return Result.fail("支付出错");
        }
        if ("SUCCESS".equals(resultMap.get("trade_state"))) {//如果成功
            //更改订单状态，处理支付结果
            String out_trade_no = resultMap.get("out_trade_no");
            paymentInfoService.paySuccess(out_trade_no, PaymentType.WEIXIN, resultMap);
            return Result.ok("支付成功");
        }
        return Result.build(null, ResultCodeEnum.PAYMENT_WAITING);
    }
}
