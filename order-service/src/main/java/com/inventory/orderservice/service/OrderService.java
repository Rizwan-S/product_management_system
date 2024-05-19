package com.inventory.orderservice.service;

import brave.Span;
import brave.Tracer;
import com.inventory.orderservice.dto.InventoryResponse;
import com.inventory.orderservice.dto.OrderLineItemsDto;
import com.inventory.orderservice.dto.OrderRequest;
import com.inventory.orderservice.event.OrderPlacedEvent;
import com.inventory.orderservice.model.Order;
import com.inventory.orderservice.model.OrderLineItems;
import com.inventory.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final Tracer tracer;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        Span inventoryServiceLookup = tracer.nextSpan().name("InventoryServiceLookUp");
        try(Tracer.SpanInScope spanInScope = tracer.withSpanInScope(inventoryServiceLookup.start())) {
            //Call Inventory Service, and place order if the product is in stock.
            InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();

            System.out.println("inventoryResponseArray check:");
            assert inventoryResponseArray != null;
            for(InventoryResponse e: inventoryResponseArray) {
                System.out.println("inventory response::" + e.isInStock());
            }

            boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                    .allMatch(InventoryResponse::isInStock);

            System.out.println("Save Order::" + allProductsInStock);
            if(allProductsInStock) {
                kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
                orderRepository.save(order);
                return "Order placed successfully!";
            } else {
                throw new IllegalArgumentException("Product is not in stock, please try again later.");
            }
        } finally {
            inventoryServiceLookup.finish();
        }

    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
