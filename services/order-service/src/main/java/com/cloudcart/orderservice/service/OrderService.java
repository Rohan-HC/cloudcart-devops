package com.cloudcart.orderservice.service;

import com.cloudcart.orderservice.client.ProductClient;
import com.cloudcart.orderservice.dto.CreateOrderItemRequest;
import com.cloudcart.orderservice.dto.CreateOrderRequest;
import com.cloudcart.orderservice.dto.OrderItemResponse;
import com.cloudcart.orderservice.dto.OrderResponse;
import com.cloudcart.orderservice.dto.ProductResponse;
import com.cloudcart.orderservice.entity.CustomerOrder;
import com.cloudcart.orderservice.entity.OrderItem;
import com.cloudcart.orderservice.entity.OrderStatus;
import com.cloudcart.orderservice.repository.OrderRepository;
import com.cloudcart.orderservice.web.ProductUnavailableException;
import com.cloudcart.orderservice.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    public OrderResponse create(CreateOrderRequest request) {
        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.CREATED);

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : request.items()) {
            ProductResponse product = productClient.getProduct(itemRequest.productId());
            validateProduct(product, itemRequest.quantity());

            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setSku(product.sku());
            item.setProductName(product.name());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(product.price());
            item.setLineTotal(lineTotal);
            order.addItem(item);

            total = total.add(lineTotal);
        }

        order.setTotalAmount(total);
        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id '%d' was not found".formatted(id)));
    }

    private void validateProduct(ProductResponse product, int requestedQuantity) {
        if (!product.active()) {
            throw new ProductUnavailableException("Product '%s' is inactive".formatted(product.sku()));
        }
        if (product.stockQuantity() < requestedQuantity) {
            throw new ProductUnavailableException(
                    "Product '%s' has only %d items in stock".formatted(product.sku(), product.stockQuantity()));
        }
    }

    private OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getSku(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
