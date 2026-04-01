# Finlytics – Personal Finance Tracker

Finlytics is a full-stack personal finance tracking web application built with Spring Boot and vanilla JavaScript.
It enables users to upload real Indian bank statements, automatically parse and categorize transactions, track budgets, and receive AI-powered financial insights.

---

## Features

### Transaction Management
- Upload SBI YONO and Google Pay UPI PDF statements (including password-protected PDFs)
- Automatic transaction parsing with multi-line UPI support
- Auto-categorization using keyword rules and a persistent merchant rule engine
- Manual transaction entry, editing, and deletion
- Date range filtering
- CSV export (full or filtered)
- Mark transactions as recurring with monthly suggestions

### Budget & Analytics
- Monthly budget limits per category with progress bars
- Overspending warnings
- Dashboard with income / expense / savings summary cards
- Charts: category breakdown, income vs expense, monthly trend, top merchants

### AI Insights
- Financial insights powered by Groq API (LLaMA 3.1)
- Cached per session, rendered with markdown formatting

### Auth
- Session-based authentication (register / login / logout)
- All pages protected behind login

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot, Spring Data JPA, Hibernate |
| Frontend | HTML, CSS, JavaScript, Chart.js |
| Database | MySQL |
| PDF Parsing | Apache PDFBox, tabula-java |
| AI | Groq API (llama-3.1-8b-instant) |
| Tools | IntelliJ IDEA, Git, GitHub |

---

## Project Structure

```
src/main/java
  controller/   — Auth, Transactions, Upload, Budget, Summary, Insights, Dashboard
  entity/       — User, Transaction, Category, Budget, MerchantRule, Upload
  repository/   — JPA repositories
  service/      — SBI statement parser, Recurring detection

src/main/resources
  templates/    — HTML pages
  static/       — CSS, JS assets
```

---

## How to Run

### Prerequisites
- Java 17+
- MySQL 8+
- IntelliJ IDEA (recommended)

### Setup

1. **Clone the repository**

```
git clone https://github.com/mithranlabs/fintracker.git
```

2. **Create the database**

Open MySQL and run:

```sql
CREATE DATABASE finlytics;
```

3. **Configure application.properties**

Create `src/main/resources/application.properties` (not committed — contains secrets):

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/finlytics
spring.datasource.username=your_mysql_username
spring.datasource.password=your_mysql_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
groq.api.key=your_groq_api_key
```

4. **Run the application**

Run `FinBackendApplication.java` from IntelliJ, or:

```bash
./mvnw spring-boot:run
```

5. **Open in browser**

```
http://localhost:8080
```

Hibernate will auto-create all tables on first run.

---

## Supported Bank Statements

| Bank | Format | Notes |
|---|---|---|
| SBI YONO | PDF (password-protected) | Password: first 5 letters of name + DOB (DDMMYYYY) |
| Google Pay | UPI PDF | Multi-line transaction support |

---

## Planned Improvements

- RAG-based conversational AI financial advisor
- Auto-detection of recurring transactions (ML-based)
- Additional bank support (HDFC, ICICI, Axis)
- Progressive Web App (PWA) with offline support
- CSV/UPI SMS import

---

## Author

**Mithran M**
https://github.com/mithranlabs
