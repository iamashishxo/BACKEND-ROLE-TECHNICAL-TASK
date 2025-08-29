package com.plaid_cash_snapshot.config;

import java.time.Duration;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(PlaidProperties.class)
public class PlaidWebClientConfig {

    @Bean
    @Qualifier("plaidWebClient")
    public WebClient plaidWebClient(PlaidProperties props) {
        String baseUrl = switch (props.getEnv().toLowerCase()) {
            case "production" -> "https://production.plaid.com";
            case "development" -> "https://development.plaid.com";
            default -> "https://sandbox.plaid.com";
        };

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMillis())
                .responseTimeout(Duration.ofMillis(props.getResponseTimeoutMillis()));

        // If you need a corporate proxy later, uncomment:
        // .proxy(type -> type.type(ProxyProvider.Proxy.HTTP).host("proxy.host").port(8080));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16 MB
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader("PLAID-CLIENT-ID", props.getClientId())
                .defaultHeader("PLAID-SECRET", props.getSecret())
                .defaultHeader("Plaid-Version", props.getVersion())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

