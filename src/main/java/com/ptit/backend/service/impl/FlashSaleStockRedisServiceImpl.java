package com.ptit.backend.service.impl;

import java.security.DrbgParameters.Reseed;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.ptit.backend.service.FlashSaleStockRedisService;

import io.jsonwebtoken.lang.Collections;
import lombok.extern.slf4j.Slf4j;

/**
 * FlashSaleStockRedisServiceImpl
 */
@Slf4j
@Service
public class FlashSaleStockRedisServiceImpl implements FlashSaleStockRedisService {

    private static final String STOCK_KEY = "flash:stock:%d";
    private static final String USER_KEY = "flash:user:%d:%d";
    private static final String IDEMPOTENT_KEY = "flash:idempotent:%d:%d";
    private static final String RESERVATION_KEY = "flash:reserve:%d";
    private static final String ORDER_RESERVATION_KEY = "flash:order_reservation:%d";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> reserveStockScript;
    private final RedisScript<Long> releaseStockScript;
    private final int idempotencyTtl;

    public FlashSaleStockRedisServiceImpl(
            RedisTemplate<String, String> redisTemplate,
            @Qualifier("reserveStockScript") RedisScript<Long> reserveStockScript,
            @Qualifier("releaseStockScript") RedisScript<Long> releaseStockScript,
            @Value("${flash-sale.idempotency-ttl-seconds:10}") int idempotencyTtl) {
        this.redisTemplate = redisTemplate;
        this.reserveStockScript = reserveStockScript;
        this.releaseStockScript = releaseStockScript;
        this.idempotencyTtl = idempotencyTtl;
    }

    @Override
    public void cleanupCampaignKeys(Long campaignId, List<Long> flashSaleItemIds) {
        for (var item : flashSaleItemIds) {
            String key = String.format(STOCK_KEY, item);
            redisTemplate.delete(key);
        }
        log.info("[Redis] Cleaned up keys for campaign={}", campaignId);
    }

    @Override
    public void commitUserPurchase(Long flashSaleItemId, Long userId) {
        // Giữ số lượng của người dùng trong Redis vì đã tăng lúc giữ hàng.
        // Khi xác nhận thì giữ nguyên để không mua quá giới hạn.
        log.info("[Redis] Committed user purchase item={}, user={}", flashSaleItemId, userId);
    }

    @Override
    public void deleteOrderReservation(Long orderId) {
        if (orderId == null)
            return;
        String key = String.format(ORDER_RESERVATION_KEY, orderId);
        redisTemplate.delete(key);
        log.info("[Redis] Deleted order reservation mapping for orderId={}", orderId);
    }

    @Override
    public void deleteReservation(String reservationId) {
        String key = String.format(RESERVATION_KEY, reservationId);
        Object orderIdObject = redisTemplate.opsForHash().get(key, "orderId");
        if (orderIdObject != null) {
            Long orderId = Long.parseLong(orderIdObject.toString());
            deleteOrderReservation(orderId);
        }
        redisTemplate.delete(key);
        log.info("[Redis] Deleted reservation={}", reservationId);
    }

    @Override
    public Map<String, String> getReservation(String reservationId) {
        String key = String.format(RESERVATION_KEY, reservationId);
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
        if (raw.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(k.toString(), v.toString()));
        return result;
    }

    @Override
    public String getReservationIdByOrderId(Long orderId) {
        String key = String.format(ORDER_RESERVATION_KEY, orderId);
        String result = redisTemplate.opsForValue().get(key);
        return result;
    }

    @Override
    public long getReservationTtl(String reservationId) {
        String key = String.format(RESERVATION_KEY, reservationId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2;
    }

    @Override
    public int getStock(Long flashSaleItemId) {
        String key = String.format(STOCK_KEY, flashSaleItemId);
        String result = redisTemplate.opsForValue().get(key);
        return result != null ? Integer.parseInt(result) : 0;
    }

    @Override
    public int getUserPurchaseCount(Long flashSaleItemId, Long userId) {
        String key = String.format(USER_KEY, flashSaleItemId, userId);
        String result = redisTemplate.opsForValue().get(key);
        return result != null ? Integer.parseInt(result) : 0;
    }

    @Override
    public void initializeStock(Long flashSaleItemId, int availableStock) {
        String key = String.format(STOCK_KEY, flashSaleItemId);
        redisTemplate.opsForValue().set(key, String.valueOf(availableStock));
        log.info("[Redis] Initialized stock for flash_sale_item_id={}: {}", flashSaleItemId, availableStock);
    }

    @Override
    public long releaseStock(Long flashSaleItemId, Long userId, String reservationId, int quantity) {
        List<String> keys = Arrays.asList(
                String.format(STOCK_KEY, flashSaleItemId),
                String.format(USER_KEY, flashSaleItemId, userId),
                String.format(RESERVATION_KEY, reservationId));
        Long result = redisTemplate.execute(releaseStockScript, keys, String.valueOf(quantity));
        log.info("[Redis] releaseStock reservation={}, result={}", reservationId, result);
        return result != null ? result : 0;
    }

    @Override
    public long reserveStock(Long flashSaleItemId, Long userId, int quantity, int maxPerUser) {
        List<String> keys = Arrays.asList(
                String.format(STOCK_KEY, flashSaleItemId),
                String.format(USER_KEY, flashSaleItemId, userId),
                String.format(IDEMPOTENT_KEY, flashSaleItemId, userId));
        Long result = redisTemplate.execute(reserveStockScript, keys, String.valueOf(quantity),
                String.valueOf(maxPerUser), String.valueOf(idempotencyTtl));

        log.info("[Redis] reserveStock item={}, user={}, qty={}, result={}", flashSaleItemId, userId, quantity, result);
        return result != null ? result : -99;
    }

    @Override
    public void setReservation(String reservationId, Map<String, String> data, long ttlSeconds) {
        String key = String.format(RESERVATION_KEY, reservationId);
        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);

        if (data.containsKey("orderId")) {
            try {
                Long orderId = Long.parseLong(data.get("orderId"));
                String orderKey = String.format(ORDER_RESERVATION_KEY, orderId);
                redisTemplate.opsForValue().set(orderKey, reservationId, ttlSeconds, TimeUnit.SECONDS);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                // TODO: handle exception
            }
        }
        log.info("[Redis] Set reservation={} with TTL={}s", reservationId, ttlSeconds);

    }

}
