<p align="center">

# LensBridge Backend 

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Maven](https://img.shields.io/badge/Maven-3.3.2-red)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)

</p>

> A backend service for LensBridge, a platform for managing crowdsourced boardEvent photos and videos.

LensBridge is the _ultimate_ platform for collecting and sharing photos from events. Whether you're organizing a conference, a concert, or a community gathering, LensBridge makes it easy to gather and showcase photos from attendees. This backend service provides the core functionality for user management, event management, and upload handling, all while ensuring security and scalability. 

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.3.2 or higher (Or use the wrapper)
- Some form of SQL distribution (PostgreSQL, MySQL, etc.) - I use PostgreSQL in prod and SQLite for local development.
- Cloudflare R2 (Or any S3-compatible storage service) for file storage
- (Optional) SMTP server for email notifications

### Setup - Local Development

1. Clone the repository:

    ```bash
    git clone https://github.com/LensBridge/LensBridgeBackend.git
    ```

2. Create the `application.properties` file:

    ```bash
    cp src/main/resources/application.properties.example src/main/resources/application.properties
    ```

    Then edit `application.properties` to configure your database connection, R2 credentials, and other settings.

    > I suggest using SQlite for local development to avoid the overhead of setting up a full database. Just change the datasource URL to `jdbc:sqlite:lensbridge.db` and remove the username/password. The dependency is already included in the project.

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

## Deployment

This project utilizes Docker for containerization, making it easy to deploy on any platform that supports Docker (AWS, Heroku, DigitalOcean, etc.). A `Dockerfile` is included in the repository. To build and run the Docker container, use the following commands:

```bash
docker compose up --build
```

...aaaand that's it. Pretty anti-climactic. Make sure to include your SSL certs in the `docker/nginx/certs` directory and configure the environment variables accordingly. Once the container is running, the API will be accessible at `https://localhost/api/`.

## API Documentation

This project uses SpringDoc OpenAPI to generate API documentation. Once the application is running, you can access the API docs at:

```
http://localhost:8080/swagger-ui.html
```

Consider disabling this in production. This _is_ an open source project, though; Not sure how effective that would be.

A local copy of the API documentation will be added sometime in the distant future