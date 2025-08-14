<p align="center">

# LensBridge Backend 

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.2-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Maven](https://img.shields.io/badge/Maven-3.3.2-red)
![MongoDB](https://img.shields.io/badge/MongoDB-4.4-green)

</p>

> A backend service for LensBridge, a platform for managing crowdsourced event photos and videos.

LensBridge is the all-in-one solution for universities to manage and showcase event photos and videos. This backend service provides APIs for user management, event management, and upload management.

## Features

- **User Management**: Register, login, and manage user profiles. All authentication is handled via JWT.
- **Event Management**: Create, update, and delete events. Uploads are associated with events for ease of organization.
- **Upload Management**: Upload photos and videos, with options for approval, featuring, and anonymization.
- **Admin Operations**: Admins can manage uploads, events, and user roles with enhanced endpoints for better user information.

## General Information

This project is built using Java (duh), Spring Boot, and MongoDB. It follows the MVC architecture and uses JWT for secure authentication. The API is designed to be RESTful, providing a clean and intuitive interface for frontend applications.

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.3.2 or higher (Or use the wrapper)
- Some form of MongoDB and Cloudflare R2 access

### Setup

1. Clone the repository:

    ```bash
    git clone https://github.com/LensBridge/LensBridgeBackend.git
    ```

2. Create the `application.properties` file in `src/main/resources/` with the following content:

```
# Server configuration
server.port=8080
# Note: Uncomment the following lines to enable SSL (for production use)
#server.ssl.enabled=
#server.ssl.key-store=
#server.ssl.key-store-type=
#server.ssl.key-store-password=
#server.ssl.key-alias=
#server.address=0.0.0.0

# Database configuration - MongoDB
spring.data.mongodb.uri=
spring.data.mongodb.database=

# For CORS - Fill in with the frontend URL. 
frontend.baseurl=

# Cloudflare R2 configuration
cloudflare.r2.access-key-id=your-access-key-id
cloudflare.r2.secret-access-key=your-secret-access-key
cloudflare.r2.endpoint=https://your-account-id.r2.cloudflarestorage.com
cloudflare.r2.bucket-name=your-bucket-name
cloudflare.r2.public-url=https://your-account-id.r2.cloudflarestorage.com/your-bucket-name

# Uploads Config
uploads.video.maxduration=240
uploads.max-size=1000000000
uploads.allowed-file-types=video/mp4,video/quicktime,video/x-matroska,video/x-msvideo,image/jpeg,image/png,image/heic
# Rate limiting configuration
ratelimit.requests=100
ratelimit.duration.minutes=1

# File upload configuration
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=150MB

# Logging configuration
logging.level.com.ibrasoft.lensbridge=INFO
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.mail=DEBUG
logging.level.org.springframework.mail.javamail=DEBUG

# SMTP Configuration for email notifications
spring.mail.host=
spring.mail.port=
spring.mail.username=
spring.mail.password=
spring.mail.properties.mail.smtp.auth=
spring.mail.properties.mail.smtp.ssl.enable=
spring.mail.properties.mail.smtp.ssl.trust=
spring.mail.properties.mail.smtp.connectiontimeout=
spring.mail.properties.mail.smtp.timeout=10000
spring.mail.properties.mail.smtp.writetimeout
spring.mail.properties.mail.debug=
spring.mail.from=
spring.mail.from.name=

lensbridge.app.jwtSecret= 
lensbridge.app.jwtExpirationMs=
```

3. Build the project using Maven:

    ```bash
    mvn clean install
    ```

4. Run the application:

    ```bash
    mvn spring-boot:run
    ```

5. Access the API at `http://localhost:8080/api/`.

And that's it! You now have a running instance of the LensBridge backend. Pair this with the frontend application to create a complete LensBridge experience.

## API Documentation

*Coming soon*