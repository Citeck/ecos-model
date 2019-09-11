package ru.citeck.ecos.model.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Children types could be forgotten")
public class ForgottenChildsException extends RuntimeException {

    public ForgottenChildsException() {
        super("Children types could be forgotten");
    }
}
