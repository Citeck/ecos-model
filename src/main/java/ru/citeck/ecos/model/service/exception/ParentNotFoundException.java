package ru.citeck.ecos.model.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Parent Not Found")
public class ParentNotFoundException extends RuntimeException {

    public ParentNotFoundException(String uuid) {
        super("Parent not found with UUID: " + uuid);
    }
}
