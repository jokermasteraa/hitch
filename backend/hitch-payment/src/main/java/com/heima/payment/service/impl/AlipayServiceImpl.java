package com.heima.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alibaba.fastjson.JSON;
import com.heima.commons.enums.BusinessErrors;
import com.heima.commons.exception.BusinessRuntimeException;
import com.heima.modules.bo.PayResultBO;
import com.heima.modules.po.OrderPO;
import com.heima.payment.configuration.AlipayPeoperties;
import com.heima.payment.service.PayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付（沙箱环境）实现
 */
@Service("aliPayService")
public class AlipayServiceImpl implements PayService {

    @Autowired
    private AlipayClient alipayClient;

    @Autowired
    private AlipayPeoperties alipayPeoperties;

    /**
     * 预支付（生成支付宝二维码支付链接）
     */
    @Override
    public PayResultBO prePay(OrderPO orderPO) throws Exception {
        // 使用订单原价（单位：元），保留两位小数传给支付宝
        String totalAmount = "0.00";
        if (orderPO.getCost() != null) {
            totalAmount = new BigDecimal(orderPO.getCost())
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
        }

        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(alipayPeoperties.getNotifyUrl());

        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", orderPO.getId());
        bizContent.put("total_amount", totalAmount);
        bizContent.put("subject", "打车订单");
        bizContent.put("timeout_express", "30m");

        request.setBizContent(JSON.toJSONString(bizContent));

        AlipayTradePrecreateResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            throw new BusinessRuntimeException(BusinessErrors.PAYMENT_COMMUNICATION_FAILURE, e.getErrMsg());
        }

        if (response == null || !response.isSuccess()) {
            throw new BusinessRuntimeException(BusinessErrors.PAYMENT_PRE_PAY_FAIL,
                    response == null ? "支付宝下单失败" : response.getSubMsg());
        }

        // 包装为 PayResultBO，复用现有字段
        PayResultBO resultBO = new PayResultBO();
        resultBO.setOutTradeNo(orderPO.getId());
        resultBO.setTotalFee(totalAmount);
        // 这里将支付宝返回的二维码地址复用到 codeUrl 字段
        resultBO.setCodeUrl(response.getQrCode());
        resultBO.setPayInfo(response.getMsg());
        resultBO.setReturnCode("SUCCESS");
        resultBO.setResultCode("SUCCESS");
        return resultBO;
    }

    /**
     * 订单查询
     */
    @Override
    public PayResultBO orderQuery(String orderId) throws Exception {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", orderId);
        request.setBizContent(JSON.toJSONString(bizContent));

        AlipayTradeQueryResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            throw new BusinessRuntimeException(BusinessErrors.PAYMENT_COMMUNICATION_FAILURE, e.getErrMsg());
        }

        if (response == null || !response.isSuccess()) {
            throw new BusinessRuntimeException(BusinessErrors.PAYMENT_PAY_IN_PROGRESSL,
                    response == null ? "支付宝查询失败" : response.getSubMsg());
        }

        PayResultBO resultBO = new PayResultBO();
        resultBO.setOutTradeNo(orderId);
        resultBO.setPayInfo(response.getTradeStatus());
        resultBO.setReturnCode("SUCCESS");

        // 交易成功/结束视为支付成功
        if ("TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus())) {
            resultBO.setResultCode("SUCCESS");
            resultBO.setPayInfo("支付成功");
        } else {
            resultBO.setResultCode("NOTPAY");
        }
        return resultBO;
    }
}