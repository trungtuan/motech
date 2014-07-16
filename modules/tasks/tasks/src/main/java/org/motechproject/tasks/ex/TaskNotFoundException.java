package org.motechproject.tasks.ex;

import static java.lang.String.format;

public class TaskNotFoundException extends IllegalArgumentException {

    public TaskNotFoundException(Long taskId) {
        super(format("Not found task with ID: %s", taskId));
    }
}
