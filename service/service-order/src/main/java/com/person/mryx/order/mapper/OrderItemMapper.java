package com.person.mryx.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.person.mryx.model.order.OrderInfo;
import com.person.mryx.model.order.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
