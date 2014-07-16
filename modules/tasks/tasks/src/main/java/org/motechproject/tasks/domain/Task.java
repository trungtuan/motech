package org.motechproject.tasks.domain;

import org.apache.commons.collections.Predicate;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.motechproject.mds.annotations.Cascade;
import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.tasks.json.TaskDeserializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.apache.commons.collections.CollectionUtils.find;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

/**
 * A task is set of actions that are executed in response to a trigger. The actions and the trigger are defined by their respective {@link Channel}s.
 */
@Entity
@JsonDeserialize(using = TaskDeserializer.class)
public class Task {
    private Long id;
    private String description;
    private String name;

    @Field
    @Cascade(delete = true)
    private List<TaskActionInformation> actions;

    @Field
    @Cascade(delete = true)
    private TaskTriggerInformation trigger;

    private boolean enabled;

    @Field
    @Cascade(delete = true)
    private Set<TaskError> validationErrors;

    @Field
    @Cascade(delete = true)
    private TaskConfig taskConfig;

    private boolean hasRegisteredChannel;

    public Task() {
        this(null, null, null);
    }

    public Task(String name, TaskTriggerInformation trigger, List<TaskActionInformation> actions) {
        this(name, trigger, actions, null, true, true);
    }

    public Task(String name, TaskTriggerInformation trigger, List<TaskActionInformation> actions,
                TaskConfig taskConfig, boolean enabled, boolean hasRegisteredChannel) {
        this.name = name;
        this.actions = actions == null ? new ArrayList<TaskActionInformation>() : actions;
        this.trigger = trigger;
        this.enabled = enabled;
        this.hasRegisteredChannel = hasRegisteredChannel;
        this.taskConfig = taskConfig == null ? new TaskConfig() : taskConfig;
        this.validationErrors = new HashSet<>();
    }

    public void addAction(TaskActionInformation action) {
        if (action != null) {
            actions.add(action);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskTriggerInformation getTrigger() {
        return trigger;
    }

    public void setTrigger(final TaskTriggerInformation trigger) {
        this.trigger = trigger;
    }

    public List<TaskActionInformation> getActions() {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        return actions;
    }

    public void setActions(final List<TaskActionInformation> actions) {
        this.actions = actions;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void addValidationErrors(Set<TaskError> validationErrors) {
        this.getValidationErrors().addAll(validationErrors);
    }

    public void removeValidationError(final String message) {
        TaskError taskError = (TaskError) find(getValidationErrors(), new Predicate() {
            @Override
            public boolean evaluate(Object object) {
                return object instanceof TaskError
                        && ((TaskError) object).getMessage().equalsIgnoreCase(message);
            }
        });

        getValidationErrors().remove(taskError);
    }

    public void setValidationErrors(Set<TaskError> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public Set<TaskError> getValidationErrors() {
        if (validationErrors == null) {
            validationErrors = new HashSet<>();
        }
        return validationErrors;
    }

    public boolean hasValidationErrors() {
        return isNotEmpty(validationErrors);
    }

    public TaskConfig getTaskConfig() {
        return taskConfig;
    }

    public void setTaskConfig(TaskConfig taskConfig) {
        this.taskConfig = taskConfig;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, description, name, getActions(), trigger, enabled, getValidationErrors(), taskConfig
        );
    }

    @Override   // NO CHECKSTYLE CyclomaticComplexity
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final Task other = (Task) obj;

        return Objects.equals(id, this.id)
                && Objects.equals(this.description, other.description)
                && Objects.equals(this.name, other.name)
                && Objects.equals(getActions(), other.getActions())
                && Objects.equals(this.trigger, other.trigger)
                && Objects.equals(this.enabled, other.enabled)
                && Objects.equals(this.hasRegisteredChannel, other.hasRegisteredChannel)
                && Objects.equals(this.getValidationErrors(), other.getValidationErrors())
                && Objects.equals(this.taskConfig, other.taskConfig);
    }

    @Override
    public String toString() {
        return String.format(
                "Task{id=%d, description='%s', name='%s', actions=%s, trigger=%s, enabled=%s, validationErrors=%s, taskConfig=%s, hasRegisteredChannel=%s} ",
                id, description, name, getActions(), trigger, enabled, getValidationErrors(), taskConfig, hasRegisteredChannel
        );
    }

    @JsonProperty("hasRegisteredChannel")
    public void setHasRegisteredChannel(boolean hasRegisteredChannel) {
        this.hasRegisteredChannel = hasRegisteredChannel;
    }

    @JsonProperty("hasRegisteredChannel")
    public boolean hasRegisteredChannel() {
        return hasRegisteredChannel;
    }
}
