# Finlytics Documentation Index

This directory contains technical guides, architecture specifications, design proposals, and phase reports for the Finlytics personal finance platform.

---

## 1. Developer Guides & System Manuals (`docs/`)

- **[CHANGELOG.md](CHANGELOG.md)**: Detailed record of features, migrations, and bug fixes across project development phases.
- **[DEMO_GUIDE.md](DEMO_GUIDE.md)**: Step-by-step walkthrough script for running live presentations and project demonstrations.
- **[DEMO_READINESS.md](DEMO_READINESS.md)**: Technical evaluation checklist and readiness criteria for exhibition day.
- **[DEVELOPMENT.md](DEVELOPMENT.md)**: Developer guide covering local environment setup, architecture overview, and commands.
- **[HANDOFF_SUMMARY.md](HANDOFF_SUMMARY.md)**: Architecture summary, implementation status, and project handoff notes.
- **[NAVIGATION_FIX.md](NAVIGATION_FIX.md)**: Troubleshooting guide for frontend page routing and controller navigation mappings.
- **[PROJECT_GUIDE.md](PROJECT_GUIDE.md)**: Comprehensive high-level project guide, database schema specifications, and feature catalog.
- **[UNIVERSAL_PARSER_QUICK_START.md](UNIVERSAL_PARSER_QUICK_START.md)**: Quick-start operational guide for testing and running the universal statement parser.

---

## 2. Architecture & Design (`docs/design/`)

- **[FUTURE_PATTERN_LEARNING.md](design/FUTURE_PATTERN_LEARNING.md)**: Architecture proposal for adaptive, learning-based regex and coordinate extraction strategies.

---

## 3. Evaluation & Phase Reports (`docs/reports/`)

- **[FIRST_CHUNK_LLM_FIX_REPORT.md](reports/FIRST_CHUNK_LLM_FIX_REPORT.md)**: Root cause analysis and resolution for first-chunk LLM extraction failures.
- **[GEOMETRY_FIX_VERIFICATION_REPORT.md](reports/GEOMETRY_FIX_VERIFICATION_REPORT.md)**: Verification of coordinate-based text token extraction improvements.
- **[PDF_TOKEN_COVERAGE_AUDIT_REPORT.md](reports/PDF_TOKEN_COVERAGE_AUDIT_REPORT.md)**: Audit verifying token accounting to prevent missed or hallucinated statement amounts.
- **[PHASE_1_1_COMPLETION_REPORT.md](reports/PHASE_1_1_COMPLETION_REPORT.md)**: Completion summary for Phase 1.1 isolated statement parser test suites.
- **[PHASE_1_2_READINESS_AUDIT_REPORT.md](reports/PHASE_1_2_READINESS_AUDIT_REPORT.md)**: Pre-integration audit of universal parser heuristics and edge case handling.
- **[PHASE_2A_FIXTURE_TEST_REPORT.md](reports/PHASE_2A_FIXTURE_TEST_REPORT.md)**: Completion report for the isolated sample-statement testing harness.
- **[PHASE_2B_PARSER_STRATEGY_EVALUATION.md](reports/PHASE_2B_PARSER_STRATEGY_EVALUATION.md)**: Comparative evaluation of heuristic parsing vs. coordinate-based approaches.
- **[PHASE_3.1_SECURITY_AUDIT_REPORT.md](reports/PHASE_3.1_SECURITY_AUDIT_REPORT.md)**: Security and integration audit for session validation and multi-tenant data safety.
- **[PHASE_3_COMPLETION_REPORT.md](reports/PHASE_3_COMPLETION_REPORT.md)**: Phase 3 milestone report covering frontend preview UI and backend universal upload endpoints.
- **[PHASE_3_INTEGRATION_GUIDE.md](reports/PHASE_3_INTEGRATION_GUIDE.md)**: Developer integration manual for universal parser controller and UI wiring.

---

*Note: Raw diagnostic reports, un-anonymized statement audits, and temporary extraction artifacts containing real bank statement metadata are archived in `docs/private/` (gitignored).*
