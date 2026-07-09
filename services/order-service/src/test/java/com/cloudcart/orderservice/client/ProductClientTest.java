package com.cloudcart.orderservice.client;

import com.cloudcart.orderservice.dto.ProductResponse;
import com.cloudcart.orderservice.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductClientTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ProductClient productClient = new ProductClient(restClientBuilder, "http://product-service");

    @Test
    void fetchesProductFromProductService() {
        server.expect(requestTo("http://product-service/api/products/5"))
                .andRespond(withSuccess("""
                        {
                          "id": 5,
                          "sku": "SKU-5",
                          "name": "Mouse",
                          "description": "Wireless mouse",
                          "price": 49.99,
                          "stockQuantity": 11,
                          "active": true
                        }
                        """, MediaType.APPLICATION_JSON));

        ProductResponse product = productClient.getProduct(5L);

        assertThat(product.sku()).isEqualTo("SKU-5");
        assertThat(product.price()).isEqualByComparingTo("49.99");
        server.verify();
    }

    @Test
    void mapsProductNotFound() {
        server.expect(requestTo("http://product-service/api/products/404"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> productClient.getProduct(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
        server.verify();
    }
}
