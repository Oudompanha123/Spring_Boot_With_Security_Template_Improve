package com.spring.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Resource Already Exists Exception
@ResponseStatus(HttpStatus.CONFLICT)
public class ResourceAlreadyExistsException extends BusinessException {
    public ResourceAlreadyExistsException(String resource) {
        super(resource + " already exists", "RESOURCE_ALREADY_EXISTS", HttpStatus.CONFLICT);
    }
    
    public ResourceAlreadyExistsException(String resource, String field, Object value) {
        super(String.format("%s with %s '%s' already exists", resource, field, value), 
              "RESOURCE_ALREADY_EXISTS", HttpStatus.CONFLICT);
    }
}