package search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebServerApplication {
    // http://localhost:8080
    // https://localhost:8443
    public static void main(String[] args) {
        SpringApplication.run(WebServerApplication.class, args);
    }
}