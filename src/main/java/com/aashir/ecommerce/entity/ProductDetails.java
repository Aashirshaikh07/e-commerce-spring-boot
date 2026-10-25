package com.aashir.ecommerce.entity;

import com.aashir.ecommerce.dto.SpecificationGroup;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(
        name = "product_details",
        indexes = {
                @Index(
                        name = "idx_product_details_product_id",
                        columnList = "product_id",
                        unique = true
                )
        }
)
public class ProductDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            unique = true
    )
    private Product product;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "about_this_item", columnDefinition = "json")
    private List<String> aboutThisItem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specifications", columnDefinition = "json")
    private List<SpecificationGroup> specifications;

    public ProductDetails() {
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public List<String> getAboutThisItem() {
        return aboutThisItem;
    }

    public void setAboutThisItem(List<String> aboutThisItem) {
        this.aboutThisItem = aboutThisItem;
    }

    public List<SpecificationGroup> getSpecifications() {
        return specifications;
    }

    public void setSpecifications(List<SpecificationGroup> specifications) {
        this.specifications = specifications;
    }
}