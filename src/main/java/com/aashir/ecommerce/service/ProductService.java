package com.aashir.ecommerce.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.dto.UpdateProductRequest;
import com.aashir.ecommerce.entity.Inventory;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.ProductStatus;
import com.aashir.ecommerce.exception.ProductNotFoundException;
import com.aashir.ecommerce.repository.InventoryRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.aashir.ecommerce.specification.ProductSpecification;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.aashir.ecommerce.dto.ProductPageDetailsResponse;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductMixingService productMixingService;
    private final ProductDetailsService productDetailsService;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository, ProductMixingService productMixingService, ProductDetailsService productDetailsService) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.productMixingService = productMixingService;
        this.productDetailsService = productDetailsService;
    }

    /*@CacheEvict(value = "products", key = "'all'")*/
    public CreateProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();

        product.setProductName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());

        Product savedProduct = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProduct(savedProduct);
        inventory.setQuantity(request.getStockQuantity());

        inventoryRepository.save(inventory);
       return mapToResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductPageDetailsResponse getProductPageDetails(Long id) {
        ProductPageDetailsResponse details =
                productDetailsService.getProductPageDetails(id);

        Integer quantity = inventoryRepository.findByProductId(id)
                .map(Inventory::getQuantity)
                .orElse(0);

        boolean inStock = quantity > 0
                && details.status() == ProductStatus.ACTIVE;

        return new ProductPageDetailsResponse(
                details.id(),
                details.productName(),
                details.price(),
                details.description(),
                details.imageUrl(),
                details.category(),
                details.status(),
                details.createdAt(),
                details.aboutThisItem(),
                details.specifications(),
                quantity,
                inStock
        );
    }

    /*@Cacheable(value = "products", key = "'all'")*/
    public Page<CreateProductResponse> getProductsAll(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);

            return products.map(product -> {
                CreateProductResponse createProductResponse = new CreateProductResponse();
                createProductResponse.setId(product.getId());
                createProductResponse.setName(product.getProductName());
                createProductResponse.setPrice(product.getPrice());
                createProductResponse.setCategory(product.getCategory());
                createProductResponse.setDescription(product.getDescription());
                createProductResponse.setImageUrl(product.getImageUrl());
                createProductResponse.setStatus(product.getStatus());
                createProductResponse.setCreatedAt(product.getCreatedAt());
                createProductResponse.setUpdatedAt(product.getUpdatedAt());
                return createProductResponse;
            });
    }

    public Page<CreateProductResponse> searchProducts(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String keyword,
            String sortBy,
            String direction,
            Pageable pageable
    ) {
        // 1. Build product filters
        Specification<Product> spec =
                ProductSpecification.hasStatus(ProductStatus.ACTIVE);

        boolean categorySelected =
                category != null
                        && !category.isBlank()
                        && !"All".equalsIgnoreCase(category);

        if (categorySelected) {spec = spec.and(ProductSpecification.hasCategory(category));}

        if (minPrice != null) {
            spec = spec.and(
                    ProductSpecification.priceGreaterThanOrEqualTo(minPrice)
            );
        }

        if (maxPrice != null) {
            spec = spec.and(ProductSpecification.priceLessThanOrEqualTo(maxPrice));
        }

        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(
                    ProductSpecification.keywordContains(keyword)
            );
        }

        // 2. Validate sorting options
        Sort.Direction sortDirection =
                "desc".equalsIgnoreCase(direction)
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        String safeSortBy = switch (
                sortBy == null ? "createdAt" : sortBy
                ) {
            case "price" -> "price";
            case "name" -> "productName";
            case "createdAt" -> "createdAt";
            default -> throw new IllegalArgumentException(
                    "Invalid sortBy. Allowed values: price, name, createdAt"
            );
        };

        // 3. Mix categories only for the default All-tab ordering
        boolean useMixedOrdering =
                !categorySelected
                        && "createdAt".equals(safeSortBy)
                        && sortDirection == Sort.Direction.DESC;

        if (useMixedOrdering) {
            // Retrieve all products matching the filters in stable order
            List<Product> matchingProducts = productRepository.findAll(
                    spec,
                    Sort.by(Sort.Direction.DESC, "createdAt")
                            .and(Sort.by(Sort.Direction.ASC, "id"))
            );

            // Delegate category mixing to the separate service
            List<Product> mixedProducts =
                    productMixingService.mixByCategory(matchingProducts);

            // 4. Apply pagination after mixing
            int start = (int) Math.min(
                    (long) pageable.getPageNumber() * pageable.getPageSize(),
                    mixedProducts.size()
            );

            int end = (int) Math.min(
                    (long) start + pageable.getPageSize(),
                    mixedProducts.size()
            );

            List<CreateProductResponse> content =
                    mixedProducts.subList(start, end)
                            .stream()
                            .map(this::mapToResponse)
                            .toList();

            return new org.springframework.data.domain.PageImpl<>(
                    content,
                    pageable,
                    mixedProducts.size()
            );
        }

        // 5. Normal database sorting and pagination for other requests
        Pageable sortedPageable =
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(sortDirection, safeSortBy)
                                .and(Sort.by(Sort.Direction.ASC, "id"))
                );

        return productRepository.findAll(spec, sortedPageable)
                .map(this::mapToResponse);
    }

   // @Cacheable(value = "products", key = "#id")
    public CreateProductResponse getProductById(Long id){
        Product product = productRepository.findById(id)
                .orElseThrow(()->new ProductNotFoundException(id));

        return mapToResponse(product);
    }

    private CreateProductResponse mapToResponse(Product product){
        CreateProductResponse response = new CreateProductResponse();

        response.setId(product.getId());
        response.setName(product.getProductName());
        response.setPrice(product.getPrice());
        response.setCategory(product.getCategory());
        response.setDescription(product.getDescription());
        response.setImageUrl(product.getImageUrl());
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        return response;
    }

    //Updating products

   /* @Caching(
            put = @CachePut(value = "products", key = "#id"),
            evict = @CacheEvict(value = "products", key = "'all'")
    )*/
    public CreateProductResponse  updateProductById(Long id,UpdateProductRequest request){

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(id)
                );

        if(request.getName()!=null){
            product.setProductName(request.getName());
        }
        if(request.getDescription()!=null){
            product.setDescription(request.getDescription());
        }
        if(request.getCategory()!=null){
            product.setCategory(request.getCategory());
        }
        if(request.getPrice()!=null){
            product.setPrice(request.getPrice());
        }

       Product updatedProducts =  productRepository.save(product);
        return mapToResponse(updatedProducts);
    }

   /* @Caching(
            put = @CachePut(value = "products", key = "#id"),
            evict = @CacheEvict(value = "products", key = "'all'")
    )*/
    public CreateProductResponse deleteProductById(Long id){
        Product product = productRepository.findById(id)
                .orElseThrow(()->new ProductNotFoundException(id));

        product.setStatus(ProductStatus.INACTIVE);
        Product delete = productRepository.save(product);
        return mapToResponse(delete);
    }

}
