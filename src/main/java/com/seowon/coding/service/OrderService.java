package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.OrderItem;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    

    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }


    /**
     * TODO #3: 구현 항목
     * 주어진 고객 정보로 새 Order를 생성
     * 지정된 Product를 주문에 추가
     * order 의 상태를 PENDING 으로 변경
     * orderDate 를 현재시간으로 설정
     * order 를 저장 (cascade 로 OrderItem 일괄 저장)
     * 각 Product 의 재고를 수정 (변경 감지로 자동 반영)
     * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.
     */
    public Order placeOrder(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        return null;
    }

    /**
     * TODO #4 (리팩토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 또는 Mapper 조회는 도메인 객체 밖에서 해결하여 의존을 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 재사용해도 됩니다.
     */
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {
        if (customerName == null || customerEmail == null) {
            throw new IllegalArgumentException("customer info required");
        }
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }

        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();


        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderProduct req : orderProducts) {
            Long pid = req.getProductId();
            int qty = req.getQuantity();

            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));
            if (req.checkQuantitty()) {
                throw new IllegalArgumentException("quantity must be positive: " + qty);
            }
            if (product.getStockQuantity() < qty) {
                throw new IllegalStateException("insufficient stock for product " + pid);
            }

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(qty)
                    .price(product.getPrice())
                    .build();
            order.getItems().add(item);

            product.decreaseStock(qty);
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        }

        BigDecimal shipping = subtotal.compareTo(new BigDecimal("100.00")) >= 0 ? BigDecimal.ZERO : new BigDecimal("5.00");
        BigDecimal discount = (couponCode != null && couponCode.startsWith("SALE")) ? new BigDecimal("10.00") : BigDecimal.ZERO;

        order.setTotalAmount(subtotal.add(shipping).subtract(discount));
        order.setStatus(Order.OrderStatus.PROCESSING);
        return orderRepository.save(order);
    }

    /**
     * TODO #5: [코드 리뷰] - 장시간 처리되는 작업을 간주하여 실시간 진행률 조회를 위한 트랜잭션 분리
     *   (MyBatis 사용시 JpaRepository 호출을 Mapper 호출로 치환했다고 가정)
     * - 시나리오: 일괄 배송 처리(장시간 작업이라고 가정함) 중 진행률을 저장하여 다른 사용자가 변화하는 진행률을 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 대량 주문 처리시 이슈, 예외 전파/롤백 범위, 가독성 등
     * - 적당한 수준에서 요구사항(기획)을 가정하여 리뷰를 상세히 작성하세요.
     */
    @Transactional
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));
        // jobId로 찾은 ProcessingStatus이 없을 경우, ProcessingStatus를 새로 생성 후 저장하고 해당 객체를 반환.

        ps.markRunning(orderIds == null ? 0 : orderIds.size());
        // orderIds가 null일 경우 0을 대입하는 방어 로직

        processingStatusRepository.save(ps);
        // 만들어진 ProcessingStatus 객체 저장

        int processed = 0;
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) {
            try {
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING));
                someHeavyOperation();
                // 중간 진행률 저장
                this.updateProgressRequiresNew(jobId, ++processed, orderIds.size());
            } catch (Exception e) {
            } // 예외 로직 필요
        } // 최대 주문 수가 제한되어 있지 않다면, 리스트의 사이즈에 따라 병목이 발생할 가능성 있음

        ps = processingStatusRepository.findByJobId(jobId).orElse(ps);
        ps.markCompleted();
        processingStatusRepository.save(ps);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressRequiresNew(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }

    // 오래 걸리는 작업 이라는 가정 시뮬레이션
    private void someHeavyOperation() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
    }

    /**
     * TODO #8: [동시성 제어]
     * 대규모 할인 행사 시 동일 상품에 대한 동시 주문이 폭주합니다.
     * 재고 차감 시 Race Condition이 발생하지 않도록 제어 로직을 완성하세요.
     * 1. 아래의 기본 구현(placeOrderWithLock)은 동시성 제어가 되지 않아 데이터 부정합이 발생할 수 있습니다.
     * 2. 전략 선택: Pessimistic Lock, Optimistic Lock, Distributed Lock 등 중 택 1 하여 로직을 수정하세요.
     * 3. 사유: 선택한 전략의 장단점 및 채택 사유를 주석으로 기술
     * 4. 검증: OrderConcurrencyTest의 테스트 케이스를 항상 통과해야 함 (Race Condition 방지 증명)
     */
    public Order placeOrderWithLock(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();

        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            int quantity = quantities.get(i);

            Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            product.decreaseStock(quantity);

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(quantity)
                    .price(product.getPrice())
                    .build();
            order.addItem(item);
        }

        return orderRepository.save(order);
    }

    /**
     * TODO #9: [아키텍처 - 결합도 해소]
     * 현재 주문 생성 로직 내에 재고 차감이 강하게 결합되어 있습니다.
     * Spring Event 등을 활용해 주문 도메인과 재고 로직의 의존성을 분리하세요.
     * - 방법: 주문 저장 후 ApplicationEventPublisher 를 통해 OrderCreatedEvent 를 발행하세요.
     * - 구현: InventoryEventListener 에서 이벤트를 구독하여 재고를 처리하도록 수정합니다.
     */
    public Order placeOrderWithEvent(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        // 1. 주문 엔티티 생성 및 저장
        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .items(new ArrayList<>())
                //.totalAmount(BigDecimal.ZERO)
                .build();

        ApplicationEventPublisher pub = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(Object event) {
                order.setTotalAmountZero();
            }
        };

        pub.publishEvent(order);

        // 2. 주문 상품(OrderItem) 생성 로직...
        Order savedOrder = orderRepository.save(order);


        return savedOrder;
    }

}