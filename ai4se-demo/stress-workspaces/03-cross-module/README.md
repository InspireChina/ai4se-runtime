# Cross-module stress sample

`core.PriceCalculator` undercharges tax; `api.OrderFacade` exposes totals to callers.
Both modules must stay consistent after the fix.
