package com.cloudcart.productservice.service;

import com.cloudcart.productservice.dto.ProductRequest;
import com.cloudcart.productservice.entity.Product;
import com.cloudcart.productservice.repository.ProductRepository;
import com.cloudcart.productservice.web.DuplicateSkuException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createsProductWhenSkuIsUnique() {
        ProductRequest request = new ProductRequest(
                "SKU-1",
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("129.99"),
                8,
                true);
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("SKU-1");
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("129.99");
    }

    @Test
    void rejectsDuplicateSku() {
        ProductRequest request = new ProductRequest("SKU-1", "Keyboard", null, new BigDecimal("10.00"), 1, true);
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("SKU-1");
    }
}
