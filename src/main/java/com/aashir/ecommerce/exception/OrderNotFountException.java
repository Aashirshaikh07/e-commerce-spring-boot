package com.aashir.ecommerce.exception;

public class OrderNotFountException extends RuntimeException{
    public OrderNotFountException(Long id) {
        super("Order Not Found With id" +id);
    }
}
