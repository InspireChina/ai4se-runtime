package com.example.api;

import com.example.core.PriceCalculator;

/** Facade over core pricing — callers expect 10% tax. */
public final class OrderFacade {

    private final PriceCalculator calculator = new PriceCalculator();

    public int quoteTotalCents(int netCents) {
        return calculator.totalWithTaxCents(netCents);
    }

    public String taxPolicy() {
        return "tax=5%";
    }
}
