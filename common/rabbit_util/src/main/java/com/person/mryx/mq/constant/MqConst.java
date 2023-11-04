package com.person.mryx.mq.constant;

public class MqConst {
    /**
     * 消息补偿
     */
    public static final String MQ_KEY_PREFIX = "mryx.mq:list";
    public static final int RETRY_COUNT = 3;

    /**
     * 商品上下架
     */
    public static final String EXCHANGE_GOODS_DIRECT = "mryx.goods.direct";
    public static final String ROUTING_GOODS_UPPER = "mryx.goods.upper";
    public static final String ROUTING_GOODS_LOWER = "mryx.goods.lower";
    //队列
    public static final String QUEUE_GOODS_UPPER  = "mryx.goods.upper";
    public static final String QUEUE_GOODS_LOWER  = "mryx.goods.lower";

    /**
     * 团长上下线
     */
    public static final String EXCHANGE_LEADER_DIRECT = "mryx.leader.direct";
    public static final String ROUTING_LEADER_UPPER = "mryx.leader.upper";
    public static final String ROUTING_LEADER_LOWER = "mryx.leader.lower";
    //队列
    public static final String QUEUE_LEADER_UPPER  = "mryx.leader.upper";
    public static final String QUEUE_LEADER_LOWER  = "mryx.leader.lower";

    //订单
    public static final String EXCHANGE_ORDER_DIRECT = "mryx.order.direct";
    public static final String ROUTING_ROLLBACK_STOCK = "mryx.rollback.stock";
    public static final String ROUTING_MINUS_STOCK = "mryx.minus.stock";

    public static final String ROUTING_DELETE_CART = "mryx.delete.cart";
    //解锁普通商品库存
    public static final String QUEUE_ROLLBACK_STOCK = "mryx.rollback.stock";
    public static final String QUEUE_SECKILL_ROLLBACK_STOCK = "mryx.seckill.rollback.stock";
    public static final String QUEUE_MINUS_STOCK = "mryx.minus.stock";
    public static final String QUEUE_DELETE_CART = "mryx.delete.cart";

    //支付
    public static final String EXCHANGE_PAY_DIRECT = "mryx.pay.direct";
    public static final String ROUTING_PAY_SUCCESS = "mryx.pay.success";
    public static final String QUEUE_ORDER_PAY  = "mryx.order.pay";
    public static final String QUEUE_LEADER_BILL  = "mryx.leader.bill";

    //取消订单
    public static final String EXCHANGE_CANCEL_ORDER_DIRECT = "mryx.cancel.order.direct";
    public static final String ROUTING_CANCEL_ORDER = "mryx.cancel.order";
    //延迟取消订单队列
    public static final String QUEUE_CANCEL_ORDER  = "mryx.cancel.order";

    /**
     * 定时任务
     */
    public static final String EXCHANGE_DIRECT_TASK = "mryx.exchange.direct.task";
    public static final String ROUTING_TASK_23 = "mryx.task.23";
    //队列
    public static final String QUEUE_TASK_23  = "mryx.queue.task.23";
}
