package com.aashir.ecommerce.dto;

import com.aashir.ecommerce.entity.ProductStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class UpdateProductRequest {
    @Size(max = 100,message = "Product name must not larger than 100 characters")
    private String name;

    @Size(max = 2000,message = "Product description must not larger than 2000 characters")
    private String description;

    @DecimalMin(value = "0.01",message = "Price must be gretere than 0")
    private BigDecimal price;

    @Min(value = 0,message = "Stock quantity cannot be negative")

    @Size(max = 50,message = "Product category must not larger than 50 characters")
    private String category;

//    @Enumerated(EnumType.STRING)
//    @Size(max = 50,message = "Product description must not larger than 50 characters")
//    private ProductStatus status;

    @Min(value = 0,message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

//    public ProductStatus getStatus() {
//        return status;
//    }
//
//    public void productStatus(ProductStatus status) {
//        this.status = status;
//    }
}
