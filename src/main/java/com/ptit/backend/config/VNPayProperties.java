
package com.ptit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payment.vnpay")
public class VNPayProperties {
    private String tmnCode;
    private String hashSecret;
    private String payUrl;
    private String returnUrl;
    private String frontendReturnUrl;

    public String getTmnCode() {
        return tmnCode != null ? tmnCode.trim() : null;
    }

    public String getHashSecret() {
        return hashSecret != null ? hashSecret.trim() : null;
    }

    public String getPayUrl() {
        return payUrl != null ? payUrl.trim() : null;
    }

    public String getReturnUrl() {
        return returnUrl != null ? returnUrl.trim() : null;
    }

    public String getFrontendReturnUrl() {
        return frontendReturnUrl != null ? frontendReturnUrl.trim() : null;
    }
}
