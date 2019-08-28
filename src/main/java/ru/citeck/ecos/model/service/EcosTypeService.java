package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosTypeDto;

import java.util.List;
import java.util.Optional;

public interface EcosTypeService {

    List<EcosTypeDto> getAll();

    List<EcosTypeDto> getAll(List<Long> ids);

    Optional<EcosTypeDto> getById(Long id);

    void delete(Long id);

    void update(EcosTypeDto mutable);
}
