package com.banking.account;

import com.banking.account.security.JwtContext;
import com.banking.common.web.CorrelationIdPropagationInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

@SpringBootApplication
@ComponentScan(basePackages = "com.banking")
public class AccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);

        RestTemplate restTemplate = new RestTemplate(requestFactory);

        // Interceptor: attach the JWT from the current thread to every outgoing request
        // so downstream internal endpoints (e.g. notification-service /internal) stay authenticated.
        ClientHttpRequestInterceptor jwtForwardingInterceptor = new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(
                    HttpRequest request,
                    byte[] body,
                    ClientHttpRequestExecution execution) throws IOException {

                String token = JwtContext.getToken();
                if (token != null && !token.isBlank()) {
                    request.getHeaders().set("Authorization", "Bearer " + token);
                }
                return execution.execute(request, body);
            }
        };

        restTemplate.setInterceptors(List.of(
                jwtForwardingInterceptor,
                new CorrelationIdPropagationInterceptor()));
        return restTemplate;
    }
}
