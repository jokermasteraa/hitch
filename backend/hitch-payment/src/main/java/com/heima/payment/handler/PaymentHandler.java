package com.heima.payment.handler;

import com.heima.commons.domin.vo.response.ResponseVO;
import com.heima.commons.enums.BusinessErrors;
import com.heima.commons.exception.BusinessRuntimeException;
import com.heima.commons.utils.CommonsUtils;
import com.heima.commons.utils.reflect.ReflectUtils;
import com.heima.modules.bo.PayResultBO;
import com.heima.modules.po.OrderPO;
import com.heima.modules.po.PaymentPO;
import com.heima.modules.vo.OrderVO;
import com.heima.modules.vo.PaymentVO;
import com.heima.payment.service.OrderAPIService;
import com.heima.payment.service.PayService;
import com.heima.payment.service.PaymentAPIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentHandler {
    @Autowired
    private PayService wxPayService;

    @Autowired
    private PayService aliPayService;

    @Autowired
    private PaymentAPIService paymentAPIService;

    @Autowired
    private OrderAPIService orderAPIService;

    /**
     * 预支付接口
     *
     * @param paymentVO
     * @return
     * @throws Exception
     */
    public ResponseVO<PaymentVO> prePay(PaymentVO paymentVO) throws Exception {
        OrderPO orderPO = checkOrder(paymentVO);
        System.out.println("PrePay request, channel=" + paymentVO.getChannel() + ", orderId=" + paymentVO.getOrderId());
        PayResultBO payResultBO = getPayService(paymentVO.getChannel()).prePay(orderPO);
        addPayOrder(paymentVO, payResultBO, orderPO);
        ReflectUtils.copyProperties(payResultBO, paymentVO);
        // 调试：打印返回给前端的支付信息（包含 codeUrl）
        System.out.println("PrePay result, channel=" + paymentVO.getChannel()
                + ", orderId=" + paymentVO.getOrderId()
                + ", codeUrl=" + paymentVO.getCodeUrl());
        return ResponseVO.success(paymentVO);
    }

    /**
     * 订单查询
     *
     * @param paymentVO
     * @return
     * @throws Exception
     */

    public ResponseVO<OrderVO> orderQuery(PaymentVO paymentVO) throws Exception {
        OrderPO orderPO = checkOrder(paymentVO);
        PaymentPO paymentPO = paymentAPIService.selectByOrderId(orderPO.getId());
        // 如果还没有生成支付记录，认为支付尚未发起/未完成，直接返回订单信息，不抛异常
        if (paymentPO == null) {
            return ResponseVO.success(orderPO, "未支付");
        }
        //如果支付未完成
        if (orderPO.getStatus() == 1) {
            //查询支付状态
            PayResultBO payResultBO = getPayService(paymentPO.getChannel()).orderQuery(orderPO.getId());
            //如果支付成功修改订单状态
            if (null != payResultBO) {
                paymentPO.setPayInfo(payResultBO.getPayInfo());
                if (payResultBO.isSuccess()) {
                    updateOrderPaySucces(paymentPO, orderPO);
                }
            }
        }
        return ResponseVO.success(orderPO, paymentPO.getPayInfo());
    }

    /**
     * 确认支付
     *
     * @param paymentVO
     */
    public ResponseVO<OrderVO> confirmPay(PaymentVO paymentVO) {
        OrderPO orderPO = checkOrder(paymentVO);
        PaymentPO paymentPO = paymentAPIService.selectByOrderId(orderPO.getId());
        if (paymentPO == null) {
            throw new BusinessRuntimeException(BusinessErrors.DATA_NOT_EXIST);
        }
        //如果支付未完成
        if (orderPO.getStatus() == 1) {
            //如果支付成功修改订单状态
            paymentPO.setPayInfo("支付成功");
            updateOrderPaySucces(paymentPO, orderPO);
        }
        return ResponseVO.success(orderPO);
    }


    /**
     * 添加支付订单
     *
     * @param paymentVO
     */
    private void addPayOrder(PaymentVO paymentVO, PayResultBO payResultBO, OrderPO orderPO) {
        PaymentPO paymentPO = CommonsUtils.toPO(paymentVO);
        paymentPO.setPrepayId(payResultBO.getPrepayId());
        // 记录真实支付金额（订单价格），单位：元
        paymentPO.setAmount(orderPO.getCost());
        // 按照请求中的渠道保存（1-支付宝，2-微信），默认微信
        paymentPO.setChannel(paymentVO.getChannel() == null ? 2 : paymentVO.getChannel());
        paymentPO.setTransactionOrderNum("1");
        paymentAPIService.add(paymentPO);
    }

    /**
     * 更新订单数据未支付成功
     *
     * @param paymentPO
     * @param orderPO
     */
    public void updateOrderPaySucces(PaymentPO paymentPO, OrderPO orderPO) {
        paymentAPIService.update(paymentPO);
        orderPO.setStatus(2);
        orderAPIService.update(orderPO);
    }

    public OrderPO checkOrder(PaymentVO paymentVO) {
        OrderPO orderPO = orderAPIService.selectByID(paymentVO.getOrderId());
        if (null == orderPO) {
            throw new BusinessRuntimeException(BusinessErrors.DATA_NOT_EXIST, "用户订单不存在");
        }
        return orderPO;
    }

    /**
     * 根据支付渠道选择具体的支付实现
     *
     * @param channel 支付渠道 支付宝：1（默认） 微信：2
     * @return PayService 实现
     */
    private PayService getPayService(Integer channel) {
        System.out.println("Select PayService by channel=" + channel);
        // 默认以及 channel==1 走支付宝
        if (channel == null || channel == 1) {
            return aliPayService;
        }
        // 只有明确为 2 时走微信
        return wxPayService;
    }

}
