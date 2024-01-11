package com.person.mryx.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.person.mryx.enums.PaymentType;
import com.person.mryx.model.order.PaymentInfo;

import java.util.Map;

public interface PaymentInfoService extends IService<PaymentInfo> {
    /**
     * 保存交易记录
     * @param orderNo
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    PaymentInfo savePaymentInfo(String orderNo, PaymentType paymentType);

    PaymentInfo getPaymentInfo(String orderNo, PaymentType paymentType);

    //支付成功
    void paySuccess(String orderNo,PaymentType paymentType, Map<String,String> paramMap);
}
