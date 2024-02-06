package ru.citeck.ecos.model.num.api.command

import org.springframework.stereotype.Service
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.model.num.service.NumTemplateService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import javax.annotation.PostConstruct

@Service
class SetNextNumberCommandExecutor(
    val commandsService: CommandsService,
    val numTemplateService: NumTemplateService
) : CommandExecutor<SetNextNumberCommand> {

    @PostConstruct
    fun init() {
        commandsService.addExecutor(this)
    }

    override fun execute(command: SetNextNumberCommand): SetNextNumberResponse {
        numTemplateService.setNextNumber(command.templateRef, command.counterKey, command.nextNumber)
        return SetNextNumberResponse()
    }
}

@CommandType("ecos.number.template.set-next")
class SetNextNumberCommand(
    val templateRef: EntityRef,
    val counterKey: String,
    val nextNumber: Long
)

class SetNextNumberResponse(
    val status: String = "OK"
)
