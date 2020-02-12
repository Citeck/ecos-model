package ru.citeck.ecos.model.converter.module;

import ru.citeck.ecos.model.converter.Converter;

public interface ModuleConverter<S, T> extends Converter<S, T> {

    T moduleToDto(S module);
}
