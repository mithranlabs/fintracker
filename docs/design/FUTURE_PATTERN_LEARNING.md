# Future Pattern Learning Design

## Overview

This document outlines a future enhancement to enable the Universal Statement Parser to learn and improve its heuristic patterns from labeled examples, without modifying production parsers during failed parses.

## Current State

The current implementation uses:
- **Fixed heuristic patterns** (date formats, amount patterns, debit/credit indicators)
- **LLM fallback** for ambiguous statements
- **Manual pattern definition** in `UniversalStatementParserService`

## Proposed Pattern Learning System

### Architecture

```
User Labels Example
        ↓
Pattern Learning Service
        ↓
Generate Candidate Patterns
        ↓
Evaluate Against Test Set
        ↓
Score by Precision/Recall/Complexity
        ↓
Regression Test Suite
        ↓
Promote to Production (with versioning)
```

### Components

#### 1. Pattern Learning Service

**Responsibilities:**
- Accept labeled statement examples (user-provided ground truth)
- Generate candidate regex patterns from multiple examples
- Generalize patterns by finding common structure

**Example:**
```java
public class PatternLearningService {
    
    public List<CandidatePattern> learnPatternsFrom(
        List<LabeledStatement> examples
    ) {
        // Group similar transaction formats
        // Extract common structures
        // Generate candidate regexes
        // Return ranked candidates
    }
}
```

#### 2. Pattern Candidate Representation

```java
public class CandidatePattern {
    private String patternId;
    private Pattern regex;
    private String description;
    private double precision;
    private double recall;
    private int complexity; // Lower is better
    private List<String> coveredExamples;
    private LocalDateTime createdAt;
    private int version;
}
```

#### 3. Pattern Evaluation Engine

**Metrics:**
- **Precision:** How many matches are correct transactions?
- **Recall:** How many actual transactions does it find?
- **Complexity:** Pattern length, number of groups, backtracking risk
- **Generalizability:** Performance on held-out examples

**Scoring Function:**
```
Score = (Precision * 0.4) + (Recall * 0.4) + (Simplicity * 0.2)
Simplicity = 1 - (patternLength / maxLength)
```

#### 4. Pattern Versioning & Rollback

Store patterns in a versioned repository:

```java
public class PatternRepository {
    private Map<String, List<VersionedPattern>> patterns;
    
    public void savePattern(CandidatePattern pattern) {
        // Version it
        // Store with rollback capability
    }
    
    public void rollback(String patternId, int toVersion) {
        // Revert to previous version
    }
}
```

#### 5. Regression Test Suite

Before promoting any learned pattern:

```java
@Test
public void testLearnedPatternRegression() {
    // Run against all known good examples
    // Ensure no degradation in existing coverage
    // Require minimum confidence threshold
}
```

### Pattern Generation Algorithm

#### Phase 1: Extract Structure

For each labeled transaction:
```
Input:  "15/04/2024  Purchase  DR  500.00"
Labels: date="15/04/2024", desc="Purchase", type="DR", amount="500.00"

Extract positions and types:
- Position 0-10: DATE (dd/MM/yyyy)
- Position 12-20: TEXT
- Position 22-24: TYPE (DR/CR)
- Position 26-32: AMOUNT
```

#### Phase 2: Generalize

From multiple examples with same structure:
```
Example 1: "15/04/2024  Purchase  DR  500.00"
Example 2: "16/04/2024  Payment   DR  300.00"
Example 3: "17/04/2024  Deposit   CR 1000.00"

Generalized:
Pattern: (\d{2}/\d{2}/\d{4})\s+([A-Za-z]+)\s+(DR|CR)\s+([\d,]+\.\d{2})
Groups:  (date)              (description)  (type)    (amount)
```

#### Phase 3: Validate Bounds

Prevent catastrophic backtracking:
- Limit quantifier repetitions: `\w{1,50}` not `\w+`
- Avoid nested quantifiers: `(a+)+` is dangerous
- Use atomic groups where appropriate: `(?>...)`
- Set maximum pattern complexity threshold

### Learning Workflow

#### User-Initiated Learning

```java
@PostMapping("/admin/learn-pattern")
public ResponseEntity<?> submitLabeledStatement(
    @RequestBody LabeledStatementRequest request
) {
    // User uploads statement with labeled transactions
    
    PatternLearningService learner = new PatternLearningService();
    
    // Generate candidates
    List<CandidatePattern> candidates = 
        learner.learnPatternsFrom(request.getExamples());
    
    // Evaluate against test set
    List<ScoredPattern> scored = 
        evaluator.scoreAgainstTestSet(candidates);
    
    // Return top candidates for review
    return ResponseEntity.ok(scored);
}
```

#### Admin Review & Promotion

```java
@PostMapping("/admin/promote-pattern")
public ResponseEntity<?> promotePattern(
    @RequestParam String patternId,
    @RequestParam boolean runTests
) {
    if (runTests) {
        // Run regression suite
        TestResults results = regressionTester.test(patternId);
        
        if (!results.allPassed()) {
            return ResponseEntity.badRequest()
                .body("Pattern failed regression tests");
        }
    }
    
    // Promote to production
    patternRepository.promote(patternId);
    
    return ResponseEntity.ok("Pattern promoted");
}
```

### Constraints & Safety

#### Production Safety Rules

1. **Never auto-modify production parsers**
   - Learned patterns must be manually reviewed
   - Require explicit admin approval
   - Maintain audit log of all pattern changes

2. **Graceful degradation**
   - Failed parse must not corrupt existing parser state
   - New patterns are opt-in, not forced
   - Rollback capability always available

3. **Isolated testing**
   - Test learned patterns in sandbox environment
   - Require minimum dataset size (e.g., 50+ examples)
   - Validate on held-out test set before production

4. **Version control**
   - Git-like versioning for patterns
   - Tag stable versions
   - Rollback to any previous version

#### Example Safety Check

```java
public class PatternSafetyValidator {
    
    public boolean isSafe(CandidatePattern pattern) {
        // Check 1: Complexity
        if (pattern.getComplexity() > MAX_COMPLEXITY) {
            return false;
        }
        
        // Check 2: Catastrophic backtracking
        if (hasNestedQuantifiers(pattern.getRegex())) {
            return false;
        }
        
        // Check 3: Minimum precision/recall
        if (pattern.getPrecision() < 0.85 || 
            pattern.getRecall() < 0.75) {
            return false;
        }
        
        // Check 4: Test set validation
        if (!passesRegressionTests(pattern)) {
            return false;
        }
        
        return true;
    }
}
```

### Storage Schema

```sql
CREATE TABLE learned_patterns (
    id VARCHAR(50) PRIMARY KEY,
    pattern_regex TEXT NOT NULL,
    description VARCHAR(255),
    version INT NOT NULL,
    precision DECIMAL(5,4),
    recall DECIMAL(5,4),
    complexity INT,
    is_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    promoted_at TIMESTAMP,
    promoted_by VARCHAR(100),
    test_results JSON
);

CREATE TABLE pattern_examples (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pattern_id VARCHAR(50) REFERENCES learned_patterns(id),
    statement_text TEXT,
    labeled_transactions JSON,
    contributed_by VARCHAR(100),
    created_at TIMESTAMP
);
```

### Implementation Phases

#### Phase 1: Data Collection (No Code Changes)
- Add UI for users to label transactions
- Store labeled examples in database
- Build initial test dataset

#### Phase 2: Offline Learning
- Implement pattern generation algorithm
- Build evaluation engine
- Create pattern repository

#### Phase 3: Testing & Validation
- Comprehensive regression test suite
- Safety validators
- Pattern versioning system

#### Phase 4: Production Integration (Opt-In)
- Admin-only pattern promotion
- A/B testing framework
- Monitoring and alerting

### Monitoring & Metrics

Track pattern performance in production:

```java
public class PatternMetrics {
    private String patternId;
    private long successfulMatches;
    private long failedMatches;
    private double averageConfidence;
    private List<String> recentFailures;
    
    // Alert if precision drops below threshold
    public boolean needsReview() {
        double currentPrecision = 
            successfulMatches / (successfulMatches + failedMatches);
        return currentPrecision < 0.80;
    }
}
```

### Future Enhancements

1. **Active Learning**
   - Identify statements that need labeling
   - Prioritize high-value examples
   - Continuous improvement loop

2. **Transfer Learning**
   - Learn from multiple bank formats
   - Generalize across similar statement types
   - Build bank-agnostic patterns

3. **Ensemble Methods**
   - Combine multiple patterns
   - Weighted voting for ambiguous cases
   - Confidence calibration

## Conclusion

This pattern learning system enables continuous improvement while maintaining production stability. The key is **never auto-modifying production parsers** and always requiring explicit human review and regression testing before pattern promotion.

**Status:** Design document only. Not implemented in Phase 1.

**Next Steps:**
1. Implement data collection UI
2. Build pattern generation algorithm
3. Create comprehensive test suite
4. Deploy with admin-only access
