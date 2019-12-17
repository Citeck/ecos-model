package ru.citeck.ecos.model.converter.module;

public abstract class AbstractModuleConverter<S, T> implements ModuleConverter<S, T> {

    @Override
    public abstract T moduleToDto(S module);

    @Override
    public T sourceToTarget(S module) {
        return moduleToDto(module);
    }

    @Override
    public S targetToSource(T t) {
        throw new UnsupportedOperationException("Converting to module is not supported");
    }

}
