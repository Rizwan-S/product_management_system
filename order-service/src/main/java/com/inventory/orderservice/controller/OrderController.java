package com.inventory.orderservice.controller;

import com.inventory.orderservice.dto.OrderRequest;
import com.inventory.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

//    @CircuitBreaker(name="inventory", fallbackMethod = "fallbackMethod")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
//    @TimeLimiter(name = "inventory")
//    @Retry(name = "inventory")
    public String placeOrder(@RequestBody OrderRequest orderRequest) {
//        return CompletableFuture.supplyAsync(() -> orderService.placeOrder(orderRequest));
        return orderService.placeOrder(orderRequest);
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest, RuntimeException runtimeException) {
        return CompletableFuture.supplyAsync(() -> "Something went wrong. Please order after sometime!");
    }
}
