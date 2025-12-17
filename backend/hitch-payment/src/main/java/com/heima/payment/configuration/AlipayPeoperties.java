package com.heima.payment.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝支付配置属性类（沙箱/正式环境通用）
 */
@ConfigurationProperties(prefix = "alipay")
public class AlipayPeoperties {

    /**
     * 应用 AppId
     */
    private String appId;

    /**
     * 商户私钥
     */
    private String merchantPrivateKey;

    /**
     * 支付宝公钥
     */
    private String alipayPublicKey;

    /**
     * 异步通知地址
     */
    private String notifyUrl;

    /**
     * 同步回跳地址
     */
    private String returnUrl;

    /**
     * 签名类型，默认 RSA2
     */
    private String signType = "RSA2";

    /**
     * 字符集，默认 utf-8
     */
    private String charset = "utf-8";

    /**
     * 网关地址（沙箱/正式环境）
     */
    private String gatewayUrl;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getMerchantPrivateKey() {
        return merchantPrivateKey;
    }

    public void setMerchantPrivateKey(String merchantPrivateKey) {
        this.merchantPrivateKey = merchantPrivateKey;
    }

    public String getAlipayPublicKey() {
        return alipayPublicKey;
    }

    public void setAlipayPublicKey(String alipayPublicKey) {
        this.alipayPublicKey = alipayPublicKey;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }
}
