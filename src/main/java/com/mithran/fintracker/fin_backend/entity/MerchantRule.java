package com.mithran.fintracker.fin_backend.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "merchant_rules")
public class MerchantRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String keyword;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    public MerchantRule() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }


    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}