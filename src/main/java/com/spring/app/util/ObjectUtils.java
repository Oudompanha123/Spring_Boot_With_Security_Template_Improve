package com.spring.app.util;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.app.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ObjectUtils {
    private final ObjectMapper objectMapper;

    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error(String.valueOf(e));
            throw new BusinessException("Error parsing JSON", e);
        }
    }

    public <T> String writeValueAsString(T object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error(String.valueOf(e));
            throw new BusinessException("Error converting to JSON", e);
        }
    }
}
