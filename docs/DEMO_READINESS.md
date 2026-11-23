# Finlytics Demo Readiness Checklist

**Demo Date:** [Tomorrow]  
**Time Required for Preparation:** 60-90 minutes  
**Last Updated:** October 11, 2026

---

## 🚨 Critical Tasks (Must Complete Before Demo)

### 1. Environment Setup
- [ ] **MySQL is running**
  - Windows: Start MySQL service from Services
  - Mac: `brew services start mysql` or MAMP
  - Linux: `sudo systemctl start mysql`
  - Test: `mysql -u root -p` (should connect)

- [ ] **Database exists and is accessible**
  ```sql
  SHOW DATABASES;  -- Should show 'finlytics'
  USE finlytics;
  SHOW TABLES;  -- Should show: users, transactions, categories, budgets, merchant_rules, uploads
  ```

- [ ] **Application.properties file exists with correct credentials**
  - File: `src/main/resources/application.properties`
  - Check: `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
  - **DO NOT commit this file!**

- [ ] **Groq API key is configured** (for AI insights)
  - Property: `gemini.api.key=your_groq_api_key`
  - Optional but recommended for full demo
  - Get from: https://console.groq.com/keys

- [ ] **Application builds successfully**
  ```bash
  mvn clean install
  ```
  - Should complete without errors
  - Creates `target/` directory with compiled classes

- [ ] **Application starts without errors**
  ```bash
  mvn spring-boot:run
  ```
  - Console should show: `Started FinBackendApplication in X.XXX seconds`
  - No red ERROR lines in console

- [ ] **Landing page loads**
  - Open: http://localhost:8080
  - Should show Finlytics landing page with "Where Wealth, Meets Intelligence"

---

## 📂 Sample Data Preparation

### 2. PDF Statements Ready
- [ ] **Google Pay UPI statement** (anonymized, at least 10 transactions)
  - Format: Must have "Paid to" / "Received from" lines
  - File size: < 10MB
  - Location: Desktop or easy-to-find folder

- [ ] **SBI YONO statement** (anonymized, password-protected)
  - Format: Must be SBI account statement (not credit card)
  - Password format known: `NNNNNDDMMYYYY` (e.g., `MITHR31122004`)
  - File size: < 10MB
  - Location: Desktop or easy-to-find folder

- [ ] **Manual transaction test data** ready
  - Example: ₹500, Food, "Swiggy dinner", expense, today's date

### 3. Demo User Account
- [ ] **Option A: Pre-create demo account** (recommended)
  - Register before demo: username `demo`, password `demo123`
  - Upload 1-2 statements to have existing data
  - Test dashboard shows data correctly

- [ ] **Option B: Register live during demo**
  - Practice registration flow beforehand
  - Have username/email/password written down

---

## 🧪 Functional Verification

### 4. Core Workflows Tested
- [ ] **User Registration**
  - Navigate to `/register-page`
  - Register new user → Should show "Registered"
  - Verify user appears in database: `SELECT * FROM users;`

- [ ] **User Login**
  - Navigate to `/login-page`
  - Login with registered user → Should redirect to `/dashboard-page`
  - Dashboard loads with welcome message

- [ ] **Google Pay Upload**
  - Upload Google Pay PDF → Should show "Parsed and saved"
  - Navigate to Dashboard → Income/Expense cards update
  - Navigate to Transactions → Transactions appear in table
  - Check database: `SELECT COUNT(*) FROM transactions;`

- [ ] **SBI YONO Upload**
  - Upload SBI PDF with correct password → Success message with count
  - Dashboard updates with new transactions
  - Verify transactions have correct dates, amounts, categories

- [ ] **Dashboard Displays Correctly**
  - Summary cards show correct numbers (income, expense, savings, count)
  - Recent transactions table populates
  - Budget health section displays (if budgets exist)

- [ ] **AI Insights Work**
  - Click "Get Insights" button
  - Wait 3-5 seconds
  - Insight text appears with spending analysis
  - If fails: Note error message, have fallback explanation ready

- [ ] **Navigation Works**
  - Click "Dashboard" from other pages → Returns to dashboard (NOT landing page)
  - Click "Logout" → Redirects to login page
  - Try accessing `/dashboard-page` without login → Redirects to login

- [ ] **Transaction List Loads**
  - Navigate to "Transactions" page
  - All uploaded transactions display
  - Edit and Delete buttons visible

### 5. Error Scenarios Tested
- [ ] **Wrong login credentials** → Shows "Wrong password" or "No user"
- [ ] **Invalid PDF upload** → Shows error message (not crash)
- [ ] **Wrong SBI password** → Shows "Wrong password. Format: NNNNNDDMMYYYY"
- [ ] **AI insights without API key** → Graceful failure message

---

## 💻 Technical Preparation

### 6. IDE and Tools Ready
- [ ] **IntelliJ IDEA (or preferred IDE) open**
  - Project loaded and indexed
  - No compilation errors
  - Ready to show code if asked

- [ ] **MySQL Workbench open** (optional but helpful)
  - Connected to `finlytics` database
  - Ready to run queries if asked

- [ ] **Browser configured**
  - Chrome or Firefox (full-screen mode ready)
  - Zoom at 100% (Ctrl+0)
  - No unnecessary tabs open
  - Incognito/private mode recommended (clean session)

- [ ] **Console visible**
  - Terminal/console with application logs visible
  - Font size readable for audience
  - Ready to show SQL queries or debug logs if asked

### 7. Backup Plans Ready
- [ ] **Postman or curl ready** (if live upload fails)
  - Can demonstrate API endpoints directly

- [ ] **Database queries prepared** (if UI fails)
  ```sql
  -- Show all transactions
  SELECT t.amount, t.type, t.note, c.name as category, t.date
  FROM transactions t
  LEFT JOIN categories c ON t.category_id = c.id
  ORDER BY t.date DESC
  LIMIT 10;

  -- Show user summary
  SELECT 
    SUM(CASE WHEN type='income' THEN amount ELSE 0 END) as income,
    SUM(CASE WHEN type='expense' THEN amount ELSE 0 END) as expense
  FROM transactions;
  ```

- [ ] **Code walkthrough prepared** (if application won't start)
  - Key classes identified: `UploadController`, `SbiStatementParser`, `DashboardController`
  - Entity relationships memorized
  - Architecture diagram (if drawn)

---

## 📖 Knowledge Verification

### 8. Can Explain These Concepts
- [ ] **What problem does Finlytics solve?**
  - Answer: Automates transaction import from PDF statements, eliminating manual entry

- [ ] **How does PDF parsing work?**
  - Answer: PDFBox extracts text, pattern matching identifies transactions, state machine handles multi-line narrations

- [ ] **How does categorization work?**
  - Answer: Merchant rules (database) → Keyword matching (in-memory) → Fallback (Salary/Personal)

- [ ] **What is the database schema?**
  - Answer: 6 tables — users, transactions, categories, budgets, merchant_rules, uploads

- [ ] **How does AI integration work?**
  - Answer: Groq API with openai/gpt-oss-20b model, sends 30-day summary, receives personalized insights

- [ ] **What are the known limitations?**
  - Answer: No duplicate detection, plaintext passwords, limited bank support, global AI cache

- [ ] **Technology stack?**
  - Answer: Spring Boot (Java 17), MySQL, Thymeleaf, Vanilla JS, Apache PDFBox, Groq API

- [ ] **Future improvements?**
  - Answer: Duplicate detection, password hashing, more banks, ML categorization, mobile app

### 9. Viva Preparation
- [ ] **Read `docs/DEMO_GUIDE.md`** — Complete demo script and Q&A
- [ ] **Review `docs/AUDIT_FINDINGS.md`** — Known issues and gaps
- [ ] **Read `docs/PROJECT_GUIDE.md`** — Features and architecture overview
- [ ] **Understand `docs/NAVIGATION_FIX.md`** — Recent bug fix explanation

---

## 🎯 Demo Flow Memorized

### 10. Rehearsal Completed
- [ ] **Practiced full demo flow** (8 minutes)
  1. Landing page overview (1 min)
  2. Registration + Login (1 min)
  3. PDF uploads (3 min)
  4. Dashboard analytics (1.5 min)
  5. AI insights (1 min)
  6. Transactions + Budgets (1 min)
  7. Wrap-up (30 sec)

- [ ] **Practiced common questions**
  - Why Spring Boot?
  - How does PDF parsing work?
  - Why not use ML for categorization?
  - What are the security gaps?
  - How long did this take?
  - What would you improve?

- [ ] **Prepared fallback explanations**
  - If AI fails: "External API dependency, core features work independently"
  - If upload fails: "Can show parsing logic in code instead"
  - If database fails: "Can demonstrate with sample JSON data"

---

## 🚀 Final Pre-Demo Steps (30 Minutes Before)

### 11. Last-Minute Checks
- [ ] **Restart MySQL** (ensure clean state)
- [ ] **Clear application database** (optional, for clean demo)
  ```sql
  -- Only if you want fresh start
  DELETE FROM transactions;
  DELETE FROM budgets;
  DELETE FROM uploads;
  DELETE FROM users;
  ```

- [ ] **Restart application** (ensure clean session)
  ```bash
  mvn spring-boot:run
  ```

- [ ] **Verify landing page loads** (http://localhost:8080)
- [ ] **Clear browser cookies/cache** (or use incognito)
- [ ] **Close unnecessary applications** (Slack, email, etc.)
- [ ] **Silence phone/notifications**
- [ ] **Have PDF statements open in file explorer**
- [ ] **Have demo script (`docs/DEMO_GUIDE.md`) open in second screen/tablet**

### 12. Confidence Check
- [ ] **Can start application from command line** (`mvn spring-boot:run`)
- [ ] **Can navigate to all pages** (landing, register, login, dashboard, transactions, upload, summary)
- [ ] **Can explain project in 30 seconds** (elevator pitch)
- [ ] **Know where to find code** (controllers, parsers, entities)
- [ ] **Prepared for "what went wrong" question** (acknowledge navigation bug, explain fix)
- [ ] **Prepared for "what's next" question** (duplicate detection, security hardening)

---

## ✅ Go/No-Go Decision

### Ready for Demo If:
✅ Application starts without errors  
✅ Database connects successfully  
✅ Landing page loads  
✅ Can register and login  
✅ At least one PDF upload works  
✅ Dashboard shows data  
✅ Navigation links work correctly  
✅ Comfortable explaining architecture  

### Delay Demo If:
❌ Application won't start (unresolved errors)  
❌ Database connection fails (wrong credentials)  
❌ No PDF statements prepared  
❌ Cannot explain basic architecture  
❌ Critical features completely broken  

---

## 📞 Emergency Contacts (If Applicable)

- **Project Guide/Mentor:** [Contact if needed]
- **Technical Support:** [Database admin, sysadmin if separate]
- **Backup Laptop Available:** [Yes/No]

---

## 🎉 Final Message

**You built a functional, practical application that solves a real problem.**

- ✅ Automated PDF parsing from Indian bank statements
- ✅ Intelligent categorization
- ✅ Real-time analytics
- ✅ AI-powered insights
- ✅ Full-stack implementation

**Known limitations are expected in academic projects.** Acknowledge them professionally and explain your design decisions confidently.

**If something breaks during demo:**
1. Stay calm
2. Acknowledge the issue
3. Explain what *should* happen
4. Show alternative (code, database queries, architecture)
5. Emphasize working features

**Good luck! 🚀**

---

## Post-Demo Tasks

After successful demo:
- [ ] Commit navigation fixes: `git add .` → `git commit -m "fix: navigation regression"` → `git push`
- [ ] Commit uncommitted InsightsController changes
- [ ] Backup database: `mysqldump -u root -p finlytics > finlytics_final.sql`
- [ ] Archive project: `zip -r finlytics_project.zip fin_backend/`
- [ ] Celebrate! 🎉

---

**Checklist Last Reviewed:** [Date/Time]  
**Status:** [Ready / Needs Work / Not Ready]
