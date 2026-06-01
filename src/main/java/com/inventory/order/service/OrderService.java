package com.inventory.order.service;

import com.inventory.order.dto.*;
import com.inventory.order.exception.InsufficientStockException;
import com.inventory.order.exception.ResourceNotFoundException;
import com.inventory.order.model.*;
import com.inventory.order.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return mapToOrderResponse(order);
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        // 1. Verify Customer
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.getCustomerId()));

        Order order = Order.builder()
                .customer(customer)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .totalPrice(BigDecimal.ZERO)
                .build();

        List<OrderItem> items = new ArrayList<>();
        BigDecimal orderTotal = BigDecimal.ZERO;

        // 2. Lock & check stock for each product
        for (OrderItemRequest itemReq : request.getItems()) {
            // Find with PESSIMISTIC_WRITE lock to prevent race conditions during concurrent orders
            Product product = productRepository.findByIdForUpdate(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId()));

            // Validate inventory stock
            if (product.getStock() < itemReq.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "' (SKU: " + product.getSku() + "). Requested: " + itemReq.getQuantity() + ", Available: " + product.getStock());
            }

            // Deduct stock
            product.setStock(product.getStock() - itemReq.getQuantity());
            productRepository.save(product);

            // Calculate costs
            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            orderTotal = orderTotal.add(itemTotal);

            // Build OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .priceAtOrder(product.getPrice())
                    .build();

            items.add(orderItem);
        }

        order.setTotalPrice(orderTotal);
        order.setOrderItems(items);

        // Save order (cascades to orderItems)
        Order savedOrder = orderRepository.save(order);
        return mapToOrderResponse(savedOrder);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        OrderStatus oldStatus = order.getStatus();

        if (oldStatus == newStatus) {
            return mapToOrderResponse(order);
        }

        // If transition is to CANCELLED, restore product stock
        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getOrderItems()) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found during order cancellation: id " + item.getProduct().getId()));
                
                // Add stock back
                product.setStock(product.getStock() + item.getQuantity());
                productRepository.save(product);
            }
        } 
        // If status was CANCELLED and is being set back to PENDING/COMPLETED, check and deduct stock
        else if (oldStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getOrderItems()) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found during order activation: id " + item.getProduct().getId()));

                if (product.getStock() < item.getQuantity()) {
                    throw new InsufficientStockException("Insufficient stock to re-activate order for product '" + product.getName() + "' (SKU: " + product.getSku() + "). Required: " + item.getQuantity() + ", Available: " + product.getStock());
                }

                // Deduct stock again
                product.setStock(product.getStock() - item.getQuantity());
                productRepository.save(product);
            }
        }

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        return mapToOrderResponse(updatedOrder);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productSku(item.getProduct().getSku())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .priceAtOrder(item.getPriceAtOrder())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer().getId())
                .customerName(order.getCustomer().getName())
                .customerEmail(order.getCustomer().getEmail())
                .orderDate(order.getOrderDate())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .items(itemResponses)
                .build();
    }
}
