package com.spring.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Resource Not Found Exception
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource) {
        super(resource + " not found", "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
    
    public ResourceNotFoundException(String resource, Object id) {
        super(String.format("%s with id %s not found", resource, id), "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}