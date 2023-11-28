package ru.citeck.ecos.model.domain.wkgsch

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.wkgsch.lib.calendar.WorkingCalendarService
import ru.citeck.ecos.wkgsch.lib.calendar.WorkingCalendarServiceImpl
import ru.citeck.ecos.wkgsch.lib.calendar.repo.WorkingCalendarRecordsRepo
import ru.citeck.ecos.wkgsch.lib.schedule.WorkingScheduleService
import ru.citeck.ecos.wkgsch.lib.schedule.local.LocalWorkingScheduleService
import ru.citeck.ecos.wkgsch.lib.schedule.remote.WorkingScheduleRemoteActions
import ru.citeck.ecos.wkgsch.lib.schedule.remote.api.records.WorkingScheduleActionRecordsDao
import ru.citeck.ecos.wkgsch.lib.schedule.remote.api.webapi.WorkingScheduleActionWebExecutor

@Configuration
class WorkingScheduleEmodelConfig {

    @Bean
    fun workingCalendarService(recordsService: RecordsService): WorkingCalendarService {
        val repo = WorkingCalendarRecordsRepo()
        repo.init(recordsService)
        val service = WorkingCalendarServiceImpl()
        service.init(repo)
        return service
    }

    @Bean
    fun workingScheduleService(
        recordsService: RecordsService,
        workingCalendarService: WorkingCalendarService,
        webAppApi: EcosWebAppApi
    ): WorkingScheduleService {
        val service = LocalWorkingScheduleService()
        service.init(recordsService, workingCalendarService, webAppApi)
        return service
    }

    @Bean
    fun workingScheduleRemoteActions(workingScheduleService: WorkingScheduleService): WorkingScheduleRemoteActions {
        val actions = WorkingScheduleRemoteActions()
        actions.init(workingScheduleService)
        return actions
    }

    @Bean
    fun workingScheduleActionRecordsDao(workingScheduleRemoteActions: WorkingScheduleRemoteActions): RecordsDao {
        val dao = WorkingScheduleActionRecordsDao()
        dao.init(workingScheduleRemoteActions)
        return dao
    }

    @Bean
    fun workingScheduleActionWebExecutor(workingScheduleRemoteActions: WorkingScheduleRemoteActions): EcosWebExecutor {
        val executor = WorkingScheduleActionWebExecutor()
        executor.init(workingScheduleRemoteActions)
        return executor
    }
}
