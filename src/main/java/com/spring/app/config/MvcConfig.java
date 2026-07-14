package com.spring.app.config;

import com.spring.app.common.FileInfoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
class MvcConfig implements WebMvcConfigurer {
    private final FileInfoProperties fileInfoProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/v1/image/**")
                .addResourceLocations("file:" + fileInfoProperties.getServerPath() + "/");
    }
}