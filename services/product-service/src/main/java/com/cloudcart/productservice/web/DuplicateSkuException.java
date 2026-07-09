package com.cloudcart.productservice.web;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("Product sku '%s' already exists".formatted(sku));
    }
}
