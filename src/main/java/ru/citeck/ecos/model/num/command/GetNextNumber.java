package ru.citeck.ecos.model.num.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commands.CommandExecutor;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.annotation.CommandType;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.num.service.NumTemplateService;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;

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
            return new Response(numTemplateService.getNextNumber(command.templateRef, command.getModel()));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @CommandType("ecos.number.template.get-next")
    public static class Command {
        private RecordRef templateRef;
        private ObjectData model;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long number;
    }
}

