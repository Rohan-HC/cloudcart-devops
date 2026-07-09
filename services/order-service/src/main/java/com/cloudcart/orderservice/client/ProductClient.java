package com.cloudcart.orderservice.client;

import com.cloudcart.orderservice.dto.ProductResponse;
import com.cloudcart.orderservice.web.ProductServiceException;
import com.cloudcart.orderservice.web.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(RestClient.Builder restClientBuilder,
                         @Value("${app.product-service.base-url}") String productServiceBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(productServiceBaseUrl).build();
    }

    public ProductResponse getProduct(Long productId) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new ResourceNotFoundException("Product with id '%d' was not found".formatted(productId));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new ProductServiceException("Product service is unavailable");
                    })
                    .body(ProductResponse.class);
        } catch (RestClientException ex) {
            throw new ProductServiceException("Product service request failed", ex);
        }
    }
}
