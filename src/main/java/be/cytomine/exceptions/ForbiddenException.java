package be.cytomine.exceptions;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;

@Slf4j
public class ForbiddenException extends CytomineException {

    /**
     * Message map with this exception
     * @param message Message
     */
    public ForbiddenException(String message) {
        this(message, new LinkedHashMap<Object, Object>());
    }
    public ForbiddenException(String message, LinkedHashMap<Object, Object> values) {
        super(message,403, values);
        log.warn(message);
    }

}