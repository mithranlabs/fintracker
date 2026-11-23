# Finlytics Development Guide

## Prerequisites

### Required Software
- **Java Development Kit (JDK) 17 or higher**
  - Download: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/
  - Verify: `java -version` should show 17+

- **MySQL 8.0 or higher**
  - Download: https://dev.mysql.com/downloads/mysql/
  - Alternative: Use XAMPP, MAMP, or Docker

- **Maven 3.6+** (usually bundled with IDEs)
  - Verify: `mvn -version`

### Recommended Software
- **IntelliJ IDEA** (Community or Ultimate)
- **MySQL Workbench** (for database management)
- **Postman** (for API testing)
- **Git** (for version control)

---

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/mithranlabs/fintracker.git
cd fintracker
```

### 2. Create MySQL Database

Open MySQL command line or MySQL Workbench:

```sql
CREATE DATABASE finlytics CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**Optional:** Create a dedicated user:

```sql
CREATE USER 'finlytics_user'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON finlytics.* TO 'finlytics_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Configure Application Properties

Copy the example configuration:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Edit `src/main/resources/application.properties`:

```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/finlytics
spring.datasource.username=root
spring.datasource.password=your_mysql_password

# Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Server Configuration
server.port=8080

# Groq API Key (for AI insights)
gemini.api.key=your_groq_api_key_here
```

**Important:** Never commit `application.properties` with real credentials!

### 4. Get Groq API Key (Optional, for AI Insights)

1. Visit: https://groq.com/
2. Sign up for free account
3. Generate API key from dashboard
4. Add to `application.properties` as `gemini.api.key`

**Note:** Application works without API key; AI insights feature will just fail gracefully.

### 5. Install Dependencies

```bash
mvn clean install
```

This will:
- Download all Maven dependencies
- Compile the application
- Run any tests (if present)
- Create target directory with compiled classes

### 6. Run the Application

#### Option A: Using Maven

```bash
mvn spring-boot:run
```

#### Option B: Using IntelliJ IDEA

1. Open project in IntelliJ
2. Wait for Maven import to complete
3. Navigate to `src/main/java/com/mithran/fintracker/fin_backend/FinBackendApplication.java`
4. Right-click → Run 'FinBackendApplication'

#### Option C: Using JAR File

```bash
mvn clean package
java -jar target/fin_backend-0.0.1-SNAPSHOT.jar
```

### 7. Verify Application Started

Look for console output:

```
Tomcat started on port(s): 8080 (http)
Started FinBackendApplication in X.XXX seconds
```

Open browser: http://localhost:8080

You should see the Finlytics landing page.

---

## Project Structure

```
fin_backend/
├── src/
│   ├── main/
│   │   ├── java/com/mithran/fintracker/fin_backend/
│   │   │   ├── controller/         # REST controllers & page routing
│   │   │   ├── entity/             # JPA entities (database models)
│   │   │   ├── repository/         # Spring Data JPA repositories
│   │   │   ├── service/            # Business logic services
│   │   │   └── FinBackendApplication.java   # Main entry point
│   │   │
│   │   └── resources/
│   │       ├── templates/          # Thymeleaf HTML templates
│   │       ├── static/
│   │       │   ├── css/           # Stylesheets
│   │       │   └── js/            # JavaScript files
│   │       ├── application.properties        # Config (not committed)
│   │       └── application.properties.example
│   │
│   └── test/                       # Test classes (currently empty)
│
├── target/                         # Compiled classes (auto-generated)
├── pom.xml                        # Maven dependencies
├── README.md
└── docs/                          # Project documentation
```

---

## Development Workflow

### 1. Start Development Server

```bash
mvn spring-boot:run
```

**Spring DevTools** is enabled, so:
- Changes to Java files trigger automatic restart
- Changes to templates/static files are hot-reloaded
- No need to manually restart for most changes

### 2. Make Code Changes

#### Adding a New Controller

Create new controller in `src/main/java/com/mithran/fintracker/fin_backend/controller/`:

```java
package com.mithran.fintracker.fin_backend.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/example")
public class ExampleController {

    @GetMapping
    public String getExample() {
        return "Hello from new controller!";
    }
}
```

Save file → Spring DevTools auto-restarts → Test at http://localhost:8080/api/example

#### Adding a New Entity

Create entity in `src/main/java/com/mithran/fintracker/fin_backend/entity/`:

```java
package com.mithran.fintracker.fin_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "examples")
public class Example {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String name;

    // Getters and setters...
}
```

Save file → Hibernate creates `examples` table automatically (thanks to `ddl-auto=update`)

#### Adding a New HTML Page

Create template in `src/main/resources/templates/newpage.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <title>New Page - Finlytics</title>
    <link rel="stylesheet" href="/css/style.css">
</head>
<body>
    <h1>New Page</h1>
</body>
</html>
```

Add route in `PageController.java`:

```java
@GetMapping("/newpage")
public String newPage() {
    return "newpage";
}
```

Access at: http://localhost:8080/newpage

### 3. Check Database Changes

Use MySQL Workbench or command line:

```sql
USE finlytics;
SHOW TABLES;
DESCRIBE transactions;
SELECT * FROM transactions LIMIT 10;
```

### 4. Test API Endpoints

Use Postman or curl:

```bash
# Register a user
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"test123"}'

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test123"}'

# Get transactions (requires session)
curl http://localhost:8080/transactions \
  -H "Cookie: JSESSIONID=your_session_id_here"
```

---

## Common Issues & Solutions

### Issue: Application won't start — "Access denied for user"

**Cause:** Wrong MySQL credentials

**Solution:**
1. Check `application.properties` username/password
2. Verify MySQL is running: `mysql -u root -p`
3. Test connection: `mysql -u your_user -p finlytics`

### Issue: Port 8080 already in use

**Cause:** Another application using port 8080

**Solution A:** Change port in `application.properties`:
```properties
server.port=8081
```

**Solution B:** Kill process using port 8080:
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -ti:8080 | xargs kill -9
```

### Issue: Hibernate creates tables on every startup

**Cause:** `ddl-auto=create` instead of `update`

**Solution:** Ensure `application.properties` has:
```properties
spring.jpa.hibernate.ddl-auto=update
```

Options:
- `update` — Updates schema, keeps data ✅ (recommended for development)
- `create` — Drops and recreates tables ⚠️ (loses data)
- `create-drop` — Drops tables on shutdown ⚠️
- `validate` — Only validates schema, no changes
- `none` — Hibernate doesn't manage schema

### Issue: Changes not reflecting

**Solution:**
1. Check if file saved
2. Wait 2-3 seconds for DevTools restart
3. Hard refresh browser (Ctrl+Shift+R)
4. Clear browser cache
5. Check console for compilation errors

### Issue: PDF upload fails

**Possible Causes:**
1. File size too large (default: 1MB)
2. PDF format not supported
3. Wrong password for SBI statements

**Solution A:** Increase file upload size in `application.properties`:
```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

**Solution B:** Check console logs for detailed error

### Issue: AI insights return "Failed to get insights"

**Possible Causes:**
1. No Groq API key configured
2. Invalid API key
3. Network connectivity issues
4. API rate limit exceeded

**Solution:**
1. Check `application.properties` has valid `gemini.api.key`
2. Verify key works: Test on Groq console
3. Check console logs for API error details

### Issue: Session lost after restart

**Cause:** Sessions stored in memory, cleared on restart

**Solution:** This is normal for development. For production, consider Redis or database-backed sessions.

---

## Environment Variables

For production deployment, use environment variables instead of `application.properties`:

```bash
export DB_URL=jdbc:mysql://localhost:3306/finlytics
export DB_USERNAME=root
export DB_PASSWORD=yourpassword
export GROQ_API_KEY=your_key_here

mvn spring-boot:run
```

Update `application.properties` to use environment variables:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/finlytics}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD}
gemini.api.key=${GROQ_API_KEY}
```

---

## Testing

### Manual Testing

1. Start application
2. Open browser to http://localhost:8080
3. Test user flows:
   - Register → Login → Upload → Dashboard → Insights

### API Testing with Postman

Import collection (if available) or manually test endpoints listed in `docs/API.md`

### Database Testing

```sql
-- Check transaction count
SELECT COUNT(*) FROM transactions;

-- Check user isolation
SELECT u.username, COUNT(t.id) as transaction_count
FROM users u
LEFT JOIN transactions t ON u.id = t.user_id
GROUP BY u.username;

-- Check budget health
SELECT b.month, c.name as category, b.limitAmount, 
       (SELECT SUM(t.amount) 
        FROM transactions t 
        WHERE t.category_id = b.category_id 
          AND DATE_FORMAT(t.date, '%Y-%m') = b.month) as spent
FROM budgets b
JOIN categories c ON b.category_id = c.id;
```

### Automated Testing (Future)

Add JUnit tests in `src/test/java/`:

```java
@SpringBootTest
class TransactionControllerTest {

    @Autowired
    private TransactionRepository repository;

    @Test
    void testCreateTransaction() {
        // Test implementation
    }
}
```

---

## Debugging

### Enable Debug Logging

Add to `application.properties`:

```properties
logging.level.com.mithran.fintracker=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

### IntelliJ Debugger

1. Set breakpoint in code (click left margin)
2. Run in Debug mode (Shift+F9)
3. Use Step Over (F8), Step Into (F7), Resume (F9)

### Check Application Logs

Console output shows:
- SQL queries (if `show-sql=true`)
- HTTP requests
- Exceptions and stack traces
- Custom `System.out.println()` statements

---

## Database Management

### View All Tables

```sql
USE finlytics;
SHOW TABLES;
```

Expected tables:
- users
- transactions
- categories
- budgets
- merchant_rules
- uploads

### Reset Database

**⚠️ Warning:** This deletes all data!

```sql
DROP DATABASE finlytics;
CREATE DATABASE finlytics;
```

Then restart application → Hibernate recreates tables

### Backup Database

```bash
mysqldump -u root -p finlytics > finlytics_backup.sql
```

### Restore Database

```bash
mysql -u root -p finlytics < finlytics_backup.sql
```

---

## Building for Production

### Create Executable JAR

```bash
mvn clean package
```

Output: `target/fin_backend-0.0.1-SNAPSHOT.jar`

### Run Production JAR

```bash
java -jar target/fin_backend-0.0.1-SNAPSHOT.jar
```

### Production Configuration

Create `application-prod.properties`:

```properties
spring.datasource.url=jdbc:mysql://production-host:3306/finlytics
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
server.port=8080
```

Run with production profile:

```bash
java -jar target/fin_backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## Development Best Practices

### 1. Never Commit Sensitive Data
- `application.properties` is .gitignored
- Never hardcode passwords or API keys
- Use environment variables for secrets

### 2. Use Meaningful Commit Messages
```bash
git commit -m "feat: add duplicate transaction detection"
git commit -m "fix: correct SBI date parsing for multi-line narrations"
git commit -m "docs: update API documentation for upload endpoint"
```

### 3. Test Before Committing
- Verify application starts
- Test affected features manually
- Check for console errors

### 4. Keep Dependencies Updated
```bash
mvn versions:display-dependency-updates
```

### 5. Document New Features
- Update README.md
- Add comments for complex logic
- Update API documentation

---

## Useful Commands

```bash
# Clean and rebuild
mvn clean install

# Skip tests (if any exist)
mvn clean install -DskipTests

# Run specific class
mvn spring-boot:run -Dspring-boot.run.arguments=--debug

# Check dependency tree
mvn dependency:tree

# Generate project report
mvn site

# Create executable JAR
mvn package

# Clean target directory
mvn clean
```

---

## Resources

- **Spring Boot Docs:** https://docs.spring.io/spring-boot/docs/current/reference/html/
- **Spring Data JPA:** https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- **Thymeleaf:** https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html
- **Apache PDFBox:** https://pdfbox.apache.org/
- **MySQL Docs:** https://dev.mysql.com/doc/

---

## Next Steps

1. Read `docs/DEMO_GUIDE.md` for demonstration preparation
2. Review `docs/API.md` for endpoint documentation
3. Check `docs/DATABASE.md` for schema details
4. See `docs/AUDIT_FINDINGS.md` for known issues

Happy coding! 🚀
