package com.agrilink.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Development fallbacks for the replaceable delivery gateways, plus the localised message templates. */
@Configuration
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    @ConditionalOnMissingBean(SmsGateway.class)
    public SmsGateway loggingSmsGateway() {
        return (to, message) -> log.info("[SMS -> {}] {}", to, message);
    }

    @Bean
    @ConditionalOnMissingBean(PushGateway.class)
    public PushGateway loggingPushGateway() {
        return (token, platform, title, body, data) ->
                log.info("[PUSH {} -> {}...] {} | {} {}", platform,
                        token.substring(0, Math.min(8, token.length())), title, body, Map.copyOf(data));
    }

    @Bean(name = "notificationMessageSource")
    public MessageSource notificationMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("notifications");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }
}
