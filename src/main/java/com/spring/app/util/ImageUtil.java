package com.spring.app.util;

import com.spring.app.common.FileInfoProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@AllArgsConstructor
@Component
public class ImageUtil {

    private final FileInfoProperties fileInfoProperties;

    public String getImage(String imageName) {
        if (ObjectUtils.isEmpty(imageName)) {
            return "";
        }
        return fileInfoProperties.getBaseUrl() + "/" + imageName;
    }
}
