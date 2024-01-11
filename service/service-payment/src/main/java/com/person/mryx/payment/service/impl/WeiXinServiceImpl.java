package com.person.mryx.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.wxpay.sdk.WXPayUtil;
import com.person.mryx.enums.PaymentType;
import com.person.mryx.model.order.PaymentInfo;
import com.person.mryx.payment.service.PaymentInfoService;
import com.person.mryx.payment.service.WeiXinService;
import com.person.mryx.payment.utils.ConstantPropertiesUtils;
import com.person.mryx.payment.utils.HttpClient;
import com.person.mryx.vo.user.UserLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WeiXinServiceImpl implements WeiXinService {
    @Autowired
    private PaymentInfoService paymentInfoService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Map<String, Object> createJsapi(String orderNo) {
        PaymentInfo paymentInfo = paymentInfoService.getPaymentInfo(orderNo, PaymentType.WEIXIN);
        if (paymentInfo == null) {
            paymentInfo = paymentInfoService.savePaymentInfo(orderNo, PaymentType.WEIXIN);
        }
        //向payment_info支付记录里添加记录,目前支付状态:正在支付

        //封装微信支付系统需要参数
        Map<String, String> paramMap = new HashMap();
        //1、设置参数
        paramMap.put("appid", ConstantPropertiesUtils.APPID);
        paramMap.put("mch_id", ConstantPropertiesUtils.PARTNER);
        paramMap.put("nonce_str", WXPayUtil.generateNonceStr());
        paramMap.put("body", paymentInfo.getSubject());
        paramMap.put("out_trade_no", paymentInfo.getOrderNo());
        int totalFee = paymentInfo.getTotalAmount().multiply(new BigDecimal(100)).intValue();
        paramMap.put("total_fee", String.valueOf(totalFee));
        paramMap.put("spbill_create_ip", "127.0.0.1");
        paramMap.put("notify_url", ConstantPropertiesUtils.NOTIFYURL);
        paramMap.put("trade_type", "JSAPI");
        UserLoginVo userLoginVo = (UserLoginVo) redisTemplate.opsForValue().get("user:login:" + paymentInfo.getUserId());
        if(null != userLoginVo && !StringUtils.isEmpty(userLoginVo.getOpenId())) {
            paramMap.put("openid", userLoginVo.getOpenId());
        } else {
            paramMap.put("openid", "oD7av4igt-00GI8PqsIlg5FROYnI");
        }
        //使用HttpClient调用微信支付系统
        HttpClient httpClient=new HttpClient("https://api.mch.weixin.qq.com/pay/unifiedorder");
        //设置参数,xml格式
        try {
            WXPayUtil.generateSignedXml(paramMap,ConstantPropertiesUtils.PARTNERKEY);
            httpClient.setHttps(true);
            httpClient.post();
            //得到返回结果
            String content = httpClient.getContent();
            Map<String, String> resultMap = WXPayUtil.xmlToMap(content);
            log.info("微信下单返回结果：{}", JSON.toJSONString(resultMap));
            //4、再次封装参数
            Map<String, String> parameterMap = new HashMap<>();
            String prepayId = String.valueOf(resultMap.get("prepay_id"));
            String packages = "prepay_id=" + prepayId;
            parameterMap.put("appId", ConstantPropertiesUtils.APPID);
            parameterMap.put("nonceStr", resultMap.get("nonce_str"));
            parameterMap.put("package", packages);
            parameterMap.put("signType", "MD5");
            parameterMap.put("timeStamp", String.valueOf(System.currentTimeMillis()));
            String sign = WXPayUtil.generateSignature(parameterMap, ConstantPropertiesUtils.PARTNERKEY);

            //返回结果
            Map<String, Object> result = new HashMap();
            result.put("timeStamp", parameterMap.get("timeStamp"));
            result.put("nonceStr", parameterMap.get("nonceStr"));
            result.put("signType", "MD5");
            result.put("paySign", sign);
            result.put("package", packages);
            if(null != resultMap.get("result_code")) {
                //微信支付二维码2小时过期，可采取2小时未支付取消订单
                redisTemplate.opsForValue().set(orderNo, result, 120, TimeUnit.MINUTES);
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    @Override
    public Map queryPayStatus(String orderNo, String paymentType) {
        //1、封装参数
        Map paramMap = new HashMap<>();
        paramMap.put("appid", ConstantPropertiesUtils.APPID);
        paramMap.put("mch_id", ConstantPropertiesUtils.PARTNER);
        paramMap.put("out_trade_no", orderNo);
        paramMap.put("nonce_str", WXPayUtil.generateNonceStr());
        //2、设置请求
        try {
            HttpClient client = new HttpClient("https://api.mch.weixin.qq.com/pay/orderquery");
            client.setXmlParam(WXPayUtil.generateSignedXml(paramMap, ConstantPropertiesUtils.PARTNERKEY));
            client.setHttps(true);
            client.post();
            //3、返回第三方的数据
            String xml = client.getContent();
            Map<String, String> resultMap = WXPayUtil.xmlToMap(xml);
            //6、转成Map
            //7、返回
            return resultMap;
        }catch (Exception e){
            e.printStackTrace();
            new HashMap<>();
        }
        return null;
    }
}
