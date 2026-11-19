package com.ptit.backend.messaging;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.ptit.backend.config.RabbitMQConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FlashSaleMessagePublisher
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Gửi message hủy trễ, hết delay sẽ chuyển sang queue xử lý.
     */
    public void publishDelayedCancel(FlashSaleMessage message, long delayMs) {
        MessagePostProcessor messagePostProcessor = msg -> {
            msg.getMessageProperties().setExpiration(String.valueOf(delayMs));
            return msg;
        };

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.FLASH_SALE_EXCHANGE,
                RabbitMQConfig.CANCEL_ROUTING_KEY,
                message,
                messagePostProcessor);

        log.info("[MQ] Published delayed cancel for reservation={}, delay={}ms", message.getReservationId(), delayMs);
    }

    /**
     * Gửi sự kiện xác nhận đơn để xử lý nền.
     */
    public void publishOrderConfirmed(FlashSaleMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FLASH_SALE_EXCHANGE, RabbitMQConfig.ORDER_CONFIRMED_ROUTING_KEY,
                message);
        log.info("[MQ] Published order confirmed for reservation={}", message.getReservationId());

    }
}
