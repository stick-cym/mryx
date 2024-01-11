package com.person.mryx.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum SkuType {
    COMMON(0,"普通"),
    SECKILL(1,"秒杀" );

    @EnumValue
    private final Integer code ;

    private final String comment ;

    SkuType(Integer code, String comment ){
        this.code=code;
        this.comment=comment;
    }
}
