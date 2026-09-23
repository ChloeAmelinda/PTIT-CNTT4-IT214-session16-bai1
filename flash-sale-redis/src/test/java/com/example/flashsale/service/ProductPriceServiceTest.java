package com.example.flashsale.service;

import com.example.flashsale.repository.ProductPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class ProductPriceServiceTest {
    private ProductPriceRepository repository;
    private ProductPriceService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductPriceRepository.class);
        service = new ProductPriceService(repository);
    }

    @Test
    void getRejectsNullAndBlankIdsWithoutQueryingDb() {
        assertThrows(IllegalArgumentException.class, () -> service.getProductPrice(null));
        assertThrows(IllegalArgumentException.class, () -> service.getProductPrice("  "));
        verify(repository, never()).findPriceById(anyString());
    }

    @Test
    void updateRejectsInvalidPriceWithoutTouchingDb() {
        assertThrows(IllegalArgumentException.class, () -> service.updateProductPrice("P001", -1));
        assertThrows(IllegalArgumentException.class, () -> service.updateProductPrice("P001", null));
        verify(repository, never()).updatePrice(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readAndUpdateUseRepository() {
        when(repository.findPriceById("P001")).thenReturn(100000);
        assertEquals(100000, service.getProductPrice("P001"));
        service.updateProductPrice("P001", 80000);
        verify(repository).updatePrice("P001", 80000);
    }
}
