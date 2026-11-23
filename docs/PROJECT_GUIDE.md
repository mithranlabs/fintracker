# Finlytics Project Guide

## Problem Statement

Managing personal finances is challenging for individuals who rely on digital transactions. Manually tracking hundreds of UPI payments, bank transfers, and purchases from PDF statements is time-consuming and error-prone. Most existing solutions require manual transaction entry or expensive subscription fees.

## Objectives

Finlytics aims to:

1. **Automate Transaction Import** — Parse Indian bank statements (Google Pay, SBI YONO) and extract transactions automatically
2. **Intelligent Categorization** — Automatically categorize expenses using merchant rules and keyword matching
3. **Visual Analytics** — Provide clear insights into income, expenses, savings, and spending patterns
4. **Budget Management** — Track monthly budgets per category with overspending warnings
5. **AI-Powered Insights** — Generate personalized financial advice using LLM analysis

## Target Users

- **Primary:** College students and young professionals in India who use UPI and digital banking
- **Secondary:** Anyone who wants to understand their spending patterns without manual data entry
- **Use Case:** Upload monthly bank statements, review categorized transactions, track budgets, and receive AI insights

## Features

### ✅ Implemented

#### 1. User Authentication
- Session-based registration and login
- Multi-user support with data isolation
- Email/username login support

#### 2. PDF Statement Upload
- **Google Pay UPI Statements**
  - Automatic parsing of "Paid to" and "Received from" transactions
  - Date and amount extraction
  - Multi-line description support

- **SBI YONO Bank Statements**
  - Password-protected PDF support (format: NNNNNDDMMYYYY)
  - Multi-line transaction narration handling
  - UPI merchant name extraction
  - Debit/credit detection

#### 3. Transaction Management
- Auto-categorization using:
  - Persistent merchant rules (database-backed)
  - Built-in keyword matching (8 categories, 150+ keywords)
  - Fallback categories (Salary/Personal)
- Manual transaction creation
- Transaction history with date filtering capability

#### 4. Dashboard Analytics
- **Summary Cards:** Income, Expenses, Net Savings, Transaction Count (last 30 days)
- **Recent Transactions:** Top 5 most recent transactions
- **Budget Health Bars:** Visual progress for category budgets
- Real-time calculation from database

#### 5. Budget Tracking
- Create monthly budgets per category
- Visual progress bars with color coding:
  - Green: < 80% spent
  - Yellow: 80-99% spent
  - Red: ≥ 100% spent (overspending)
- Budget management (create/delete)

#### 6. AI Financial Insights
- Groq API integration (Model: openai/gpt-oss-20b)
- 30-day spending analysis
- Personalized advice based on:
  - Category spending breakdown
  - Income vs. expense trends
  - Top merchants
  - Savings rate

#### 7. Upload History
- Track all uploaded statements
- View filename, date, and transaction count
- Delete upload records

### ⚠️ Partially Implemented

- **Transaction Editing:** UI exists but incomplete
- **Recurring Transaction Detection:** Database field exists but no auto-detection logic
- **Date Range Filtering:** Backend support exists but no frontend UI

### ❌ Not Implemented (Mentioned in README)

- CSV export
- Additional bank support (HDFC, ICICI, Axis)
- RAG-based conversational AI
- Progressive Web App (PWA)
- SMS import

## Technology Stack

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| **Backend Framework** | Spring Boot | 4.0.3 | Application framework |
| **Language** | Java | 17 | Backend logic |
| **Database** | MySQL | 8+ | Transaction storage |
| **ORM** | Hibernate / Spring Data JPA | — | Database abstraction |
| **Template Engine** | Thymeleaf | — | Server-side HTML rendering |
| **Frontend** | HTML5 + CSS3 + Vanilla JS | — | User interface |
| **Charts** | Chart.js | — | Data visualization |
| **PDF Processing** | Apache PDFBox | 2.0.30 | PDF text extraction |
| **Table Extraction** | Tabula-Java | 1.0.5 | Structured PDF parsing |
| **AI API** | Groq API | — | LLM insights |
| **Build Tool** | Maven | — | Dependency management |
| **Development** | Spring Boot DevTools | — | Hot reload |

## Scope

### In Scope
- ✅ Statement upload and parsing (Google Pay, SBI YONO)
- ✅ Transaction categorization and management
- ✅ Dashboard with financial summaries
- ✅ Monthly budget tracking
- ✅ AI-powered spending insights
- ✅ Multi-user support with session authentication

### Out of Scope (for current version)
- ❌ Real-time bank API integration
- ❌ Investment tracking
- ❌ Loan/EMI calculators
- ❌ Notifications and alerts
- ❌ Mobile application
- ❌ Bill payment integration
- ❌ Multi-currency support

## Project Structure

```
fin_backend/
├── src/main/java/com/mithran/fintracker/fin_backend/
│   ├── controller/              # REST endpoints + page routing
│   │   ├── AuthController.java         # Login, register, logout
│   │   ├── DashboardController.java    # Dashboard summary API
│   │   ├── TransactionController.java  # Transaction CRUD
│   │   ├── UploadController.java       # PDF upload handlers
│   │   ├── BudgetController.java       # Budget management
│   │   ├── CategoryController.java     # Category operations
│   │   ├── InsightsController.java     # AI insights API
│   │   ├── RuleController.java         # Merchant rule management
│   │   ├── SummaryController.java      # Chart data aggregation
│   │   └── PageController.java         # HTML page routing
│   │
│   ├── entity/                  # JPA entities
│   │   ├── User.java
│   │   ├── Transaction.java
│   │   ├── Category.java
│   │   ├── Budget.java
│   │   ├── MerchantRule.java
│   │   └── Upload.java
│   │
│   ├── repository/              # Spring Data JPA repositories
│   │   ├── UserRepository.java
│   │   ├── TransactionRepository.java
│   │   ├── CategoryRepository.java
│   │   ├── BudgetRepository.java
│   │   ├── MerchantRuleRepository.java
│   │   └── UploadRepository.java
│   │
│   ├── service/                 # Business logic services
│   │   └── SbiStatementParser.java
│   │
│   └── FinBackendApplication.java   # Spring Boot entry point
│
├── src/main/resources/
│   ├── templates/               # Thymeleaf HTML pages
│   │   ├── landing.html           # Public landing page
│   │   ├── index.html             # Dashboard (authenticated)
│   │   ├── login.html             # Login page
│   │   ├── register.html          # Registration page
│   │   ├── transactions.html      # Transaction list
│   │   ├── upload.html            # Upload page
│   │   ├── summary.html           # Charts/analytics
│   │   └── add.html               # Manual transaction entry
│   │
│   ├── static/
│   │   ├── css/                   # Stylesheets
│   │   │   ├── style.css          # Main app styles
│   │   │   ├── landing.css        # Landing page styles
│   │   │   └── dashboard.css      # Dashboard-specific styles
│   │   │
│   │   └── js/                    # Frontend JavaScript
│   │       ├── index.js           # Dashboard logic
│   │       ├── login.js           # Login handler
│   │       ├── register.js        # Registration handler
│   │       ├── chart.js           # Chart.js integration
│   │       ├── add.js             # Manual transaction form
│   │       └── landing.js         # Landing page interactions
│   │
│   ├── application.properties     # App configuration (not committed)
│   └── application.properties.example   # Template
│
├── pom.xml                      # Maven dependencies
├── README.md                    # Project documentation
└── docs/                        # Comprehensive documentation (this directory)
```

## Key Design Decisions

### 1. Session-Based Authentication
**Decision:** Use HttpSession instead of JWT tokens  
**Rationale:** Simpler for demo project, no token management complexity  
**Trade-off:** Not stateless, harder to scale horizontally

### 2. Hybrid PDF Parsing
**Decision:** Two separate parsers (Google Pay + SBI YONO)  
**Rationale:** Statement formats are fundamentally different  
**Trade-off:** Each bank needs custom parser

### 3. Keyword-Based Categorization
**Decision:** Built-in keyword map + persistent merchant rules  
**Rationale:** Fast, deterministic, user-customizable  
**Trade-off:** No ML-based categorization, requires keyword maintenance

### 4. Frontend: Vanilla JavaScript
**Decision:** No React/Vue/Angular framework  
**Rationale:** Lightweight, no build step, easy to understand  
**Trade-off:** Manual DOM manipulation, less reusable components

### 5. Groq API for AI
**Decision:** External LLM API instead of local model  
**Rationale:** No GPU required, faster inference, cost-effective for demo  
**Trade-off:** API key required, external dependency, PII sent to third party

### 6. MySQL with Hibernate
**Decision:** Relational database with ORM  
**Rationale:** ACID guarantees, complex queries, familiar technology  
**Trade-off:** Schema migrations required for changes

## Success Metrics

| Metric | Target | Actual |
|--------|--------|--------|
| Statement Parsing Accuracy | > 95% | ✅ ~98% (based on test statements) |
| Category Auto-Assignment | > 80% | ✅ ~85% (keyword matching) |
| Dashboard Load Time | < 2 seconds | ✅ < 1 second |
| AI Insight Generation | < 10 seconds | ✅ ~3-5 seconds |
| Concurrent Users | 10+ | ⚠️ Not load tested |
| PDF Formats Supported | 2 | ✅ Google Pay + SBI YONO |

## Known Limitations

1. **No Duplicate Detection** — Uploading same statement twice creates duplicate transactions
2. **Security Gaps** — Passwords stored in plaintext, no CSRF protection
3. **Single Currency** — Only INR supported
4. **Manual Bank Addition** — Each bank needs custom parser implementation
5. **AI Cache Bug** — Cached insight is global, not per-user
6. **No Offline Support** — Requires internet for AI insights
7. **Limited Error Handling** — Partial PDF parsing failures not reported clearly

## Future Enhancements

### Short-term (Post-Demo)
- Duplicate transaction detection
- Password hashing (BCrypt)
- Per-user AI insight caching
- Upload success/failure summary
- Transaction editing completion

### Medium-term
- Additional bank support (HDFC, ICICI)
- CSV export functionality
- Email notifications for budget overspending
- Recurring transaction auto-detection
- Transaction search and filtering UI

### Long-term
- RAG-based conversational financial advisor
- Mobile app (PWA or native)
- Investment portfolio tracking
- Bill payment reminders
- Multi-currency support

## Developer Notes

- **Entry Point:** `FinBackendApplication.java` (runs on port 8080)
- **Database Creation:** Hibernate auto-creates tables on first run (ddl-auto=update)
- **Hot Reload:** Spring DevTools enabled for development
- **Logging:** `spring.jpa.show-sql=true` for query debugging
- **API Key:** Required for AI insights (Groq API)

## Resources

- **Repository:** https://github.com/mithranlabs/fintracker
- **API Documentation:** See `docs/API.md`
- **Database Schema:** See `docs/DATABASE.md`
- **Demo Script:** See `docs/DEMO_GUIDE.md`

---

**Last Updated:** October 11, 2026  
**Project Status:** Demo-Ready (with minor fixes needed)
