package ru.citeck.ecos.model.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class ForgottenChildsException extends RuntimeException {

    public ForgottenChildsException() {
        super("Children types could be forgotten");
    }
}
