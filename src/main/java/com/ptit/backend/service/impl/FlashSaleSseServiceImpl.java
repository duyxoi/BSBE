package com.ptit.backend.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ptit.backend.dto.response.FlashSaleStockUpdate;
import com.ptit.backend.service.FlashSaleSseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FlashSaleSseServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleSseServiceImpl implements FlashSaleSseService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 phút

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public void broadcastStockUpdate(Long flashSaleItemId, int remainingStock, boolean soldOut) {
        FlashSaleStockUpdate update = FlashSaleStockUpdate.builder()
                .soldOut(soldOut)
                .remainingStock(remainingStock)
                .flashSaleItemId(flashSaleItemId)
                .build();

        List<SseEmitter> deadEmitter = new ArrayList<>();

        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("stock-update")
                        .data(update));
            } catch (Exception e) {
                deadEmitter.add(emitter);
                log.debug("[SSE] Emitter removed due to error: {}", e.getMessage());
            }
        }

        emitters.removeAll(deadEmitter);

        if (!deadEmitter.isEmpty()) {
            log.info("[SSE] Removed {} dead emitters. Remaining: {}", deadEmitter.size(), emitters.size());

        }
    }

    @Override
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));

        emitter.onTimeout(() -> {
            emitter.complete();
        });

        emitter.onError(e -> {
            emitter.completeWithError(e);
        });

        // Gửi sự kiện kết nối ban đầu
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\"}"));
            log.info("[SSE] New emitter connected. Total: {}", emitters.size());

        } catch (IOException e) {
            emitters.remove(emitter);
            log.warn("[SSE] Failed to send initial event: {}", e.getMessage());
        }

        return emitter;
    }

}
