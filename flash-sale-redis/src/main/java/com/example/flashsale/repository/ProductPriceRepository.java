package com.example.flashsale.repository;

import java.util.NoSuchElementException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductPriceRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProductPriceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer findPriceById(String productId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT price FROM products WHERE id = ?", Integer.class, productId);
        } catch (EmptyResultDataAccessException ex) {
            throw new NoSuchElementException("Không tìm thấy sản phẩm: " + productId);
        }
    }

    public void updatePrice(String productId, Integer newPrice) {
        int updated = jdbcTemplate.update(
                "UPDATE products SET price = ? WHERE id = ?", newPrice, productId);
        if (updated == 0) {
            throw new NoSuchElementException("Không tìm thấy sản phẩm: " + productId);
        }
    }
}
