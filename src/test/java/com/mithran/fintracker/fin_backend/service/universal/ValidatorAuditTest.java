package com.mithran.fintracker.fin_backend.service.universal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit of ValidatorFaultInjectionTest baseline and detection logic.
 * 
 * Purpose: Independently verify baseline transactions and fault detection claims
 * before extending the validator or claiming production readiness.
 */
public class ValidatorAuditTest {

    /**
     * AUDIT FINDING 1: BASELINE VERIFICATION STATUS
     * 
     * The baseline transactions were NOT independently verified against the PDF.
     * Instead, they were extracted from GeometryDiagnosticTest output, which itself
     * is experimental code under test.
     * 
     * Evidence:
     * 1. Opening balance 905.67 was CALCULATED BACKWARD (1003.67 - 98.00), not read from PDF
     * 2. GeometryDiagnosticTest validates balance equations starting from i=1, skipping Row 0
     * 3. No independent PDF page/row coordinates recorded
     * 4. Descriptions truncated in test output ("Account UPI-..." may be incomplete)
     * 
     * Status: PARTIALLY VERIFIED
     * - Balance equations: Verified via GeometryDiagnosticTest (34/34 pass)
     * - Dates: Verified as calendar-valid (02-Sep-2026)
     * - Amounts/Balances: Consistent with parser output, NOT verified against PDF directly
     * - Opening balance: UNVERIFIED (calculated, not read from PDF)
     * - Descriptions: UNVERIFIED (may be truncated/incomplete)
     */
    @Test
    void auditBaseline_VerificationStatus() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT 1: BASELINE VERIFICATION STATUS");
        System.out.println("=".repeat(100));
        
        System.out.println("\n❌ FINDING: Opening balance NOT independently verified");
        System.out.println("   Source claim: 905.67");
        System.out.println("   Method: Backward calculation (1003.67 - 98.00)");
        System.out.println("   PDF verification: MISSING");
        System.out.println("   Recommendation: Manually inspect kotak_chandan.pdf Page 1 for \"Opening Balance\" field");
        
        System.out.println("\n⚠️  FINDING: Baseline derived from experimental parser");
        System.out.println("   Source: GeometryDiagnosticTest output (itself under test)");
        System.out.println("   Risk: Circular dependency (parser validates parser output)");
        System.out.println("   Recommendation: Manually verify at least 2 transactions against PDF");
        
        System.out.println("\n✅ FINDING: Balance chain internally consistent");
        System.out.println("   Evidence: GeometryDiagnosticTest reports 34/34 balance equations valid");
        System.out.println("   Note: Consistency != Correctness (garbage in, consistent garbage out)");
        
        // Mark test as documentation only - no assertions to fail
        assertTrue(true, "Audit findings documented");
    }

    /**
     * AUDIT FINDING 2: FALSE VERIFIED CLAIM
     * 
     * The report claims "False VERIFIED: 0" but does NOT test this property explicitly.
     * 
     * The original fault tests only assert:
     * - Detected faults should NOT be VERIFIED
     * - Undetected faults behavior varies
     * 
     * Missing: Explicit test that wrong description/balanced error cannot achieve VERIFIED.
     */
    @Test
    void auditFalseVerified_ExplicitCheck() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT 2: FALSE VERIFIED CLAIM");
        System.out.println("=".repeat(100));
        
        // Reconstruct Fault 7: Wrong description
        List<ValidatorFaultInjectionTest.Transaction> baseline = ValidatorFaultInjectionTest.getKotakBaselineFixtures();
        List<ValidatorFaultInjectionTest.Transaction> wrongDescription = new ArrayList<>(baseline);
        
        // Swap descriptions
        ValidatorFaultInjectionTest.Transaction tx1 = baseline.get(0).withDescription(baseline.get(1).description);
        ValidatorFaultInjectionTest.Transaction tx2 = baseline.get(1).withDescription(baseline.get(0).description);
        wrongDescription.set(0, tx1);
        wrongDescription.set(1, tx2);
        
        ValidatorFaultInjectionTest.TransactionValidator validator = new ValidatorFaultInjectionTest.TransactionValidator();
        ValidatorFaultInjectionTest.ValidationResult result = validator.validate(wrongDescription, ValidatorFaultInjectionTest.getKotakOpeningBalance());
        
        System.out.println("\nTest: Wrong description (Fault 7)");
        System.out.println("Result status: " + result.status);
        System.out.println("Errors: " + result.errors.size());
        System.out.println("Warnings: " + result.warnings.size());
        
        // Check if VERIFIED
        if (result.status == ValidatorFaultInjectionTest.ValidationResult.Status.VERIFIED) {
            System.out.println("\n❌ FALSE VERIFIED DETECTED!");
            System.out.println("   Wrong description received VERIFIED status");
            fail("False VERIFIED: Wrong description should not achieve VERIFIED status");
        } else {
            System.out.println("\n✅ No false VERIFIED (status: " + result.status + ")");
        }
        
        // Reconstruct Fault 10: Balanced error
        List<ValidatorFaultInjectionTest.Transaction> balancedError = new ArrayList<>();
        balancedError.add(baseline.get(0));
        
        BigDecimal newBalance2 = baseline.get(0).balance.subtract(new BigDecimal("1100.00"));
        ValidatorFaultInjectionTest.Transaction tx2Mod = baseline.get(1)
            .withDebit(new BigDecimal("1100.00"))
            .withBalance(newBalance2);
        balancedError.add(tx2Mod);
        
        BigDecimal newBalance3 = newBalance2.add(new BigDecimal("1100.00"));
        ValidatorFaultInjectionTest.Transaction tx3Mod = baseline.get(2)
            .withCredit(new BigDecimal("1100.00"))
            .withBalance(newBalance3);
        balancedError.add(tx3Mod);
        
        balancedError.add(baseline.get(3));
        balancedError.add(baseline.get(4));
        
        ValidatorFaultInjectionTest.ValidationResult result2 = validator.validate(balancedError, ValidatorFaultInjectionTest.getKotakOpeningBalance());
        
        System.out.println("\nTest: Balanced error (Fault 10)");
        System.out.println("Result status: " + result2.status);
        System.out.println("Errors: " + result2.errors.size());
        System.out.println("Warnings: " + result2.warnings.size());
        
        if (result2.status == ValidatorFaultInjectionTest.ValidationResult.Status.VERIFIED) {
            System.out.println("\n⚠️  Balanced error received VERIFIED status");
            System.out.println("   This is EXPECTED (undetectable fault)");
            System.out.println("   But demonstrates validator blindspot");
        }
    }

    /**
     * AUDIT FINDING 3: DETECTION VS IDENTIFICATION
     * 
     * The report conflates "detecting an error exists" with "identifying its exact cause".
     * 
     * Example: Fault 2 (duplicate transaction)
     * - Balance equation fails at Tx3
     * - Duplicate warning appears
     * - Which one detected the fault? Both? Does it matter?
     * 
     * Finding: Multiple rules can fire for one fault, but report counts this as single detection.
     */
    @Test
    void auditDetection_MultipleRules() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT 3: DETECTION VS IDENTIFICATION");
        System.out.println("=".repeat(100));
        
        // Reconstruct Fault 2: Duplicate transaction
        List<ValidatorFaultInjectionTest.Transaction> baseline = ValidatorFaultInjectionTest.getKotakBaselineFixtures();
        List<ValidatorFaultInjectionTest.Transaction> duplicate = new ArrayList<>(baseline);
        duplicate.add(2, baseline.get(1)); // Duplicate Tx2 at position 3
        
        ValidatorFaultInjectionTest.TransactionValidator validator = new ValidatorFaultInjectionTest.TransactionValidator();
        ValidatorFaultInjectionTest.ValidationResult result = validator.validate(duplicate, ValidatorFaultInjectionTest.getKotakOpeningBalance());
        
        System.out.println("\nFault 2: Duplicate transaction");
        System.out.println("Balance equations checked: " + result.balanceEquationsChecked);
        System.out.println("Balance equations passed: " + result.balanceEquationsPassed);
        System.out.println("Errors: " + result.errors.size());
        System.out.println("Warnings: " + result.warnings.size());
        
        boolean balanceDetected = result.balanceEquationsPassed < result.balanceEquationsChecked;
        boolean duplicateWarning = result.warnings.stream().anyMatch(w -> w.contains("duplicate"));
        
        System.out.println("\nDetection methods:");
        System.out.println("  Balance equation failure: " + (balanceDetected ? "YES" : "NO"));
        System.out.println("  Duplicate warning: " + (duplicateWarning ? "YES" : "NO"));
        
        if (balanceDetected && duplicateWarning) {
            System.out.println("\n✅ Multiple rules detected same fault");
            System.out.println("   Question: Which rule gets credit? Both?");
            System.out.println("   Report: Counts as single detection (correct)");
        }
        
        assertTrue(balanceDetected || duplicateWarning, "Fault should be detected by at least one rule");
    }

    /**
     * AUDIT FINDING 4: WARNINGS VS ERRORS VS REJECTION
     * 
     * The baseline has 2 warnings but status != FAILED.
     * The report claims "False rejections: 0" but warnings could be considered soft rejections.
     * 
     * Question: Are warnings acceptable on clean baseline?
     * Answer: YES - warnings indicate possible issues, not definitive errors.
     * 
     * But: Report should clarify this distinction.
     */
    @Test
    void auditFalseRejections_WarningVsError() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT 4: WARNINGS VS ERRORS VS REJECTION");
        System.out.println("=".repeat(100));
        
        List<ValidatorFaultInjectionTest.Transaction> baseline = ValidatorFaultInjectionTest.getKotakBaselineFixtures();
        ValidatorFaultInjectionTest.TransactionValidator validator = new ValidatorFaultInjectionTest.TransactionValidator();
        ValidatorFaultInjectionTest.ValidationResult result = validator.validate(baseline, ValidatorFaultInjectionTest.getKotakOpeningBalance());
        
        System.out.println("\nBaseline validation:");
        System.out.println("Status: " + result.status);
        System.out.println("Errors: " + result.errors.size());
        System.out.println("Warnings: " + result.warnings.size());
        
        if (result.warnings.size() > 0) {
            System.out.println("\nWarnings on baseline:");
            result.warnings.forEach(w -> System.out.println("  - " + w));
        }
        
        System.out.println("\n✅ FINDING: Baseline has warnings but no errors");
        System.out.println("   Status: " + result.status + " (not FAILED)");
        System.out.println("   Interpretation: Warnings = possible issues, not definitive errors");
        System.out.println("   Legitimate: UPI reversals flagged as potential duplicates");
        System.out.println("   False rejection: Only if status == FAILED on clean data");
        
        assertNotEquals(ValidatorFaultInjectionTest.ValidationResult.Status.FAILED, result.status,
            "Baseline should not be FAILED (errors would indicate false rejection)");
        assertEquals(0, result.errors.size(), "Baseline should have zero errors");
    }

    /**
     * AUDIT FINDING 5: DATE SHIFT DETECTION CLAIM
     * 
     * Report claims Fault 6 (date shift) was detected via chronological ordering.
     * This is TRUE but has important limitations.
     * 
     * Test: Shift date forward without breaking chronology.
     */
    @Test
    void auditDateShift_ChronologyLimitation() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT 5: DATE SHIFT DETECTION LIMITATION");
        System.out.println("=".repeat(100));
        
        List<ValidatorFaultInjectionTest.Transaction> baseline = ValidatorFaultInjectionTest.getKotakBaselineFixtures();
        
        // Original: All dates are 02-Sep-2026
        // Shift: Change all dates to 03-Sep-2026 (chronology preserved)
        List<ValidatorFaultInjectionTest.Transaction> shiftedAll = new ArrayList<>();
        for (ValidatorFaultInjectionTest.Transaction tx : baseline) {
            shiftedAll.add(tx.withDate("03-Sep-2026"));
        }
        
        ValidatorFaultInjectionTest.TransactionValidator validator = new ValidatorFaultInjectionTest.TransactionValidator();
        ValidatorFaultInjectionTest.ValidationResult result = validator.validate(shiftedAll, ValidatorFaultInjectionTest.getKotakOpeningBalance());
        
        System.out.println("\nTest: All dates shifted 02-Sep → 03-Sep");
        System.out.println("Chronology: PRESERVED (all same date)");
        System.out.println("Result status: " + result.status);
        System.out.println("Chronological warnings: " + 
            result.warnings.stream().filter(w -> w.contains("before")).count());
        
        boolean chronologyDetected = result.warnings.stream().anyMatch(w -> w.contains("before"));
        
        if (!chronologyDetected) {
            System.out.println("\n✅ CONFIRMED: Chronology check only detects ordering violations");
            System.out.println("   Limitation: Cannot detect wrong date if chronology preserved");
            System.out.println("   Example: All dates wrong by same offset = undetectable");
        } else {
            System.out.println("\n⚠️  Unexpected: Chronology violation detected");
        }
        
        System.out.println("\n📊 FAULT 6 CLAIM VERIFICATION:");
        System.out.println("   Original claim: Date shift detected via chronology");
        System.out.println("   Accurate: YES, but only when chronology violated");
        System.out.println("   Complete: NO, date shifts preserving order undetectable");
    }

    /**
     * AUDIT FINDING 6: BALANCED ERROR TEST VALIDITY
     * 
     * Fault 10 claims to test balanced errors, but must verify:
     * 1. Running balances correctly recalculated
     * 2. Balance equations actually pass
     * 3. Final balance matches original
     */
    @Test
    void auditBalancedError_EquationCheck() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT 6: BALANCED ERROR TEST VALIDITY");
        System.out.println("=".repeat(100));
        
        List<ValidatorFaultInjectionTest.Transaction> baseline = ValidatorFaultInjectionTest.getKotakBaselineFixtures();
        
        // Reconstruct Fault 10
        List<ValidatorFaultInjectionTest.Transaction> faulty = new ArrayList<>();
        faulty.add(baseline.get(0)); // Tx1: unchanged
        
        // Tx2: debit 1000 → 1100, recalculate balance
        BigDecimal bal1 = baseline.get(0).balance; // 1003.67
        BigDecimal bal2 = bal1.subtract(new BigDecimal("1100.00")); // -96.33
        ValidatorFaultInjectionTest.Transaction tx2 = baseline.get(1)
            .withDebit(new BigDecimal("1100.00"))
            .withBalance(bal2);
        faulty.add(tx2);
        
        // Tx3: credit 1000 → 1100, recalculate balance
        BigDecimal bal3 = bal2.add(new BigDecimal("1100.00")); // 1003.67
        ValidatorFaultInjectionTest.Transaction tx3 = baseline.get(2)
            .withCredit(new BigDecimal("1100.00"))
            .withBalance(bal3);
        faulty.add(tx3);
        
        faulty.add(baseline.get(3));
        faulty.add(baseline.get(4));
        
        // Manual verification
        System.out.println("\nManual balance equation verification:");
        System.out.println("  Tx1 → Tx2: " + bal1 + " - " + tx2.debit + " = " + bal2 + 
            " (expected: " + bal2 + ") " + (bal2.compareTo(tx2.balance) == 0 ? "✅" : "❌"));
        System.out.println("  Tx2 → Tx3: " + bal2 + " + " + tx3.credit + " = " + bal3 + 
            " (expected: " + bal3 + ") " + (bal3.compareTo(tx3.balance) == 0 ? "✅" : "❌"));
        System.out.println("  Final balance: " + bal3 + " (original: " + baseline.get(2).balance + ")");
        
        boolean balancesMatch = bal3.compareTo(baseline.get(2).balance) == 0;
        System.out.println("  Balanced: " + (balancesMatch ? "YES ✅" : "NO ❌"));
        
        // Run validator
        ValidatorFaultInjectionTest.TransactionValidator validator = new ValidatorFaultInjectionTest.TransactionValidator();
        ValidatorFaultInjectionTest.ValidationResult result = validator.validate(faulty, ValidatorFaultInjectionTest.getKotakOpeningBalance());
        
        System.out.println("\nValidator result:");
        System.out.println("  Status: " + result.status);
        System.out.println("  Balance equations: " + result.balanceEquationsPassed + "/" + result.balanceEquationsChecked);
        System.out.println("  Errors: " + result.errors.size());
        
        if (result.balanceEquationsPassed == result.balanceEquationsChecked && result.errors.isEmpty()) {
            System.out.println("\n✅ CONFIRMED: Balanced error undetectable");
            System.out.println("   All balance equations pass despite wrong amounts");
        } else {
            System.out.println("\n❌ TEST FLAW: Balanced error detected");
            System.out.println("   Running balances may not be correctly recalculated");
            fail("Balanced error test invalid - validator detected the fault");
        }
    }

    /**
     * AUDIT FINDING 7: DISTINCTION BETWEEN DUPLICATE AND REVERSAL
     * 
     * Baseline has legitimate UPI reversals (same transaction ID, opposite directions).
     * Validator flags these as "potential duplicates" (warning).
     * 
     * Question: How to distinguish genuine duplicate from reversal?
     * Current logic: Same date+debit+credit+balance = duplicate
     * Issue: Reversals have same ID but opposite directions, not flagged as exact duplicates.
     */
    @Test
    void auditDuplicateDetection_ReversalDistinction() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT 7: DUPLICATE VS REVERSAL DISTINCTION");
        System.out.println("=".repeat(100));
        
        List<ValidatorFaultInjectionTest.Transaction> baseline = ValidatorFaultInjectionTest.getKotakBaselineFixtures();
        
        // Baseline has:
        // Tx2: Debit 1000, Balance 3.67, Description "Account UPI-624573058291"
        // Tx3: Credit 1000, Balance 1003.67, Description "Account UPI-624573058291"
        
        System.out.println("\nBaseline reversals:");
        System.out.println("  Tx2: " + baseline.get(1));
        System.out.println("  Tx3: " + baseline.get(2));
        System.out.println("  Same UPI ID: Account UPI-624573058291");
        System.out.println("  Opposite directions: Debit vs Credit");
        
        ValidatorFaultInjectionTest.TransactionValidator validator = new ValidatorFaultInjectionTest.TransactionValidator();
        ValidatorFaultInjectionTest.ValidationResult result = validator.validate(baseline, ValidatorFaultInjectionTest.getKotakOpeningBalance());
        
        long duplicateWarnings = result.warnings.stream().filter(w -> w.contains("duplicate")).count();
        System.out.println("\nDuplicate warnings: " + duplicateWarnings);
        
        if (duplicateWarnings > 0) {
            System.out.println("Sample warnings:");
            result.warnings.stream()
                .filter(w -> w.contains("duplicate"))
                .forEach(w -> System.out.println("  - " + w));
        }
        
        System.out.println("\n✅ FINDING: Reversals flagged as 'potential duplicates'");
        System.out.println("   Current logic: date|debit|credit|balance signature");
        System.out.println("   Reversals have different debit/credit → different signatures");
        System.out.println("   But validator still flags them (likely matching balance+date?)");
        System.out.println("\n⚠️  Limitation: Cannot distinguish reversal from genuine duplicate");
        System.out.println("   Requires: Transaction ID matching or semantic analysis");
    }

    /**
     * META-TEST: Validator independence from mutation logic
     * 
     * Ensures validator doesn't just validate the mutation process itself.
     */
    @Test
    void metaTest_ValidatorIndependence() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("META-TEST: VALIDATOR INDEPENDENCE");
        System.out.println("=".repeat(100));
        
        // Create completely new transactions (not using baseline mutation)
        List<ValidatorFaultInjectionTest.Transaction> independent = Arrays.asList(
            new ValidatorFaultInjectionTest.Transaction(
                "01-Jan-2026",
                "Independent Test 1",
                null,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                99, 99
            ),
            new ValidatorFaultInjectionTest.Transaction(
                "02-Jan-2026",
                "Independent Test 2",
                new BigDecimal("50.00"),
                null,
                new BigDecimal("50.00"),
                99, 99
            )
        );
        
        ValidatorFaultInjectionTest.TransactionValidator validator = new ValidatorFaultInjectionTest.TransactionValidator();
        ValidatorFaultInjectionTest.ValidationResult result = validator.validate(independent, new BigDecimal("0.00"));
        
        System.out.println("\nIndependent transaction set:");
        System.out.println("  Opening: 0.00");
        System.out.println("  Tx1: +100.00 credit = 100.00 balance");
        System.out.println("  Tx2: -50.00 debit = 50.00 balance");
        System.out.println("\nValidator result:");
        System.out.println("  Status: " + result.status);
        System.out.println("  Balance equations: " + result.balanceEquationsPassed + "/" + result.balanceEquationsChecked);
        System.out.println("  Errors: " + result.errors.size());
        
        assertEquals(2, result.balanceEquationsPassed, "Both balance equations should pass");
        assertEquals(0, result.errors.size(), "No errors on clean independent data");
        
        System.out.println("\n✅ CONFIRMED: Validator operates independently");
        System.out.println("   Not coupled to baseline mutation logic");
    }

    /**
     * Summary of audit findings for report correction.
     */
    @Test
    void auditSummary() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT SUMMARY - REQUIRED REPORT CORRECTIONS");
        System.out.println("=".repeat(100));
        
        System.out.println("\n1. BASELINE VERIFICATION STATUS:");
        System.out.println("   ❌ Opening balance 905.67 = UNVERIFIED (calculated backward)");
        System.out.println("   ⚠️  Transactions sourced from experimental GeometryDiagnosticTest");
        System.out.println("   ✅ Balance chain internally consistent (34/34 equations)");
        System.out.println("   Required: Change \"manually verified\" to \"derived from parser output\"");
        
        System.out.println("\n2. DETECTION RATE:");
        System.out.println("   Current: \"8/10 fault categories detected (80%)\"");
        System.out.println("   Issue: Sample size n=1 per category (except date shift)");
        System.out.println("   Required: Add \"(10 test cases, 1 per category)\" to avoid over-generalization");
        
        System.out.println("\n3. FALSE VERIFIED:");
        System.out.println("   Current: \"False VERIFIED: 0\"");
        System.out.println("   Finding: Not explicitly tested in original suite");
        System.out.println("   Status: Confirmed via audit - wrong description gets WARNINGS, not VERIFIED");
        System.out.println("   Required: Add \"(verified via audit)\" caveat");
        
        System.out.println("\n4. CHRONOLOGICAL DETECTION:");
        System.out.println("   Current: \"Date shift detected (unexpected success!)\"");
        System.out.println("   Limitation: Only detects when chronology violated");
        System.out.println("   Required: Add limitation note about synchronized date shifts");
        
        System.out.println("\n5. WARNINGS VS ERRORS:");
        System.out.println("   Current: \"False rejections: 0\"");
        System.out.println("   Clarification: Baseline has 2 warnings but status != FAILED");
        System.out.println("   Required: Define \"false rejection\" as FAILED status, not warnings");
        
        System.out.println("\n6. BALANCED ERROR:");
        System.out.println("   Current: \"Undetectable\"");
        System.out.println("   Verification: Confirmed - all balance equations pass");
        System.out.println("   Required: Add equation verification showing recalculated balances");
        
        System.out.println("\n7. DUPLICATE VS REVERSAL:");
        System.out.println("   Issue: Baseline reversals flagged as \"potential duplicates\"");
        System.out.println("   Required: Clarify warnings are expected for legitimate reversals");
    }
}
