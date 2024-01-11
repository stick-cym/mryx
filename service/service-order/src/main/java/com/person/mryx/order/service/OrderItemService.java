package com.person.mryx.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.person.mryx.model.order.OrderInfo;
import com.person.mryx.model.order.OrderItem;
import com.person.mryx.vo.order.OrderConfirmVo;
import com.person.mryx.vo.order.OrderSubmitVo;
import org.springframework.stereotype.Service;

public interface OrderItemService extends IService<OrderItem> {
}
