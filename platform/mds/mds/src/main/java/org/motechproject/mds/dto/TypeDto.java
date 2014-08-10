package org.motechproject.mds.dto;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.motechproject.commons.date.model.Time;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang.StringUtils.startsWith;

/**
 * The <code>TypeDto</code> class contains information about an available field in an entity.
 */
public class TypeDto {
    private Long id;
    private String displayName;
    private String description;
    private String defaultName;
    private String typeClass;

    /**
     * Constant <code>PERIOD</code> is a representation of the MDS Period type.
     */
    public static final TypeDto PERIOD = new TypeDto(
            "mds.field.period", "mds.field.description.period", "period", Period.class.getName()
    );

    /**
     * Constant <code>MAP</code> is a representation of the MDS Map type.
     */
    public static final TypeDto MAP = new TypeDto(
            "mds.field.map", "mds.field.description.map", "map", Map.class.getName()
    );

    /**
     * Constant <code>BLOB</code> is a representation of the MDS BLOB type.
     */
    public static final TypeDto BLOB = new TypeDto(
            "mds.field.blob", "mds.field.description.blob", "blob", Byte[].class.getName()
    );

    /**
     * Constant <code>INTEGER</code> is a representation of the MDS Integer type.
     */
    public static final TypeDto INTEGER = new TypeDto(
            "mds.field.integer", "mds.field.description.integer", "integer", Integer.class.getName()
    );

    /**
     * Constant <code>STRING</code> is a representation of the MDS String type.
     */
    public static final TypeDto STRING = new TypeDto(
            "mds.field.string", "mds.field.description.string", "str", String.class.getName()
    );

    /**
     * Constant <code>BOOLEAN</code> is a representation of the MDS Boolean type.
     */
    public static final TypeDto BOOLEAN = new TypeDto(
            "mds.field.boolean", "mds.field.description.boolean", "bool", Boolean.class.getName()
    );

    /**
     * Constant <code>DATE</code> is a representation of the MDS Date type.
     */
    public static final TypeDto DATE = new TypeDto(
            "mds.field.date", "mds.field.description.date", "date", Date.class.getName()
    );

    /**
     * Constant <code>TIME</code> is a representation of the MDS Time type.
     */
    public static final TypeDto TIME = new TypeDto(
            "mds.field.time", "mds.field.description.time", "time", Time.class.getName()
    );

    /**
     * Constant <code>DATETIME</code> is a representation of the MDS DateTime type.
     */
    public static final TypeDto DATETIME = new TypeDto(
            "mds.field.datetime", "mds.field.description.datetime", "datetime", DateTime.class.getName()
    );

    /**
     * Constant <code>DOUBLE</code> is a representation of the MDS Decimal type.
     */
    public static final TypeDto DOUBLE = new TypeDto(
            "mds.field.decimal", "mds.field.description.decimal", "dec", Double.class.getName()
    );

    /**
     * Constant <code>LIST</code> is a representation of the MDS Combobox type.
     */
    public static final TypeDto LIST = new TypeDto(
            "mds.field.combobox", "mds.field.description.combobox", "list", List.class.getName()
    );

    /**
     * Constant <code>LOCAL_DATE</code> is a representation of the {@link org.joda.time.LocalDate} type.
     */
    public static final TypeDto LOCAL_DATE = new TypeDto(
            "mds.field.localDate", "mds.field.description.localDate", "localDate", LocalDate.class.getName()
    );

    /**
     * Constant <code>LONG</code> is a representation of the MDS Long type.
     */
    public static final TypeDto LONG = new TypeDto(
            "mds.field.long", "mds.field.description.long", "long", Long.class.getName()
    );

    public TypeDto() {
        this(null, null, null, null);
    }

    public TypeDto(String displayName, String description, String defaultName, String typeClass) {
        this(null, displayName, description, defaultName, typeClass);
    }

    public TypeDto(Long id, String displayName, String description, String defaultName,
                   String typeClass) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.defaultName = defaultName;
        this.typeClass = typeClass;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName;
    }

    public String getTypeClass() {
        return typeClass;
    }

    public void setTypeClass(String typeClass) {
        this.typeClass = typeClass;
    }

    @JsonIgnore
    public boolean isCombobox() {
        return equalsIgnoreCase(displayName, "mds.field.combobox");
    }

    @JsonIgnore
    public boolean isRelationship() {
        return startsWith(displayName, "mds.field.relationship");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj, new String[]{"id"});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
