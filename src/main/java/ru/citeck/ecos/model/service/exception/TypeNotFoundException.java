package ru.citeck.ecos.model.service.exception;

public class TypeNotFoundException extends RuntimeException {

    public TypeNotFoundException(String extId) {
        super("Type not found with external ID: " + extId);
    }
}
