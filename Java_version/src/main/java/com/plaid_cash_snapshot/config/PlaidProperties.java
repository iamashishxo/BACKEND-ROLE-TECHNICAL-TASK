package com.plaid_cash_snapshot.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "plaid")
public class PlaidProperties {
    @NotBlank
    private String clientId;
    @NotBlank
    private String secret;
    private String env = "sandbox";
    private String version = "2020-09-14";
    private List<String> countryCodes = List.of("US");
    private String webhook;
    private String redirectUri;
    private int connectTimeoutMillis = 5000;
    private int responseTimeoutMillis = 15000;
}

