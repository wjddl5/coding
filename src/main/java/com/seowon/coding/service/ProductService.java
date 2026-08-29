package com.seowon.coding.service;

import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    
    private final ProductRepository productRepository;
    
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }
    
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
    
    public Product updateProduct(Long id, Product product) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        product.setId(id);
        return productRepository.save(product);
    }
    
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    /**
     * TODO #1 [JPA]: Repository를 사용하여 category 로 찾을 제품목록 제공
     *
     * TODO #1 [MyBatis]: ProductMapper.selectByCategory(category) 형태로 mapper 메소드를 정의하고
     *                     XML 또는 @Select 어노테이션으로 구현
     */
    @Transactional(readOnly = true)
    public List<Product> findProductsByCategory(String category) {
        return new ArrayList<>(productRepository.findByCategory(category));
    }

    /**
     * TODO #6: [리팩토링 - 전략 패턴 및 정확성]
     * 가격 변경 로직을 도메인 모델(Product)로 이동하고 다음을 개선하세요.
     * 1. BigDecimal 정확성: 모든 연산은 BigDecimal API를 사용하며, 소수점 처리 정책(RoundingMode)을 명시하세요.
     * 2. 확장성: 세율(Tax)이나 반올림 정책을 외부 정책 객체(Policy/Strategy)로 분리하여 DI로 주입받도록 설계하세요.
     * 3. 성능: 다건 처리 시 영속성 컨텍스트의 쓰기 지연(Write-behind)을 활용하여 DB I/O를 최소화하세요.
     */
    public void applyBulkPriceChange(List<Long> productIds, double percentage, boolean includeTax) {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("empty productIds");
        }
        for (Long id : productIds) {
            Product p = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

            p.changePrice(percentage, includeTax);
            productRepository.save(p);
        }
    }
}
