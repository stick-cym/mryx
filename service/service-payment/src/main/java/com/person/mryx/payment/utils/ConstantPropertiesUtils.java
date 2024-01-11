package com.person.mryx.payment.utils;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConstantPropertiesUtils implements InitializingBean {

    public static  String CERT;
    public static  String PARTNER;
    public static  String NOTIFYURL;
    public static  String APPID ;
    public static  String PARTNERKEY ;
    @Value("${weixin.appid}")
    private String appid;

    @Value("${weixin.partner}")
    private String partner;

    @Value("${weixin.partnerkey}")
    private String partnerkey ;

    @Value("${weixin.notifyurl}")
    private String notifyurl;

    @Value("${weixin.cert}")
    private String cert;

    @Override
    public void afterPropertiesSet() throws Exception {
        PARTNER=partner;
        NOTIFYURL=notifyurl;
        APPID=appid;
        CERT=cert;
        PARTNERKEY=partnerkey;
    }


}
