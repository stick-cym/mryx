package com.person.mryx.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.person.mryx.common.exception.MryxException;
import com.person.mryx.common.result.ResultCodeEnum;
import com.person.mryx.enums.PaymentStatus;
import com.person.mryx.enums.PaymentType;
import com.person.mryx.model.order.OrderInfo;
import com.person.mryx.model.order.PaymentInfo;
import com.person.mryx.mq.constant.MqConst;
import com.person.mryx.mq.service.RabbitService;
import com.person.mryx.order.client.OrderFeignClient;
import com.person.mryx.payment.mapper.PaymentInfoMapper;
import com.person.mryx.payment.service.PaymentInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Service
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {
    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private RabbitService rabbitService;
    @Override
    public PaymentInfo savePaymentInfo(String orderNo, PaymentType paymentType) {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderNo);
        if(orderInfo==null){
            throw new MryxException(ResultCodeEnum.DATA_ERROR);
        }
        PaymentInfo paymentInfo=new PaymentInfo();
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setUserId(orderInfo.getUserId());
        paymentInfo.setOrderNo(orderInfo.getOrderNo());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID);
        String subject = "test";
        paymentInfo.setSubject(subject);
//        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setTotalAmount(new BigDecimal("0.01"));

        baseMapper.insert(paymentInfo);
        return paymentInfo;
    }

    @Override
    public PaymentInfo getPaymentInfo(String orderNo, PaymentType paymentType) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>()
                .eq(PaymentInfo::getOrderNo,orderNo));
    }

    //支付成功后,需修改支付记录表状态:未支付->已支付,修改订单记录表状态:未支付->已支付,并且修改商品库存
    @Override
    public void paySuccess(String orderNo, PaymentType paymentType, Map<String, String> paramMap) {
        PaymentInfo paymentInfo = baseMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>()
                .eq(PaymentInfo::getOrderNo, orderNo).eq(PaymentInfo::getPaymentType, paymentType));
        if(paymentInfo.getPaymentStatus()!=PaymentStatus.UNPAID){
            return;
        }
        paymentInfo.setPaymentStatus(PaymentStatus.PAID);
        paymentInfo.setTradeNo(paramMap.get("transaction_id"));
        paymentInfo.setCallbackContent(paramMap.toString());
        baseMapper.updateById(paymentInfo);
        //发送消息
        rabbitService.sendMessage(MqConst.EXCHANGE_PAY_DIRECT,MqConst.ROUTING_PAY_SUCCESS,orderNo);
    }
}
