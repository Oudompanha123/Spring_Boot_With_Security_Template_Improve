package com.spring.app.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "file")
public class FileInfoProperties {

    private String serverPath;

    private String clientPath;

    private String baseUrl;

}
