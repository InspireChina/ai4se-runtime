# FeatureFlags sample

## raw

Add a FeatureFlags toggle that disables beta UI when flag is off.

## goal

Provide a FeatureFlags API used by the sample app to gate beta UI.

## in_scope

- FeatureFlags class and config binding
- Unit-testable isEnabled(name) check

## out_of_scope

- Remote feature-flag service
- Admin UI

## acceptance

- FeatureFlags.isEnabled("beta-ui") returns false when config disables it
- FeatureFlags.isEnabled("beta-ui") returns true when config enables it
- Missing flag name returns false (safe default)
