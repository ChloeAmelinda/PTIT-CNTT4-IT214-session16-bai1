package com.example.flashsale.service;

import com.example.flashsale.repository.ProductPriceRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductPriceService {
    private final ProductPriceRepository productRepository;

    public ProductPriceService(ProductPriceRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(cacheNames = "productPrices", key = "#p0",
            condition = "#p0 != null && !#p0.isBlank()",
            unless = "#result == null")
    public Integer getProductPrice(String productId) {
        validateProductId(productId);
        return productRepository.findPriceById(productId);
    }

    @Transactional
    @CacheEvict(cacheNames = "productPrices", key = "#p0",
            condition = "#p0 != null && !#p0.isBlank()")
    public void updateProductPrice(String productId, Integer newPrice) {
        validateProductId(productId);
        if (newPrice == null || newPrice < 0) {
            throw new IllegalArgumentException("newPrice phải là số nguyên >= 0");
        }
        productRepository.updatePrice(productId, newPrice);
        // Cache bị evict sau khi phương thức thành công; CacheManager transactionAware
        // trì hoãn evict đến khi DB transaction commit thành công.
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId không được null hoặc rỗng");
        }
    }
}
