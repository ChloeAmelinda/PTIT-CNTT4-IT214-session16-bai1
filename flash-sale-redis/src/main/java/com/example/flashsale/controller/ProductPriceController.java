package com.example.flashsale.controller;

import com.example.flashsale.dto.PriceResponse;
import com.example.flashsale.dto.UpdatePriceRequest;
import com.example.flashsale.service.ProductPriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductPriceController {
    private final ProductPriceService service;

    public ProductPriceController(ProductPriceService service) {
        this.service = service;
    }

    @GetMapping("/{productId}/price")
    public PriceResponse getPrice(@PathVariable String productId) {
        return new PriceResponse(productId, service.getProductPrice(productId));
    }

    // Demo học tập: production phải xác thực + phân quyền admin, có audit log.
    @PatchMapping("/{productId}/price")
    public PriceResponse updatePrice(@PathVariable String productId,
                                     @RequestBody UpdatePriceRequest request) {
        service.updateProductPrice(productId,
                request == null ? null : request.newPrice());
        return new PriceResponse(productId, service.getProductPrice(productId));
    }
}
