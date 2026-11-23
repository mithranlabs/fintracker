# Sample Statement Fixtures

This directory contains synthetic bank statement fixtures for testing the universal statement parser.

## Important Notes

- **All data is synthetic** - no real personal or financial information
- Account numbers are masked (XXXX1234 format)
- Names are fictional (Acme Corp, John Doe, etc.)
- Amounts and dates are fabricated for testing purposes
- These fixtures test various statement formats and edge cases

## Fixture Descriptions

### 1. separate_columns_statement.txt
Tests statements with separate Debit, Credit, and Balance columns.
- Bank: ACME BANK LIMITED
- Transactions: 6 (excluding opening/closing balance lines)
- Features: Column-based layout, standard format

### 2. single_column_with_markers.txt
Tests statements with a single amount column plus DR/CR type markers.
- Bank: GLOBAL BANK PLC
- Transactions: 8
- Features: DR/CR/DEBIT markers, single amount column

### 3. indian_formatting_statement.txt
Tests Indian number formatting (lakhs system: 1,25,000.00).
- Bank: BHARAT NATIONAL BANK
- Transactions: 6 (excluding opening/closing balance)
- Features: Indian comma grouping, large amounts

### 4. multiline_narration_statement.txt
Tests transaction descriptions spanning multiple lines.
- Bank: METRO CITY BANK
- Transactions: 5
- Features: Multi-line narration, detailed transaction info

### 5. large_transaction_small_balance.txt
Tests the critical case where transaction amount > running balance.
- Bank: SUNRISE BANK
- Transactions: 6 (excluding opening balance)
- Features: Large deposits with small balances, tests amount extraction

### 6. repeated_amounts_same_date.txt
Tests legitimate repeated transactions with identical amounts.
- Bank: CITY BANK EXPRESS
- Transactions: 9
- Features: Multiple transactions with same amount on same date

### 7. malformed_ambiguous_statement.txt
Tests error handling for malformed, ambiguous, and invalid data.
- Bank: PROBLEMATIC BANK
- Valid transactions: 2-3
- Features: Invalid dates, missing data, wrong formats, ambiguous indicators

## Usage

These fixtures are loaded by the `SampleStatementFixtureTest` class, which:
1. Parses each fixture through the UniversalStatementParserService
2. Asserts exact expected results (transaction count, amounts, dates, directions)
3. Validates warnings and confidence scores
4. Tests edge cases and error handling

## Maintenance

When adding new fixtures:
1. Use only synthetic data
2. Document the expected parsing results
3. Add corresponding test cases in SampleStatementFixtureTest
4. Ensure the fixture tests a specific scenario not covered by existing tests
