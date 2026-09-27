package com.flipfinder.config;

import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        // Bind enum query parameters leniently, so sort=estimatedProfit selects ESTIMATED_PROFIT.
        ApplicationConversionService.addApplicationConverters(registry);
    }
}
