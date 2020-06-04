package ru.citeck.ecos.model.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class ParentNotFoundException extends RuntimeException {

    public ParentNotFoundException(String extId) {
        super("Parent not found with id: " + extId);
    }
}
