# Phase 3: Universal Parser Integration Guide

## Overview

Phase 3 integrates the isolated universal statement parser into the Finlytics backend and frontend with a preview-and-confirm workflow. Users can upload any bank statement (PDF or TXT), see parsed transactions with confidence scores, manually correct ambiguous directions, and save to the database.

## Architecture

### Workflow
1. **Upload** → User uploads statement file (PDF/TXT)
2. **Parse** → Universal parser extracts transactions with confidence scores
3. **Preview** → Frontend displays transactions in editable table
4. **Correct** → User manually fixes UNKNOWN directions via dropdown
5. **Save** → Backend validates and persists to database

### Key Design Decisions
- **No direct persistence**: Parse endpoint returns preview only, does not save to database
- **Session-based corrections**: Direction corrections happen client-side before save
- **Validation at save time**: Backend rejects transactions with UNKNOWN direction
- **Existing parsers preserved**: SBI and GPay parsers remain unchanged and functional

## Backend API

### 1. Parse Statement (Preview)

**Endpoint**: `POST /upload/universal`

**Authentication**: Required (session-based)

**Request**:
```
Content-Type: multipart/form-data

Form fields:
- file: MultipartFile (PDF or TXT)
```

**Response** (200 OK):
```json
{
  "success": true,
  "message": "Successfully parsed 15 transactions",
  "totalTransactions": 15,
  "needsReviewCount": 3,
  "heuristicCount": 15,
  "llmCount": 0,
  "transactions": [
    {
      "date": "01 Jan 2026",
      "amount": 1500.50,
      "direction": "DEBIT",
      "narration": "UPI/Amazon/Purchase",
      "parsingMethod": "HEURISTIC",
      "confidence": 0.9,
      "needsReview": false
    },
    {
      "date": "02 Jan 2026",
      "amount": 500.00,
      "direction": "UNKNOWN",
      "narration": "Payment received",
      "parsingMethod": "HEURISTIC",
      "confidence": 0.6,
      "needsReview": true
    }
  ]
}
```

**Error Response** (200 OK with success=false):
```json
{
  "success": false,
  "message": "No transactions found in the statement. Please check the file format.",
  "totalTransactions": 0,
  "needsReviewCount": 0,
  "heuristicCount": 0,
  "llmCount": 0,
  "transactions": []
}
```

**Authentication Error** (401 Unauthorized):
```
Not logged in
```

### 2. Save Confirmed Transactions

**Endpoint**: `POST /upload/universal/save`

**Authentication**: Required (session-based)

**Request**:
```json
Content-Type: application/json

{
  "transactions": [
    {
      "date": "01 Jan 2026",
      "amount": 1500.50,
      "direction": "DEBIT",
      "narration": "UPI/Amazon/Purchase"
    },
    {
      "date": "02 Jan 2026",
      "amount": 500.00,
      "direction": "CREDIT",
      "narration": "Payment received"
    }
  ],
  "replaceExisting": false,
  "fileName": "statement-jan-2026.pdf"
}
```

**Response** (200 OK):
```
Successfully saved 2 transactions
```

**Validation Errors** (400 Bad Request):
```
Cannot save transaction with UNKNOWN direction. Please correct all transactions before saving.
```
```
Invalid direction: INVALID. Must be DEBIT or CREDIT.
```
```
No transactions to save
```
```
Invalid date format: 2026-01-01
```

**Authentication Error** (401 Unauthorized):
```
Not logged in
```

### Field Definitions

#### UniversalTransactionDTO
| Field | Type | Description | Required |
|-------|------|-------------|----------|
| date | String | Date in format "dd MMM yyyy" (e.g., "01 Jan 2026") | Yes (preview), Yes (save) |
| amount | Double | Transaction amount | Yes |
| direction | String | "DEBIT", "CREDIT", or "UNKNOWN" | Yes |
| narration | String | Transaction description | Yes |
| parsingMethod | String | "HEURISTIC", "LLM", or "FAILED" | Yes (preview only) |
| confidence | Double | Confidence score 0.0 to 1.0 | Yes (preview only) |
| needsReview | Boolean | True if direction is UNKNOWN | Yes (preview only) |

#### Direction Values
- **DEBIT**: Money out (expense, withdrawal, payment)
- **CREDIT**: Money in (income, deposit, received)
- **UNKNOWN**: Cannot determine direction (requires manual correction)

#### Parsing Method Values
- **HEURISTIC**: Parsed using regex patterns and keyword detection (fast, deterministic)
- **LLM**: Parsed using Groq fallback (slower, requires API key)
- **FAILED**: Parsing failed (no transactions found)

## Frontend Integration

### HTML Structure

The universal parser UI is added to `/upload-page` (`upload.html`) as a new section between the GPay and SBI upload cards.

**Key Elements**:
- File input (`universalFile`) accepting PDF and TXT files
- Replace checkbox (`universalReplace`)
- Parse button triggering `uploadUniversal()`
- Status message (`universalStatus`)
- Preview section (`universalPreview`) with:
  - Summary (`previewSummary`)
  - Scrollable table (`previewTableBody`)
  - Review warning banner (`reviewWarning`)
  - Save button triggering `saveUniversalTransactions()`

### JavaScript Functions

**`uploadUniversal()`**:
- Validates file selection
- Calls `POST /upload/universal`
- Displays preview table with confidence badges
- Enables direction correction dropdowns

**`displayUniversalPreview(data)`**:
- Renders transaction table with formatting
- Shows confidence badges (green ≥80%, yellow ≥60%, red <60%)
- Creates direction dropdowns for each transaction
- Displays review warning if UNKNOWN directions exist

**`updateDirection(index, newDirection)`**:
- Updates transaction direction in memory
- Recalculates needsReview count
- Updates warning banner visibility

**`saveUniversalTransactions()`**:
- Validates no UNKNOWN directions remain
- Calls `POST /upload/universal/save`
- Clears preview on success
- Refreshes upload history

### Styling

The universal parser section uses existing CSS classes from `style.css`:
- `.container` - Card wrapper
- `.upload-card` - Section card
- `.form-group` - Form field groups
- `.btn` - Action buttons
- `.hint` - Helper text

Inline styles are used for table formatting and dynamic elements (confidence badges, warnings, dropdowns).

## Testing

### Running Tests

**Integration Tests**:
```bash
.\mvnw.cmd test -Dtest=UniversalUploadIntegrationTest
```

**Full Test Suite** (includes parser unit tests):
```bash
.\mvnw.cmd test
```

### Test Coverage

The integration test suite (`UniversalUploadIntegrationTest`) covers:

1. **Parse Endpoint**:
   - ✅ Valid file with transactions (200 OK with data)
   - ✅ Empty file (200 OK with error message)
   - ✅ No authentication (401 Unauthorized)

2. **Save Endpoint**:
   - ✅ Valid transactions (200 OK, persisted to DB)
   - ✅ UNKNOWN direction rejection (400 Bad Request)
   - ✅ Invalid direction rejection (400 Bad Request)
   - ✅ Empty transaction list (400 Bad Request)
   - ✅ No authentication (401 Unauthorized)

**Test Results**: 48/48 tests passing
- 8 integration tests (new)
- 40 parser unit tests (existing from Phases 1-2)

### Manual Testing Checklist

- [ ] Upload valid bank statement → See parsed transactions
- [ ] Upload empty file → See error message
- [ ] Upload unsupported format → See error message
- [ ] Change UNKNOWN direction to DEBIT → Warning disappears
- [ ] Try to save with UNKNOWN → See validation error
- [ ] Save corrected transactions → See success message
- [ ] Check transaction history → See new upload record
- [ ] Verify SBI parser still works
- [ ] Verify GPay parser still works
- [ ] Test without login → See 401 error

## Confidence Score Interpretation

| Confidence | Badge Color | Interpretation |
|------------|-------------|----------------|
| ≥ 0.80 | Green | High confidence - likely correct |
| 0.60 - 0.79 | Yellow | Medium confidence - review recommended |
| < 0.60 | Red | Low confidence - verify carefully |

**Confidence Factors**:
- Date and amount extraction: core requirements (weight: 30%)
- Direction detection: keyword presence (weight: 40%)
- Narration quality: completeness (weight: 20%)
- Validation: plausibility checks (weight: 10%)

## Limitations and Future Work

### Current Limitations
1. **No persistent corrections**: Direction corrections are not stored for future pattern learning (Phase 4 feature)
2. **No bulk edit**: Users must correct each UNKNOWN transaction individually
3. **No preview edit**: Cannot edit date/amount/narration in preview (only direction)
4. **No LLM pattern learning**: Groq fallback does not learn from user corrections yet

### Phase 4 Roadmap
- Persist direction corrections with merchant/narration mapping
- Implement pattern learning: merchant name → direction inference
- Add merchant rule creation from corrections
- Enable bulk direction assignment for similar transactions

## Security Notes

✅ **Authentication enforced**: Both endpoints require active session

✅ **User isolation**: Transactions are scoped to authenticated user

✅ **Input validation**: Direction values strictly validated at save time

✅ **No persistent UNKNOWN**: Cannot save unresolved transactions

✅ **Existing parsers isolated**: SBI and GPay endpoints remain unchanged

⚠️ **No CSRF protection**: Consider adding CSRF tokens for production

⚠️ **No file size limits**: Large uploads may timeout (add multipart config)

⚠️ **No rate limiting**: API can be called repeatedly (add throttling)

## Troubleshooting

### "No transactions found"
- **Cause**: Statement format not recognized by heuristics, LLM unavailable
- **Fix**: Check statement has date/amount columns, verify Groq API key configured

### "Cannot save transaction with UNKNOWN direction"
- **Cause**: User attempted to save without correcting all UNKNOWN transactions
- **Fix**: Review all transactions, manually set direction via dropdown

### "LLM client unavailable"
- **Cause**: Groq API key not configured in `application.properties`
- **Fix**: Add `gemini.api.key=your-groq-key` to enable LLM fallback

### Tests fail with "Connection refused"
- **Cause**: MySQL database not running or wrong credentials
- **Fix**: Start MySQL, verify connection string in `application.properties`

## Files Created/Modified

### Backend
**Created**:
- `src/main/java/com/mithran/fintracker/fin_backend/dto/UniversalTransactionDTO.java`
- `src/main/java/com/mithran/fintracker/fin_backend/dto/UniversalParseResponseDTO.java`
- `src/main/java/com/mithran/fintracker/fin_backend/dto/UniversalSaveRequestDTO.java`
- `src/main/java/com/mithran/fintracker/fin_backend/dto/DirectionCorrectionDTO.java` (unused, for Phase 4)
- `src/test/java/com/mithran/fintracker/fin_backend/controller/UniversalUploadIntegrationTest.java`

**Modified**:
- `src/main/java/com/mithran/fintracker/fin_backend/controller/UploadController.java`
  - Added `/upload/universal` endpoint
  - Added `/upload/universal/save` endpoint
  - Added `resolveCategory()` helper method

### Frontend
**Modified**:
- `src/main/resources/templates/upload.html`
  - Added universal parser section
  - Added preview table HTML
  
- `src/main/resources/static/js/upload.js`
  - Added `uploadUniversal()` function
  - Added `displayUniversalPreview()` function
  - Added `updateDirection()` function
  - Added `saveUniversalTransactions()` function

### Documentation
**Created**:
- `PHASE_3_INTEGRATION_GUIDE.md` (this file)

## API Quick Reference

```bash
# Parse statement (preview)
curl -X POST http://localhost:8080/upload/universal \
  -H "Cookie: JSESSIONID=..." \
  -F "file=@statement.pdf"

# Save transactions
curl -X POST http://localhost:8080/upload/universal/save \
  -H "Cookie: JSESSIONID=..." \
  -H "Content-Type: application/json" \
  -d '{
    "transactions": [...],
    "replaceExisting": false,
    "fileName": "statement.pdf"
  }'
```

## Support

For issues or questions:
1. Check parser unit test logs for parsing failures
2. Review backend console for stack traces
3. Inspect browser console for frontend errors
4. Verify authentication session is active
5. Confirm existing SBI/GPay parsers still work

## Version History

- **Phase 3 (Current)**: Integration with preview-and-confirm workflow
- **Phase 2**: Parser strategy evaluation (Docling/NuExtract3)
- **Phase 1**: Isolated universal parser implementation
