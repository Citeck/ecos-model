package ru.citeck.ecos.model.converter.module.impl;

import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.model.converter.module.AbstractModuleConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.eapps.listener.SectionModule;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converting section module objects to local dto objects.
 *
 * @see ru.citeck.ecos.model.dto.SectionDto
 */
@Component
public class SectionModuleConverter extends AbstractModuleConverter<SectionModule, SectionDto> {

    @Override
    public SectionDto moduleToDto(SectionModule module) {

        SectionDto dto = new SectionDto();

        dto.setId(module.getId());
        dto.setName(module.getName());
        dto.setDescription(module.getDescription());
        dto.setTenant(Strings.EMPTY);
        dto.setAttributes(module.getAttributes());

        List<ModuleRef> typesModuleRefs = module.getTypes();
        if (typesModuleRefs != null && !typesModuleRefs.isEmpty()) {
            Set<RecordRef> sectionRecordRefs = typesModuleRefs.stream()
                .map(t -> RecordRef.create(TypeRecordsDao.ID, t.getId()))
                .collect(Collectors.toSet());
            dto.setTypes(sectionRecordRefs);
        } else {
            dto.setTypes(Collections.emptySet());
        }

        return dto;
    }
}
