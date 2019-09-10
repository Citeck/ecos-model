package ru.citeck.ecos.model.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Type Not Found")
public class TypeNotFoundException extends RuntimeException {

    public TypeNotFoundException(String extId) {
        super("Type not found with external ID: " + extId);
    }
}
