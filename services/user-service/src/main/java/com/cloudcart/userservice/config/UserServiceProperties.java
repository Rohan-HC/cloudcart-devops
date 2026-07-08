package com.cloudcart.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record UserServiceProperties(String secret, long expirationMinutes) {
}
