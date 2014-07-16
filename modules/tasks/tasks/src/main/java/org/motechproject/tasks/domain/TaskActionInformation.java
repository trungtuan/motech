package org.motechproject.tasks.domain;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Represents an action event configured with a {@link Task}
 */
@Entity
public class TaskActionInformation extends TaskEventInformation {
    private static final long serialVersionUID = -132464255615128442L;

    @Field
    private String serviceInterface;
    @Field
    private String serviceMethod;
    @Field
    private Map<String, String> values;

    public TaskActionInformation() {
        this(null, null, null, null, null);
    }

    public TaskActionInformation(String displayName, String channelName, String moduleName, String moduleVersion,
                                 String subject) {
        this(displayName, channelName, moduleName, moduleVersion, subject, (Map<String, String>) null);
    }

    public TaskActionInformation(String displayName, String channelName, String moduleName, String moduleVersion,
                                 String subject, Map<String, String> values) {
        this(displayName, channelName, moduleName, moduleVersion, subject, null, null, values);
    }

    public TaskActionInformation(String displayName, String channelName, String moduleName, String moduleVersion,
                                 String serviceInterface, String serviceMethod) {
        this(displayName, channelName, moduleName, moduleVersion, null, serviceInterface, serviceMethod, null);
    }

    public TaskActionInformation(String displayName, // NO CHECKSTYLE More than 7 parameters (found 8).
                                 String channelName, String moduleName, String moduleVersion, String subject,
                                 String serviceInterface, String serviceMethod, Map<String, String> values) {
        super(displayName, channelName, moduleName, moduleVersion, subject);

        this.serviceInterface = serviceInterface;
        this.serviceMethod = serviceMethod;
        this.values = values == null ? new HashMap<String, String>() : values;
    }

    public boolean hasService() {
        return isNotBlank(serviceInterface) && isNotBlank(serviceMethod);
    }

    public String getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public String getServiceMethod() {
        return serviceMethod;
    }

    public void setServiceMethod(String serviceMethod) {
        this.serviceMethod = serviceMethod;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values.clear();

        if (values != null) {
            this.values.putAll(values);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceInterface, serviceMethod, values);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        if (!super.equals(obj)) {
            return false;
        }

        final TaskActionInformation other = (TaskActionInformation) obj;

        return Objects.equals(this.serviceInterface, other.serviceInterface) &&
                Objects.equals(this.serviceMethod, other.serviceMethod) &&
                Objects.equals(this.values, other.values);
    }

    @Override
    public String toString() {
        return String.format("TaskActionInformation{serviceInterface='%s', serviceMethod='%s', values=%s} %s",
                serviceInterface, serviceMethod, values, super.toString());
    }
}
