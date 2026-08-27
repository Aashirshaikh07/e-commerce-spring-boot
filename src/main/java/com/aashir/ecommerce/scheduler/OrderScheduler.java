package com.aashir.ecommerce.scheduler;

import com.aashir.ecommerce.entity.Order;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.repository.OrderRepository;
import com.aashir.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.expiration.minutes}")
    private long expirationMinutes;

    @Scheduled(fixedRate = 20000)
    public void cancelExpiredOrders(){
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expirationMinutes);

        List<Order> expiredOrders =
                orderRepository.findByStatusAndCreatedAtBefore(
                        OrderStatus.PENDING,
                        cutoff
                );

        for(Order order : expiredOrders){
            orderService.updateOrderStatus(
                    order.getId(),
                    OrderStatus.CANCELLED
            );

            log.info(
                    "Expired order cancelled.orderNumber={}",
                    order.getOrderNumber()
            );
        }
    }

}
