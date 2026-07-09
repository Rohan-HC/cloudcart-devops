package com.cloudcart.orderservice.repository;

import com.cloudcart.orderservice.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
}
