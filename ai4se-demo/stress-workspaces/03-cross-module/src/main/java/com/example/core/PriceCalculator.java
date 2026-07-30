package com.example.core;

/** BUG: tax rate applied as 0.05 instead of 0.10. */
public final class PriceCalculator {

    public int totalWithTaxCents(int netCents) {
        return netCents + (netCents * 5 / 100);
    }
}
