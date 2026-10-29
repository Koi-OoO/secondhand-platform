package com.secondhand.constant;

/**
 * 订单状态常量，取值与数据库 order.status 一致；前端对应 src/utils/constant.js 的 ORDER_*。
 */
public final class OrderStatus {

    public static final int WAIT_MEETUP = 1;   // 待面交：卖家已接单
    public static final int WAIT_CONFIRM = 2;  // 待确认：卖家已交付，等买家确认
    public static final int COMPLETED = 3;     // 已完成：买家确认收到
    public static final int CANCELLED = 4;     // 已取消：买家取消或卖家拒绝
    public static final int WAIT_ACCEPT = 5;   // 待接单：买家刚下单
    public static final int NEGOTIATING = 6;   // 待协商：买家反馈问题

    private OrderStatus() {
    }

    public static boolean isWaitMeetup(Integer status) {
        return Integer.valueOf(WAIT_MEETUP).equals(status);
    }

    public static boolean isWaitConfirm(Integer status) {
        return Integer.valueOf(WAIT_CONFIRM).equals(status);
    }

    public static boolean isCompleted(Integer status) {
        return Integer.valueOf(COMPLETED).equals(status);
    }

    public static boolean isWaitAccept(Integer status) {
        return Integer.valueOf(WAIT_ACCEPT).equals(status);
    }

    public static boolean isNegotiating(Integer status) {
        return Integer.valueOf(NEGOTIATING).equals(status);
    }
}
