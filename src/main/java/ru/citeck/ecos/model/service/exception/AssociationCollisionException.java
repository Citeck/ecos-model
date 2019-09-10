package ru.citeck.ecos.model.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Association name inappropriate")
public class AssociationCollisionException extends RuntimeException {
    public AssociationCollisionException(String checkedName) {
        super(checkedName);
    }
}
