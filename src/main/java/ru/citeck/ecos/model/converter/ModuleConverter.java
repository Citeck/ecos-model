package ru.citeck.ecos.model.converter;

public interface ModuleConverter<S, T> extends Converter<S, T> {

    T moduleToDto(S module);
}
