package com.cloudcart.orderservice.service;

import com.cloudcart.orderservice.client.ProductClient;
import com.cloudcart.orderservice.dto.CreateOrderItemRequest;
import com.cloudcart.orderservice.dto.CreateOrderRequest;
import com.cloudcart.orderservice.dto.OrderResponse;
import com.cloudcart.orderservice.dto.ProductResponse;
import com.cloudcart.orderservice.entity.CustomerOrder;
import com.cloudcart.orderservice.repository.OrderRepository;
import com.cloudcart.orderservice.web.ProductUnavailableException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createsOrderUsingProductDetails() {
        when(productClient.getProduct(5L)).thenReturn(product(5L, "SKU-5", "Mouse", "49.99", 11, true));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateOrderRequest request = new CreateOrderRequest("customer-1", List.of(new CreateOrderItemRequest(5L, 2)));

        OrderResponse response = orderService.create(request);

        assertThat(response.customerId()).isEqualTo("customer-1");
        assertThat(response.totalAmount()).isEqualByComparingTo("99.98");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().sku()).isEqualTo("SKU-5");
    }

    @Test
    void rejectsInactiveProduct() {
        when(productClient.getProduct(5L)).thenReturn(product(5L, "SKU-5", "Mouse", "49.99", 11, false));
        CreateOrderRequest request = new CreateOrderRequest("customer-1", List.of(new CreateOrderItemRequest(5L, 1)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("inactive");
    }

    private ProductResponse product(Long id, String sku, String name, String price, int stockQuantity, boolean active) {
        return new ProductResponse(
                id,
                sku,
                name,
                null,
                new BigDecimal(price),
                stockQuantity,
                active,
                null,
                null);
    }
}
