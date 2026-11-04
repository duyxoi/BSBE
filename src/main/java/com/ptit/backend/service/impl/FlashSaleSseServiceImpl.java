package com.ptit.backend.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ptit.backend.service.FlashSaleSseService;

import lombok.RequiredArgsConstructor;

/**
 * FlashSaleSseServiceImpl
 */

@Service
@RequiredArgsConstructor
public class FlashSaleSseServiceImpl implements FlashSaleSseService {@Override
	

    public void broadcastStockUpdate(Long flashSaleItemId, int remainingStock, boolean soldOut) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SseEmitter createEmitter() {
		// TODO Auto-generated method stub
		return null;
	}
    
}
