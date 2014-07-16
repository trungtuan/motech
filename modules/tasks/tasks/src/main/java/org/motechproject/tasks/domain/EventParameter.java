package org.motechproject.tasks.domain;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.tasks.contract.EventParameterRequest;

import java.util.Objects;

@Entity
public class EventParameter extends Parameter {

    private static final long serialVersionUID = 2564446352940524099L;

    @Field
    private String eventKey;

    public EventParameter() {
        this(null, null);
    }

    public EventParameter(String displayName, String eventKey) {
        this(displayName, eventKey, ParameterType.UNICODE);
    }

    public EventParameter(final String displayName, final String eventKey, final ParameterType type) {
        super(displayName, type);
        this.eventKey = eventKey;
    }

    public EventParameter(EventParameterRequest eventParameterRequest) {
        this(eventParameterRequest.getDisplayName(), eventParameterRequest.getEventKey(), ParameterType.fromString(eventParameterRequest.getType()));
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(final String eventKey) {
        this.eventKey = eventKey;
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

        final EventParameter other = (EventParameter) obj;

        return Objects.equals(this.eventKey, other.eventKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventKey);
    }

    @Override
    public String toString() {
        return String.format("EventParameter{eventKey='%s'} %s", eventKey, super.toString());
    }
}
