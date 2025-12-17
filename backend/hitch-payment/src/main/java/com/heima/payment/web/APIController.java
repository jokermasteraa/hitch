package com.heima.payment.web;

import com.alipay.api.internal.util.AlipaySignature;
import com.github.wxpay.sdk.WXPayUtil;
import com.heima.commons.constant.HtichConstants;
import com.heima.commons.domin.vo.response.ResponseVO;
import com.heima.commons.groups.Group;
import com.heima.commons.initial.annotation.RequestInitial;
import com.heima.commons.utils.CommonsUtils;
import com.heima.modules.vo.OrderVO;
import com.heima.modules.vo.PaymentVO;
import com.heima.payment.configuration.AlipayPeoperties;
import com.heima.payment.handler.PaymentHandler;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api")
@Api(value = "支付操作Controller", tags = {"支付管理"})
@ApiResponses(@ApiResponse(code = 200, message = "处理成功"))
public class APIController {
    @Autowired
    private PaymentHandler paymentHandler;

    @Autowired
    private AlipayPeoperties alipayPeoperties;

    @ApiOperation(value = "微信异步通知API", tags = {"支付管理"})
    @RequestMapping("/nofify")
    public void notify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("[WX-Notify] 微信支付回调开始");
        //输入流转换为xml字符串
        String xml = CommonsUtils.streamToString(request.getInputStream());
        if (StringUtils.isEmpty(xml)) {
            System.out.println("[WX-Notify] 回调内容为空");
            return;
        }
        System.out.println("[WX-Notify] 原始报文：" + xml);
        Map<String, String> payResultMap = WXPayUtil.xmlToMap(xml);
        PaymentVO paymentVO = new PaymentVO();
        if (!"SUCCESS".equals(payResultMap.get("return_code"))) {
            System.out.println("[WX-Notify] return_code 非 SUCCESS，return_code=" + payResultMap.get("return_code"));
            return;
        }
        if (!"SUCCESS".equals(payResultMap.get("result_code"))) {
            System.out.println("[WX-Notify] result_code 非 SUCCESS，result_code=" + payResultMap.get("result_code"));
            return;
        }
        paymentVO.setOrderId(payResultMap.get("out_trade_no"));
        paymentVO.setAmount(Float.parseFloat(payResultMap.get("total_fee")));
        System.out.println("[WX-Notify] 订单号=" + paymentVO.getOrderId()
                + "，金额(分)=" + payResultMap.get("total_fee"));
        //确认支付
        paymentHandler.confirmPay(paymentVO);
        System.out.println("[WX-Notify] 业务处理完成，返回成功响应");
        //如果成功，给微信支付一个成功的响应
        response.setContentType("text/xml");
        response.getWriter().write(HtichConstants.WX_NOTIFY_SUCCESSFUL_RESPONSE_RESULT);
    }

    @ApiOperation(value = "支付宝异步通知API", tags = {"支付管理"})
    @RequestMapping("/alipayNotify")
    public void alipayNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("[Alipay-Notify] 支付宝支付回调开始");
        // 获取支付宝回调的所有参数
        Map<String, String[]> requestParams = request.getParameterMap();
        if (requestParams == null || requestParams.isEmpty()) {
            System.out.println("[Alipay-Notify] 参数为空");
            response.getWriter().write("failure");
            return;
        }

        Map<String, String> params = new HashMap<>();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            if (values == null) {
                continue;
            }
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                valueStr.append(values[i]);
                if (i < values.length - 1) {
                    valueStr.append(",");
                }
            }
            params.put(name, valueStr.toString());
        }
        System.out.println("[Alipay-Notify] 原始参数：" + params);

        // 验签
        boolean signVerified = AlipaySignature.rsaCheckV1(
                params,
                alipayPeoperties.getAlipayPublicKey(),
                alipayPeoperties.getCharset(),
                alipayPeoperties.getSignType()
        );

        if (!signVerified) {
            System.out.println("[Alipay-Notify] 验签失败");
            response.getWriter().write("failure");
            return;
        }

        String tradeStatus = params.get("trade_status");
        String outTradeNo = params.get("out_trade_no");
        String totalAmount = params.get("total_amount");
        System.out.println("[Alipay-Notify] trade_status=" + tradeStatus
                + "，out_trade_no=" + outTradeNo
                + "，total_amount=" + totalAmount);

        // 只处理支付成功的状态
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            System.out.println("[Alipay-Notify] 非支付成功状态，忽略本次回调");
            response.getWriter().write("success");
            return;
        }

        PaymentVO paymentVO = new PaymentVO();
        paymentVO.setOrderId(outTradeNo);
        if (StringUtils.isNotBlank(totalAmount)) {
            // total_amount 单位是元，转换为 Float
            paymentVO.setAmount(new BigDecimal(totalAmount).floatValue());
        }
        System.out.println("[Alipay-Notify] 开始执行业务确认，订单号=" + paymentVO.getOrderId()
                + "，金额(元)=" + totalAmount);
        //确认支付
        paymentHandler.confirmPay(paymentVO);

        // 按照支付宝要求返回 success
        System.out.println("[Alipay-Notify] 业务处理完成，返回 success");
        response.getWriter().write("success");
    }

    /**
     * 进行支付
     *
     * @return
     */
    @ApiOperation(value = "预支付接口API", tags = {"支付管理"})
    @PostMapping("/payment")
    @RequestInitial(groups = Group.Create.class)
    public ResponseVO<PaymentVO> payment(@RequestBody PaymentVO paymentVO) throws Exception {
        return paymentHandler.prePay(paymentVO);
    }

    @ApiOperation(value = "支付查询接口API", tags = {"支付管理"})
    @PostMapping("/query")
    @RequestInitial(groups = Group.Select.class)
    public ResponseVO<OrderVO> orderQuery(@RequestBody PaymentVO paymentVO) throws Exception {
        return paymentHandler.orderQuery(paymentVO);
    }


}
