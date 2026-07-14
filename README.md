# 🚀 Spring Boot Application

A modern, secure Spring Boot application built with best practices for authentication, user management, and content sharing.

## ✨ What This Application Does

This is a **social media-like platform** that allows users to:
- 🔐 Sign up and log in securely with JWT tokens
- 👥 Manage user profiles and roles
- 📝 Create and share posts
- 📱 View personalized feeds
- 🛡️ Control access based on user permissions
- 📊 Handle file uploads and images

## 🏗️ Project Structure - Made Simple

Think of this project like a well-organized house with different rooms for different purposes:

```
📁 src/main/java/com/spring/app/
├── 🚪 SpringAppApplication.java          # Main entrance (starts the app)
│
├── 🛠️ common/                           # Tools everyone can use
│   ├── AbstractRestController.java      # Base template for controllers
│   ├── ApiResponse.java                 # Standard way to send responses
│   ├── PaginatedResponse.java          # Handle large lists of data
│   └── BaseEntity.java                  # Common fields for all entities
│
├── ⚙️ config/                           # App settings and configuration
│   ├── SecurityConfig.java             # Security rules and permissions
│   ├── JpaAuditingConfig.java          # Track when data is created/updated
│   ├── MvcConfig.java                  # Web configuration
│   └── OpenApiConfig.java              # API documentation
│
├── 🎮 controller/                       # Handles incoming web requests
│   ├── auth/                           # Login, logout, registration
│   ├── user/                           # User management
│   ├── admin/                          # Admin-only functions
│   ├── post/                           # Create, read, update posts
│   ├── feed/                           # Show posts to users
│   └── profile/                        # User profile management
│
├── 💼 service/                         # Business logic (the "brain")
│   ├── auth/                           # Authentication logic
│   ├── user/                           # User operations
│   ├── admin/                          # Admin operations
│   ├── post/                           # Post operations
│   ├── feed/                           # Feed generation
│   ├── profile/                        # Profile operations
│   ├── setting/                        # App settings
│   └── file/                           # File handling
│
├── 🗄️ domain/                          # Data models (like database tables)
│   ├── user/                           # User information
│   ├── role/                           # User roles (admin, user, etc.)
│   ├── permission/                     # What users can do
│   ├── post/                           # Post content
│   └── setting/                        # App configuration
│
├── 📦 payload/                         # Data transfer objects (DTOs)
│   ├── auth/                           # Login/register data
│   ├── user/                           # User data
│   ├── feed/                           # Feed data
│   ├── post/                           # Post data
│   └── profile/                        # Profile data
│
├── 🔄 mapper/                          # Convert between different data types
│   └── UserMapper.java                 # Convert user data
│
├── 🔒 security/                        # Security and authentication
│   ├── JwtUtil.java                    # JWT token handling
│   ├── SecurityUser.java               # User security details
│   └── JwtAuthenticationConverter.java # Convert JWT to user info
│
├── ⚠️ exception/                       # Error handling
│   ├── GlobalExceptionHandler.java     # Catch and handle all errors
│   ├── BusinessException.java          # Business rule violations
│   └── ResourceNotFoundException.java  # When data isn't found
│
├── 🆘 helper/                          # Helper utilities
│   └── AuthHelper.java                 # Authentication helpers
│
├── 🔄 converter/                       # Data conversion utilities
│   └── JwtAuthenticationConverter.java # JWT conversion
│
├── 📋 enums/                           # Predefined lists of values
│   ├── ClientType.java                 # Types of clients (web, mobile)
│   ├── PostStatus.java                 # Post states (draft, published)
│   ├── PostType.java                   # Types of posts (text, image)
│   ├── Status.java                     # General status values
│   └── Visibility.java                 # Who can see content
│
└── 🛠️ util/                            # General utility functions
    ├── ImageUtil.java                  # Image processing
    ├── JwtUtil.java                    # JWT operations
    └── ObjectUtils.java                # Object utilities
```

## 📝 Naming Rules - Keep It Simple!

### 🎯 Basic Java Naming

| What | How to Name | Example |
|------|-------------|---------|
| **Classes** | Start with capital letter | `UserController`, `AuthService` |
| **Methods** | Start with small letter | `getUserById()`, `createUser()` |
| **Variables** | Start with small letter | `userRepository`, `authToken` |
| **Constants** | ALL CAPS with underscores | `MAX_RETRY_ATTEMPTS` |
| **Packages** | All lowercase | `com.spring.app.controller` |

### 🗄️ Database & Entity Naming

#### 📊 Entity Classes (Database Tables)
- **Entity Name**: `User` → database table `users`
- **Entity Name**: `UserProfile` → database table `user_profiles`

#### 📋 Entity Fields (Database Columns)
- **Field Name**: `firstName` → database column `first_name`
- **Field Name**: `emailAddress` → database column `email_address`

#### 🔗 Relationships
- **One User, One Profile**: `userProfile` (one-to-one)
- **One User, Many Posts**: `posts` (one-to-many)
- **Many Users, Many Roles**: `roles` (many-to-many)

#### 💡 Example Entity
```java
@Entity
@Table(name = "users")                    // Table name in database
public class User extends BaseEntity {

    @Column(name = "first_name")          // Column name in database
    private String firstName;

    @Column(name = "last_name")           // Column name in database
    private String lastName;

    @OneToOne(mappedBy = "user")         // One user has one profile
    private UserProfile userProfile;

    @OneToMany(mappedBy = "user")        // One user has many posts
    private List<Post> posts;

    @ManyToMany                           // Many users can have many roles
    @JoinTable(name = "user_roles")      // Join table name
    private Set<Role> roles;
}
```

### 📤 Request & Response Naming (DTOs)

#### 📥 Request Objects (Data Coming In)
- **Create**: `CreateUserRequest` - for new users
- **Update**: `UpdateUserRequest` - for changing existing users
- **Search**: `UserSearchRequest` - for finding users

#### 📤 Response Objects (Data Going Out)
- **Single**: `UserResponse` - one user's data
- **List**: `UserListResponse` - many users' data
- **Summary**: `UserSummaryResponse` - brief user info

#### 💡 Example Request/Response
```java
// 📥 Request - Data coming from user
public class CreateUserRequest {
    private String firstName;        // User's first name
    private String lastName;         // User's last name
    private String email;            // User's email
    private String password;         // User's password
    private List<Long> roleIds;     // Which roles to assign
}

// 📤 Response - Data going to user
public class UserResponse {
    private Long id;                 // User's ID
    private String firstName;        // User's first name
    private String lastName;         // User's last name
    private String email;            // User's email
    private LocalDateTime createdAt; // When user was created
    private List<RoleResponse> roles; // User's roles
}

// 📊 List Response - Multiple users
public class UserListResponse {
    private List<UserSummaryResponse> users;  // List of users
    private Pagination pagination;            // Page info
    private long totalCount;                  // Total number of users
}
```

### 🔧 JSON Naming Best Practice

**Use this pattern to keep Java names clean and JSON names consistent:**

```java
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class UserResponse {
    private String firstName;        // Java: firstName
    private String lastName;         // Java: lastName
    private String emailAddress;     // Java: emailAddress
    private LocalDateTime createdAt; // Java: createdAt
    private boolean isActive;        // Java: isActive
}
```

**This automatically converts to JSON:**
```json
{
  "first_name": "John",
  "last_name": "Doe", 
  "email_address": "john@example.com",
  "created_at": "2024-01-01T10:00:00",
  "is_active": true
}
```

### ✅ Validation Rules

```java
public class CreateUserRequest {
    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be 2-50 characters")
    private String firstName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{8,}$",
             message = "Password must be at least 8 characters with letters and numbers")
    private String password;
}
```

## 🚀 Getting Started - Quick & Easy!

### 🐳 Prerequisites
- **Java 17** or higher
- **Gradle** (included in the project)

### ⚡ Quick Commands

```bash
# 🏃‍♂️ Run the application
./gradlew bootRun

# 🏗️ Build the application
./gradlew build

# 🧪 Run tests
./gradlew test

# 📦 Create JAR file
./gradlew bootJar
```

### 🌐 Access Your Application
After running `./gradlew bootRun`:
- **Main App**: http://localhost:8088
- **API Docs**: http://localhost:8088/swagger-ui/index.html

## 🔒 Security Features

- 🔐 **JWT Authentication** - Secure login without storing passwords
- 👥 **Role-Based Access** - Different permissions for different users
- 🛡️ **Input Validation** - All data is checked before processing
- 🚫 **Rate Limiting** - Prevent abuse of your API
- 🔒 **HTTPS Ready** - Secure connections in production

## 🧪 Testing Strategy

- **Unit Tests** ✅ - Test individual pieces
- **Integration Tests** ✅ - Test how pieces work together
- **End-to-End Tests** ✅ - Test complete user journeys
- **Goal**: 80%+ code coverage

## 📊 Monitoring & Health

- 📈 **Health Checks** - Know when your app is sick
- 📝 **Structured Logging** - Easy to read logs
- 📊 **Metrics** - Track app performance
- 🔍 **API Documentation** - Auto-generated with Swagger

## 🚀 Deployment Tips

- 🐳 **Docker Ready** - Easy to containerize
- 🌍 **Environment Configs** - Different settings for dev/prod
- 📊 **Health Endpoints** - Monitor app status
- 🔄 **Graceful Shutdown** - Handle server restarts properly

## 🤝 Contributing

1. **Follow the naming conventions** above
2. **Write tests** for new features
3. **Update documentation** when changing APIs
4. **Use meaningful commit messages**

## 📚 Need Help?

- 📖 **Spring Boot Docs**: https://spring.io/projects/spring-boot
- 🔒 **Security Docs**: https://spring.io/projects/spring-security
- 📊 **JPA Docs**: https://spring.io/projects/spring-data-jpa

---

**🎯 Project Goal**: Create a robust, scalable social platform  
**🔄 Version**: 1.0  
**📅 Last Updated**: 2024  
**☕ Java Version**: 17+  
**🌱 Spring Boot**: 3.2.0+
