package ru.citeck.ecos.model.deploy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.deploy.dto.EcosTypeDeployDto;
import ru.citeck.ecos.model.deploy.service.impl.EcosTypeDeployServiceImpl;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;

@ExtendWith(SpringExtension.class)
public class EcosTypeDeployServiceTest {

    @Mock
    private EcosTypeRepository typeRepository;

    @Mock
    private EcosAssociationRepository associationRepository;

    private EcosTypeDeployService typeDeployService;

    @BeforeEach
    public void init() {
        typeDeployService = new EcosTypeDeployServiceImpl(typeRepository, associationRepository);
    }

    @Test
    public void deployTest() {
        EcosAssociationDto assoc = new EcosAssociationDto("assocId", "aname", "atitle",
            RecordRef.EMPTY, RecordRef.EMPTY);
        EcosTypeDeployDto dto = new EcosTypeDeployDto();
        dto.setId("id");
        dto.setName("name");
        dto.setDescription("desc");
        dto.setTenant("tenant");
        dto.setParent(RecordRef.create("type", "parentId"));
        dto.setAssociations(Collections.singleton(assoc));

        EcosTypeEntity parent = new EcosTypeEntity();
        parent.setExtId("parentId");
        parent.setName("pname");
        parent.setDescription("pdesc");
        parent.setTenant("ptenant");
        parent.setParent(null);
        parent.setAssocsToOther(null);

        Mockito.when(typeRepository.findByExtId(parent.getExtId())).thenReturn(Optional.of(parent));
        Mockito.when(associationRepository.saveAll(Mockito.anyList())).thenReturn(Mockito.anyList());


        typeDeployService.deploy(dto);


        Mockito.verify(typeRepository, Mockito.times(1)).findByExtId(dto.getId());
        Mockito.verify(typeRepository, Mockito.times(1)).save(Mockito.any(EcosTypeEntity.class));
        Mockito.verify(associationRepository, Mockito.times(1)).saveAll(Mockito.any());
    }
}
