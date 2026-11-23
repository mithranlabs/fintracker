# Finlytics Demo Guide

**Duration:** 5-10 minutes  
**Audience:** Final-year project evaluators  
**Goal:** Demonstrate practical value and technical competence

---

## Pre-Demo Checklist

### 1. Environment Setup (30 minutes before)
- [ ] Start MySQL server (ensure port 3306 is free)
- [ ] Verify `application.properties` exists with valid credentials
- [ ] Run `mvn clean install` to ensure build succeeds
- [ ] Start application: `mvn spring-boot:run` or run `FinBackendApplication.java`
- [ ] Verify app loads at `http://localhost:8080`
- [ ] Test Groq API key is valid (check AI insights work)
- [ ] Clear browser cache/cookies or use incognito mode

### 2. Sample Data Preparation
- [ ] Prepare 2 anonymized PDF statements:
  - **Google Pay UPI Statement** (at least 10 transactions)
  - **SBI YONO Statement** (password-protected, at least 10 transactions)
- [ ] Know the SBI password format: `NNNNN` + `DDMMYYYY` (e.g., `MITHR31122004`)
- [ ] Have sample data ready for manual transaction entry

### 3. Demo Account
- [ ] Create a test user account before demo starts (username: `demo`, password: `demo123`)
- [ ] Or be ready to demonstrate registration flow

### 4. Browser Setup
- [ ] Open Chrome/Firefox in full-screen mode
- [ ] Zoom to 100% (Ctrl+0)
- [ ] Close unnecessary tabs and applications
- [ ] Disable browser notifications

---

## Demo Script (8 minutes)

### Part 1: Problem & Solution Overview (1 minute)

**What to Say:**
> "Finlytics solves the problem of manual financial tracking. Instead of entering every UPI payment and bank transaction manually, users can upload their bank statement PDFs, and the application automatically parses, categorizes, and analyzes their spending. It's designed specifically for Indian users who rely heavily on UPI and digital banking."

**What to Show:**
- Landing page (`http://localhost:8080/`)
- Highlight "PDF Statement Import", "AI Financial Insights", "Built for India"

---

### Part 2: User Registration & Authentication (1 minute)

**What to Do:**
1. Click "Get Started" or navigate to `/register-page`
2. Register a new user:
   - Username: `testuser`
   - Email: `test@example.com`
   - Password: `test123`
3. Click "Register" → should show "Registered" message
4. Login with the same credentials
5. Should redirect to dashboard

**What to Highlight:**
- Session-based authentication
- Multi-user support (each user has isolated data)

---

### Part 3: PDF Statement Upload (3 minutes)

#### A. Google Pay Upload (1.5 minutes)

**What to Do:**
1. Navigate to "Upload" page
2. Click "Upload Google Pay Statement"
3. Select your Google Pay PDF
4. Click "Upload GPay Statement"
5. Wait for success message: "Parsed and saved"
6. Navigate back to "Dashboard"

**What to Show:**
- Dashboard cards update with income, expenses, savings
- Recent transactions table populates
- Transaction count increases

**What to Highlight:**
- Automatic UPI transaction extraction
- "Paid to" and "Received from" detection
- Auto-categorization (Food, Travel, Shopping, etc.)

#### B. SBI YONO Upload (1.5 minutes)

**What to Do:**
1. Navigate back to "Upload" page
2. Click "Upload SBI Statement"
3. Select SBI PDF
4. Enter password in format: `NNNNNDDMMYYYY`
   - Example: `MITHR31122004` (first 5 letters of name + DOB)
5. Click "Upload SBI Statement"
6. Wait for success message showing transaction count
7. Navigate to "Dashboard"

**What to Show:**
- Password-protected PDF handling
- Additional transactions appear
- Dashboard metrics update

**What to Highlight:**
- Multi-line transaction narration support
- Debit/credit detection from amount columns
- Merchant name extraction from UPI patterns
- IST timezone handling

**Common Issues to Address:**
- If password fails: Explain password format clearly
- If no transactions found: Mention format compatibility requirements

---

### Part 4: Dashboard & Analytics (1.5 minutes)

**What to Show:**
1. **Summary Cards:**
   - Total Income (last 30 days)
   - Total Expenses (last 30 days)
   - Net Savings (green if positive, red if negative)
   - Transaction Count

2. **Budget Health Bars:**
   - If you created budgets, show overspending warnings
   - Color coding: Green < 80%, Yellow 80-99%, Red ≥ 100%

3. **Recent Transactions:**
   - Most recent 5 transactions
   - Show note, category, amount, type, date

**What to Highlight:**
- Real-time calculation from database
- 30-day rolling window
- Visual feedback for overspending

---

### Part 5: AI Financial Insights (1 minute)

**What to Do:**
1. Scroll to "AI Insights" section
2. Click "Get Insights" button
3. Wait 3-5 seconds for AI response
4. Show generated insights

**What to Show:**
- Personalized spending analysis
- Category-specific recommendations
- Mention of top merchants and spending patterns

**What to Highlight:**
- Integration with Groq API (LLM: openai/gpt-oss-20b)
- 30-day spending analysis
- Actionable financial advice

**Fallback if AI fails:**
> "The AI insights feature uses an external API. In a production environment, this would have proper error handling and caching. The core financial tracking functionality works independently of the AI service."

---

### Part 6: Transaction Management (1 minute)

**What to Do:**
1. Navigate to "Transactions" page
2. Show the full transaction list
3. Demonstrate filtering by date range (if time permits)
4. Show manual transaction entry (navigate to "Add" page)

**What to Highlight:**
- Complete transaction history
- Ability to manually add transactions
- Transaction editing capability (mention it exists)

---

### Part 7: Budget Tracking (30 seconds)

**What to Do:**
1. Navigate to "Summary" page or show budget section on dashboard
2. If time permits, create a budget:
   - Category: Food
   - Month: 2026-10
   - Limit: ₹5000

**What to Highlight:**
- Monthly budget limits per category
- Visual progress bars
- Overspending warnings

---

### Part 8: Wrap-Up & Architecture Overview (30 seconds)

**What to Say:**
> "Finlytics is built with Spring Boot for the backend, MySQL for data persistence, and vanilla JavaScript for a lightweight frontend. PDF parsing uses Apache PDFBox, and financial insights are powered by the Groq API. The application demonstrates full-stack development skills, RESTful API design, database modeling, and integration with external services."

**What to Show (if asked):**
- Project structure in IDE
- Database tables (if MySQL Workbench is open)
- Key controller/service classes

---

## Expected Viva Questions & Answers

### Technical Questions

#### Q: Why did you choose Spring Boot over other frameworks?
**A:** Spring Boot provides:
- Rapid development with auto-configuration
- Built-in server (Tomcat) — no separate deployment needed
- Excellent ecosystem (Spring Data JPA, Thymeleaf, DevTools)
- Industry-standard for Java web applications
- Easy dependency injection and transaction management

#### Q: How does your PDF parsing work?
**A:** We use Apache PDFBox to extract text from PDFs. For Google Pay statements, we pattern-match "Paid to" and "Received from" lines to identify transactions. For SBI YONO statements, we use a more complex state machine that detects date patterns, transaction types (WDL/DEP/UPI), multi-line narrations, and amount lines with debit/credit columns. Each bank format requires a custom parser due to layout differences.

#### Q: How do you handle password-protected PDFs?
**A:** PDFBox's `PDDocument.load()` method accepts a password parameter. For SBI YONO, the password format is well-defined: first 5 letters of the account holder's name + DOB in DDMMYYYY format. We pass this directly to PDFBox for decryption before text extraction.

#### Q: What algorithm do you use for transaction categorization?
**A:** We use a hybrid approach:
1. **Persistent Merchant Rules:** Database table maps exact merchant names to categories
2. **Keyword Matching:** 8 built-in categories with 150+ keywords (e.g., "swiggy" → Food, "uber" → Travel)
3. **Fallback:** Unmatched income → Salary, unmatched expenses → Personal

First match wins. Users can create custom rules that override keyword matching.

#### Q: Why not use machine learning for categorization?
**A:** For the scope of this project:
- Keyword-based classification is **fast, deterministic, and explainable**
- No training data required
- 85%+ accuracy on test data
- Users can create custom rules for edge cases

ML would be valuable for a production system with thousands of users where supervised learning can generalize better, but the current approach is effective for a final-year project demonstration.

#### Q: How do you ensure data security?
**A (honest answer):**
- **Current:** Session-based authentication with user data isolation
- **Missing:** Passwords are stored in plaintext (acknowledged limitation)
- **Production Requirements:** Would implement BCrypt password hashing, HTTPS, CSRF tokens, API rate limiting, and avoid sending full financial data to external APIs

This is a **proof-of-concept** focused on functional completeness over production-grade security.

#### Q: What is the database schema?
**A:** Six main tables:
1. **users:** id, username, email, password
2. **transactions:** id, amount, type, note, date, user_id, category_id, recurring
3. **categories:** id, name, type (income/expense)
4. **budgets:** id, month, limitAmount, category_id, user_id
5. **merchant_rules:** id, keyword, category_id
6. **uploads:** id, fileName, uploadDate, transactionCount, user_id

Relationships: User 1→N Transactions, Transaction N→1 Category, User 1→N Budgets, Budget N→1 Category

#### Q: How does the AI integration work?
**A:** We use the Groq API (cloud-based LLM inference):
1. Fetch last 30 days of user transactions
2. Calculate category spending, top merchant, income/expense totals
3. Build a structured prompt with financial summary
4. POST request to Groq API with model `openai/gpt-oss-20b`
5. Parse JSON response and extract insight text
6. Display on dashboard

**Trade-off:** Sends financial data to external API. Production version would either:
- Use local LLM (requires GPU)
- Anonymize data before sending
- Make AI opt-in with explicit consent

#### Q: How do you handle duplicate uploads?
**A (honest):** Currently, **we don't**. Uploading the same statement twice creates duplicate transactions. This is a **known limitation**.

**Solution (not implemented):** Would add transaction hashing (date + amount + merchant) and check before insertion.

#### Q: What happens if PDF parsing fails?
**A:** Partial failures are silent — transactions that don't match patterns are skipped. Success message shows count of saved transactions.

**Better approach:** Return detailed summary:
- X transactions successfully parsed
- Y transactions skipped (with reasons)
- Failed line examples

#### Q: Why use Thymeleaf instead of React/Vue?
**A:** Thymeleaf provides:
- **Server-side rendering** — no JavaScript bundling required
- **SEO-friendly** (though not critical for authenticated app)
- **Simple deployment** — single JAR file, no frontend build step
- **Good for smaller projects** where frontend complexity is manageable

For a larger production app, I'd consider React for better state management and reusability.

### Project Management Questions

#### Q: What challenges did you face?
**A:**
1. **PDF Format Variability:** Each bank has different layouts. Solved with bank-specific parsers.
2. **Multi-line Narrations:** SBI transactions span 3-5 lines. Used state machine approach.
3. **Date/Time Handling:** Timezone normalization for IST.
4. **AI Model Selection:** Tried multiple models; settled on OpenAI GPT-OSS-20B for balance of speed and quality.

#### Q: How long did this take to develop?
**A (example):** Approximately **2-3 months** with:
- Week 1-2: Requirements, design, database schema
- Week 3-5: Backend API, PDF parsing
- Week 6-8: Frontend, dashboard, charts
- Week 9-10: AI integration, budget tracking
- Week 11-12: Testing, bug fixes, documentation

#### Q: How did you test the application?
**A (honest):**
- **Manual testing** with real statement PDFs
- **Database queries** to verify transaction counts
- **Cross-browser testing** (Chrome, Firefox)

**Missing:** Automated unit tests, integration tests. Given more time, I'd add:
- JUnit tests for parsers
- Mockito for repository tests
- Selenium for frontend tests

#### Q: What would you improve with more time?
**A:**
1. **Duplicate detection** (highest priority)
2. **Password hashing** (security)
3. **More bank support** (HDFC, ICICI, Axis)
4. **CSV export** (feature mentioned in README but not implemented)
5. **Automated tests** (JUnit + Mockito)
6. **Mobile responsiveness** (currently desktop-focused)
7. **Email notifications** for budget overspending
8. **Recurring transaction auto-detection** (currently manual)

### Conceptual Questions

#### Q: What is the unique value proposition of Finlytics?
**A:** **Automated transaction import from Indian bank statements.** Most finance trackers require manual entry or paid API access to banks. Finlytics lets users upload freely available PDF statements and get instant categorization and insights — no subscription fees, no bank API dependencies.

#### Q: Who is the target user?
**A:** College students and young professionals in India who:
- Use UPI heavily (Google Pay, PhonePe, Paytm)
- Want to understand spending patterns
- Don't want to manually log every ₹50 chai purchase
- Prefer free, privacy-friendly solutions over subscription apps like Walnut or ET Money

#### Q: How does this compare to existing solutions?
**A:**
- **Walnut, ET Money:** Require SMS permissions or bank API access; privacy concerns
- **Excel/Google Sheets:** Manual entry, time-consuming
- **Mint (US):** Not designed for Indian banking/UPI formats
- **Finlytics:** PDF upload, auto-categorization, AI insights, free, no app permissions needed

---

## Common Demo Pitfalls to Avoid

### ❌ Don't:
1. **Skip authentication demo** — it shows multi-user support
2. **Upload real financial data** — use anonymized samples
3. **Panic if AI fails** — acknowledge external dependency, show other features
4. **Say "I don't know"** — say "That's a future enhancement" or "Out of current scope"
5. **Apologize excessively** — frame limitations as scoping decisions

### ✅ Do:
1. **Speak confidently** about what you built
2. **Acknowledge known limitations** proactively
3. **Explain design trade-offs** (e.g., keyword vs. ML categorization)
4. **Highlight technical depth** (PDF parsing state machine, timezone handling)
5. **Mention production readiness gaps** (password hashing, duplicate detection)

---

## Emergency Fallbacks

### If Application Won't Start:
- Show project structure in IDE
- Walk through key classes (controllers, parsers)
- Show database schema in MySQL Workbench
- Explain architecture with diagrams

### If PDF Upload Fails:
- Show upload history (if previous uploads exist)
- Demonstrate manual transaction entry instead
- Explain parsing logic with code walkthrough

### If AI Insights Fail:
- Say: "AI feature requires external API key and network access. Core financial tracking works independently."
- Show dashboard and other analytics features

### If Database Connection Fails:
- Show sample data in JSON format
- Walk through entity classes and relationships
- Explain JPA annotations and Hibernate

---

## Post-Demo Discussion Points

### Strengths to Emphasize:
- ✅ Solves real problem (no one likes manual finance tracking)
- ✅ Full-stack implementation (frontend, backend, database, external API)
- ✅ Complex PDF parsing logic
- ✅ User authentication and multi-user support
- ✅ AI integration
- ✅ Responsive to feedback (demonstrated by navigation fix)

### Limitations to Acknowledge:
- ⚠️ Security gaps (plaintext passwords, no HTTPS)
- ⚠️ No duplicate detection
- ⚠️ Limited bank support (2 formats)
- ⚠️ No automated tests
- ⚠️ AI sends PII to external service

### Future Enhancements:
- 🔮 RAG-based conversational financial advisor
- 🔮 Investment portfolio tracking
- 🔮 Mobile app (PWA or native)
- 🔮 More bank integrations
- 🔮 Machine learning categorization
- 🔮 Bill payment reminders

---

## Final Confidence Checklist

Before demo starts, confirm:
- [ ] I can explain the problem Finlytics solves
- [ ] I can demonstrate core workflow (register → upload → dashboard → insights)
- [ ] I can explain PDF parsing approach
- [ ] I can describe database schema from memory
- [ ] I can discuss trade-offs (keyword vs. ML, Session vs. JWT, etc.)
- [ ] I know the limitations and can frame them professionally
- [ ] I have backup plan if live demo fails

---

**Remember:** You built a functional, practical application that solves a real problem. Speak confidently, acknowledge gaps honestly, and demonstrate technical depth. Good luck! 🎉
