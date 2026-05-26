package com.jwcode.core.eval.model;

/**
 * Order 实体类
 */
public class Order {

    private Long id;
    private Long userId;
    private Double amount;

    public Order() {
    }

    public Order(Long id, Long userId, Double amount) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", userId=" + userId +
                ", amount=" + amount +
                '}';
    }
}
