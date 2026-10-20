package com.ptit.backend.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ptit.backend.entity.Order;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class VNPayProvider {
    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "pay";
    private static final String CURRENCY = "VND";
    private static final String ORDER_TYPE = "250000";
    private static final String LOCALE = "vn";
    private static final String DEFAULT_IP = "127.0.0.1";
    private final VNPayProperties vnPayProperties;

    public String createPaymentUrl(Order order, HttpServletRequest request) {
        return createPaymentUrl(order, request, 15);
    }

    public String createPaymentUrl(Order order, HttpServletRequest request, int expireMinutes) {
        String tmnCode = vnPayProperties.getTmnCode();
        String payUrl = vnPayProperties.getPayUrl();
        String hashSercet = vnPayProperties.getHashSecret();

        String returnUrl = resolveReturnUrl(request);

        log.info(tmnCode + "\n" + payUrl + "\n" + hashSercet + "\n" + returnUrl);

        long amount = order.getTotalAmount().longValue() * 100;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", VERSION);
        vnp_Params.put("vnp_Command", COMMAND);
        vnp_Params.put("vnp_TmnCode", tmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", CURRENCY);

        vnp_Params.put("vnp_TxnRef", String.valueOf(order.getOrderId()));
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderId());

        vnp_Params.put("vnp_OrderType", ORDER_TYPE);
        vnp_Params.put("vnp_Locale", LOCALE);
        vnp_Params.put("vnp_ReturnUrl", returnUrl);

        vnp_Params.put("vnp_IpAddr", resolveClientIp(request));

        // 3. Thời gian (sử dụng Asia/Ho_Chi_Minh thay vì Etc/GMT+7 bị sai lệch giờ)
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));

        cld.add(Calendar.MINUTE, expireMinutes);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));
        // 4. Sắp xếp Alphabet
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        try {
            for (String fieldName : fieldNames) {
                String fieldValue = vnp_Params.get(fieldName);
                if (fieldValue != null && !fieldValue.isEmpty()) {
                    if (hashData.length() > 0) {
                        hashData.append('&');
                    }
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (query.length() > 0) {
                        query.append('&');
                    }
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                }
            }
        } catch (Exception e) {
            log.error("[VNPayProvider] Error building payment url query", e);
            return "";
        }

        // 5. Băm bằng HMAC-SHA512
        String queryUrl = query.toString();
        String vnp_SecureHash = hmacSHA512(hashSercet, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;

        return payUrl + "?" + queryUrl;

    }


    public boolean verifyIpn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }

        // 1. Lấy chữ ký do VNPay gửi về
        String vnp_SecureHash = params.get("vnp_SecureHash");
        if (vnp_SecureHash == null || vnp_SecureHash.isBlank()) {
            return false;
        }

        // 2. Chỉ lọc các trường bắt đầu bằng "vnp_", bỏ qua các trường chữ ký và giá trị rỗng/null
        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();
            if (fieldName != null && fieldName.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(fieldName)
                    && !"vnp_SecureHashType".equals(fieldName)
                    && fieldValue != null && !fieldValue.isEmpty()) {
                fields.put(fieldName, fieldValue);
            }
        }

        // 3. Sắp xếp các tham số Alphabet
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        try {
            for (String fieldName : fieldNames) {
                String fieldValue = fields.get(fieldName);
                if (hashData.length() > 0) {
                    hashData.append('&');
                }
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
            }
        } catch (Exception e) {
            log.error("[VNPayProvider] Error building hash data for verification", e);
            return false;
        }

        // 4. Băm lại chuỗi bằng HashSecret
        String vnp_HashSecret = vnPayProperties.getHashSecret();
        String signValue = hmacSHA512(vnp_HashSecret, hashData.toString());

        // 5. So sánh 2 chữ ký không phân biệt hoa thường
        return signValue.equalsIgnoreCase(vnp_SecureHash);
    }
    private String hmacSHA512(final String key,final String data){
        try {
            if(key == null || data == null) throw new NullPointerException();
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes= key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKeySpec = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKeySpec);
            
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder();
            for(var b: result){
                sb.append(String.format("%02x", b & 0xff));
            }

            return sb.toString();

        } catch (Exception e) {
            //TODO: handle exception
            e.printStackTrace();
            return "";
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_IP;
        }
        String ip = DEFAULT_IP;
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            ip = forwardedFor.split(",")[0].trim();
        } else {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                ip = realIp.trim();
            } else {
                String remoteAddr = request.getRemoteAddr();
                if (remoteAddr != null && !remoteAddr.isBlank()) {
                    ip = remoteAddr;
                }
            }
        }
        // VNPay có thể lỗi chữ ký nếu IP là IPv6 (có chứa dấu : )
        if (ip.contains(":")) {
            return "127.0.0.1";
        }
        return ip;

    }

    private String resolveReturnUrl(HttpServletRequest request) {
        String configuredUrl = vnPayProperties.getReturnUrl();
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl.trim();
        }
        if (request == null) {
            return "http://localhost:8081/api/v1/payment/vnpay-callback";
        }

        // Tai sao lai co phan sau nay ??
        String scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            scheme = forwardedProto.trim();
        }

        return scheme + "://" + host + "/api/v1/payment/vnpay-callback";
    }

}
