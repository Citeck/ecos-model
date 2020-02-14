package ru.citeck.ecos.model.utils;

import ecos.com.fasterxml.jackson210.core.JsonProcessingException;
import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;

@Slf4j
public class JsonUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String safeWriteValueAsJsonString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Json write error", e);
            return null;
        }
    }

    public static <T> T safeReadJsonValue(String valueStr, Class<T> type) {
        return safeReadJsonValue(valueStr, type, null);
    }

    public static <T> T safeReadJsonValue(String valueStr, Class<T> type, T def) {
        if (StringUtils.isBlank(valueStr)) {
            return def;
        }
        try {
            Character firstNotEmptyChar = null;
            for (int i = 0; i < valueStr.length(); i++) {
                char ch = valueStr.charAt(i);
                if (ch != ' ') {
                    firstNotEmptyChar = ch;
                    break;
                }
            }
            if (firstNotEmptyChar != null && firstNotEmptyChar == '{') {
                return mapper.readValue(valueStr, type);
            } else {
                Constructor<T> constructor = type.getConstructor(String.class);
                if (constructor != null) {
                    return constructor.newInstance(valueStr);
                } else {
                    throw new RuntimeException("String is not json and type doesn't " +
                                               "have constructor with string parameter");
                }
            }
        } catch (Exception e) {
            log.error("Parse error. Value: '" + valueStr + "' Type: " + type, e);
            return def;
        }
    }
}
