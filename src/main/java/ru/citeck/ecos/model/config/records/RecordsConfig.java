package ru.citeck.ecos.model.config.records;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records2.source.dao.RecordsDAO;

import java.util.List;

@Configuration
public class RecordsConfig extends RecordsServiceFactory {

    @Bean
    public RecordsService recordsService(List<RecordsDAO> recordsDao) {
        RecordsService recordsService = super.createRecordsService();
        recordsDao.forEach(recordsService::register);
        return recordsService;
    }

    @Bean
    public PredicateService predicateService() {
        return super.createPredicateService();
    }

    @Bean
    public RestHandler restHandler(RecordsService recordsService) {
        return new RestHandler(recordsService);
    }
}
