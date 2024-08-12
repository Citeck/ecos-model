package ru.citeck.ecos.model.num.api.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commands.CommandExecutor;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.annotation.CommandType;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.num.service.NumTemplateService;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import jakarta.annotation.PostConstruct;

public class GetNextNumber {

    @Service
    @RequiredArgsConstructor
    public static class GetNextNumberCommandExecutor implements CommandExecutor<Command> {

        private final NumTemplateService numTemplateService;
        private final CommandsService commandsService;

        @PostConstruct
        public void init() {
            commandsService.addExecutor(this);
        }

        @Nullable
        @Override
        public Object execute(Command command) {
            boolean increment = !Boolean.FALSE.equals(command.increment);
            if (StringUtils.isNotBlank(command.counterKey)) {
                return new Response(
                    numTemplateService.getNextNumber(
                        command.templateRef,
                        command.counterKey,
                        increment
                    )
                );
            } else {
                return new Response(
                    numTemplateService.getNextNumber(
                        command.templateRef,
                        command.getModel(),
                        increment
                    )
                );
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @CommandType("ecos.number.template.get-next")
    public static class Command {

        private EntityRef templateRef;
        private ObjectData model;
        private String counterKey;
        private Boolean increment;

        public Command(EntityRef templateRef, ObjectData model) {
            this.templateRef = templateRef;
            this.model = model;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long number;
    }
}
