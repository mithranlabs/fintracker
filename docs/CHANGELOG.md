# Finlytics Changelog

## [Unreleased] - Demo Preparation - 2026-10-11

### 🔧 Fixed
- **Navigation Regression:** Fixed Home links on authenticated pages to redirect to dashboard instead of public landing page
  - Modified: `transactions.html`, `upload.html`, `summary.html`, `add.html`
  - Changed `<a href="/">Home</a>` to `<a href="/dashboard-page">Dashboard</a>`
  - Added logout links to all authenticated pages
  - Added `logout()` function to `transactions.js`, `upload.js`, `summary.js`, `add.js`

### 📝 Documentation
- Created comprehensive project documentation in `docs/` directory:
  - `AUDIT_FINDINGS.md` — Complete repository audit report
  - `PROJECT_GUIDE.md` — Project overview, features, and architecture
  - `NAVIGATION_FIX.md` — Detailed navigation regression fix documentation
  - `DEMO_GUIDE.md` — Complete demonstration script with viva Q&A
  - `DEVELOPMENT.md` — Setup and development workflow guide
  - `CHANGELOG.md` — This file
  - `DEMO_READINESS.md` — Pre-demo verification checklist

### ⚠️ Known Issues (Not Fixed Yet)
- AI insights cache is global (not per-user)
- No duplicate transaction detection when uploading same statement twice
- Passwords stored in plaintext (no BCrypt hashing)
- Test endpoint `/upload/sbi/test` contains hardcoded local file path
- No comprehensive error feedback for partial PDF parsing failures

---

## [Recent] - 2026-10-XX (from Git History)

### Added
- **AI Insights Integration**
  - Migrated from WebClient to HttpURLConnection (removed Spring WebFlux dependency)
  - Changed model from `llama-3.1-8b-instant` to `openai/gpt-oss-20b`
  - Added custom JSON parsing method `extractContent()` to avoid Jackson dependency
  - Improved error handling for HTTP error responses

- **Landing Page Redesign**
  - New hero section with "Where Wealth, Meets Intelligence" tagline
  - Modern CSS animations and glassmorphism effects
  - 3D financial object visualization
  - Feature showcase sections

### Fixed
- JSON escaping in AI insights prompt construction
- HTTP error stream handling in Groq API calls

---

## [v0.0.1] - Initial Features (Historical)

### Added
- User authentication (register, login, logout) with session management
- PDF upload support:
  - Google Pay UPI statement parser
  - SBI YONO statement parser with password protection
- Automatic transaction categorization:
  - 8 built-in categories (Food, Travel, Shopping, Entertainment, Health, Education, Utilities, Finance)
  - 150+ keyword mappings
  - Persistent merchant rule engine
- Dashboard analytics:
  - Income, expense, savings summary (last 30 days)
  - Recent transactions display
  - Budget health bars
- Budget tracking:
  - Create monthly budgets per category
  - Visual progress bars with color coding
  - Overspending warnings
- Transaction management:
  - Manual transaction entry
  - Transaction editing (partial implementation)
  - Transaction deletion
  - Date range filtering (backend support)
  - Recurring transaction flags
- Upload history:
  - Track uploaded statements
  - View filename, date, transaction count
  - Delete upload records
- Summary charts:
  - Category breakdown
  - Income vs. expense comparison
  - Monthly trend analysis
  - Top merchant analytics

### Technical Implementation
- Spring Boot 4.0.3
- MySQL database with Hibernate ORM
- Apache PDFBox for PDF text extraction
- Tabula-Java for structured PDF parsing
- Thymeleaf templates for server-side rendering
- Vanilla JavaScript for frontend interactivity
- Chart.js for data visualization
- Groq API for AI-powered insights

---

## Versioning

This project does not follow semantic versioning as it's a final-year academic project.

Version milestones:
- **v0.0.1:** Core features implemented
- **v0.0.2:** AI insights + landing page
- **v0.0.3:** Navigation fixes + documentation (current, pre-demo)

---

## Migration Notes

### From No Documentation → Full Documentation

This release adds extensive documentation that did not exist before:

**Before:**
- README.md only
- No architecture diagrams
- No demo preparation guide
- No known issues documented

**After:**
- Complete docs/ directory with 7+ detailed documents
- Clear separation of concerns (project guide, API, database, demo, development)
- Known issues explicitly documented
- Navigation regression fully analyzed and fixed

### Database Migration

**No schema changes in this release.** All fixes are frontend-only.

Existing tables remain unchanged:
- users
- transactions
- categories
- budgets
- merchant_rules
- uploads

---

## Contributors

**Mithran M** — Full-stack development, PDF parsing, AI integration, documentation

---

## Acknowledgments

- Spring Boot team for excellent framework
- Apache PDFBox maintainers
- Groq API for LLM access
- IntelliJ IDEA for development environment

---

## Future Roadmap (Not in Current Release)

### Planned for Post-Demo
1. **Duplicate Detection** — Hash-based transaction deduplication
2. **Password Security** — BCrypt hashing implementation
3. **Per-User AI Cache** — Fix global cache bug
4. **Upload Feedback** — Detailed parsing success/failure report
5. **Transaction Edit Completion** — Finish category dropdown logic

### Long-term Vision
1. RAG-based conversational financial advisor
2. Additional bank support (HDFC, ICICI, Axis)
3. CSV export functionality
4. Email notifications for budget overspending
5. ML-based recurring transaction detection
6. Mobile PWA or native app
7. Investment portfolio tracking
8. Multi-currency support

---

## License

This is an academic project. No formal license applied.

---

**Last Updated:** October 11, 2026  
**Prepared By:** Kiro AI (autonomous audit and documentation)
