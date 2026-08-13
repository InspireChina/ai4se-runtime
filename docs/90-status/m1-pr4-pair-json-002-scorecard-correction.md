# pair-json-002 scorecard correction

Arm B's normalized `missed_acceptance` is **4**: the four pointer-fragment acceptance checks were not proven after Analysis stopped at `FAILED_POLICY` (exit 50). The accompanying CSV is the corrected audit projection.

The original evidence directory is immutable and has not been edited, rerun, or rehashed; in particular, `requirement.sha256` is unchanged. This correction must be used for pair-level reporting instead of changing the historical `arm-b/scorecard.csv`.
