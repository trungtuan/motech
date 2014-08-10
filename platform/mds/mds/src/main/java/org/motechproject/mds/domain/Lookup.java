package org.motechproject.mds.domain;

import org.apache.commons.lang.StringUtils;
import org.motechproject.mds.dto.LookupDto;
import org.motechproject.mds.dto.LookupFieldDto;
import org.motechproject.mds.util.LookupName;
import org.motechproject.mds.util.ValidationUtil;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Join;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang.BooleanUtils.toBoolean;
import static org.motechproject.mds.util.Constants.Util.TRUE;

/**
 * The <code>Lookup</code> class contains information about single lookup
 */
@PersistenceCapable(identityType = IdentityType.DATASTORE, detachable = TRUE)
public class Lookup {

    @PrimaryKey
    @Persistent(valueStrategy = IdGeneratorStrategy.INCREMENT)
    private Long id;

    @Persistent
    private String lookupName;

    @Persistent
    private boolean singleObjectReturn;

    @Persistent
    private boolean exposedViaRest;

    @Persistent
    private Entity entity;

    @Persistent
    private boolean readOnly;

    @Persistent
    private String methodName;

    @Persistent(table = "LookupFields")
    @Join
    private List<Field> fields;

    @Persistent(defaultFetchGroup = TRUE)
    private List<String> rangeLookupFields;

    @Persistent(defaultFetchGroup = TRUE)
    private List<String> setLookupFields;

    @Persistent(defaultFetchGroup = TRUE)
    private Map<String, String> customOperators;

    @Persistent(defaultFetchGroup = TRUE)
    private Map<String, Boolean> useGenericParams;

    public Lookup() {
        this(null, false, false, null);
    }

    public Lookup(String lookupName, boolean singleObjectReturn, boolean exposedViaRest, List<Field> fields) {
        this(lookupName, singleObjectReturn, exposedViaRest, fields, false, LookupName.lookupMethod(lookupName));
    }

    public Lookup(String lookupName, boolean singleObjectReturn, boolean exposedViaRest, List<Field> fields, boolean readOnly,
                  String methodName) {
        this(lookupName, singleObjectReturn, exposedViaRest, fields, readOnly, methodName, Collections.<String>emptyList(),
                Collections.<String>emptyList(), new HashMap<String, String>(), new HashMap<String, Boolean>());
    }

    public Lookup(String lookupName, boolean singleObjectReturn, boolean exposedViaRest, List<Field> fields, boolean readOnly,
                  String methodName, List<String> rangeLookupFields, List<String> setLookupFields,
                  Map<String, String> customOperators) {
        this(lookupName, singleObjectReturn, exposedViaRest, fields, readOnly, methodName, rangeLookupFields,
                setLookupFields, customOperators, new HashMap<String, Boolean>());
    }

    public Lookup(String lookupName, boolean singleObjectReturn, boolean exposedViaRest, List<Field> fields, boolean readOnly,
                  String methodName, List<String> rangeLookupFields, List<String> setLookupFields,
                  Map<String, String> customOperators, Map<String, Boolean> useGenericParams) {
        setLookupName(lookupName);
        this.singleObjectReturn = singleObjectReturn;
        this.exposedViaRest = exposedViaRest;
        this.fields = fields;
        this.readOnly = readOnly;
        this.methodName = methodName;
        this.rangeLookupFields = rangeLookupFields;
        this.setLookupFields = setLookupFields;
        this.customOperators = customOperators;
        this.useGenericParams = useGenericParams;
    }

    public Lookup(String lookupName, boolean singleObjectReturn, boolean exposedViaRest, List<Field> fields, Entity entity) {
        this(lookupName, singleObjectReturn, exposedViaRest, fields);
        this.entity = entity;
    }

    public Lookup(LookupDto lookupDto, List<Field> lookupFields) {
        update(lookupDto, lookupFields);
    }

    public LookupDto toDto() {
        List<LookupFieldDto> lookupFields = new ArrayList<>();

        if (fields != null) {
            for (Field field : fields) {
                LookupFieldDto.Type lookupFieldType = LookupFieldDto.Type.VALUE;

                if (isRangeParam(field)) {
                    lookupFieldType = LookupFieldDto.Type.RANGE;
                } else if (isSetParam(field)) {
                    lookupFieldType = LookupFieldDto.Type.SET;
                }

                String fieldName = field.getName();

                String customOperator = getCustomOperators().get(fieldName);
                boolean useGenericParam = toBoolean(getUseGenericParams().get(fieldName));

                lookupFields.add(new LookupFieldDto(field.getId(), fieldName, lookupFieldType, customOperator, useGenericParam));
            }
        }

        return new LookupDto(id, lookupName, singleObjectReturn, exposedViaRest,
                lookupFields, readOnly, methodName);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLookupName() {
        return lookupName;
    }

    public final void setLookupName(String lookupName) {
        ValidationUtil.validateNoJavaKeyword(lookupName);
        this.lookupName = lookupName;
    }

    public Map<String, String> getCustomOperators() {
        if (customOperators == null) {
            customOperators = new HashMap<>();
        }
        return customOperators;
    }

    public void setCustomOperators(Map<String, String> customOperators) {
        this.customOperators = customOperators;
    }

    public boolean isSingleObjectReturn() {
        return singleObjectReturn;
    }

    public void setSingleObjectReturn(boolean singleObjectReturn) {
        this.singleObjectReturn = singleObjectReturn;
    }

    public boolean isExposedViaRest() {
        return exposedViaRest;
    }

    public void setExposedViaRest(boolean exposedViaRest) {
        this.exposedViaRest = exposedViaRest;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getMethodName() {
        return (StringUtils.isBlank(methodName)) ? LookupName.lookupMethod(lookupName) : methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public final List<String> getRangeLookupFields() {
        if (rangeLookupFields == null) {
            rangeLookupFields = new ArrayList<>();
        }
        return rangeLookupFields;
    }

    public void setRangeLookupFields(List<String> rangeLookupFields) {
        this.rangeLookupFields = rangeLookupFields;
    }

    public final List<String> getSetLookupFields() {
        if (setLookupFields == null) {
            setLookupFields = new ArrayList<>();
        }
        return setLookupFields;
    }

    public void setSetLookupFields(List<String> setLookupFields) {
        this.setLookupFields = setLookupFields;
    }

    public final Field getLookupFieldByName(String name) {
        for (Field field : getFields()) {
            if (StringUtils.equals(name, field.getName())) {
                return field;
            }
        }
        return null;
    }

    public final Field getLookupFieldById(Long id) {
        for (Field field : getFields()) {
            if (Objects.equals(field.getId(), id)) {
                return field;
            }
        }
        return null;
    }

    public Map<String, Boolean> getUseGenericParams() {
        if (useGenericParams == null) {
            useGenericParams = new HashMap<>();
        }

        return useGenericParams;
    }

    public void setUseGenericParams(Map<String, Boolean> useGenericParams) {
        this.useGenericParams = useGenericParams;
    }

    public Lookup copy(List<Field> fields) {
        List<Field> lookupFields = new ArrayList<>();
        for (Field lookupField : this.fields) {
            for (Field newField : fields) {
                if (lookupField.getName().equals(newField.getName())) {
                    lookupFields.add(newField);
                    break;
                }
            }
        }

        return new Lookup(lookupName, singleObjectReturn, exposedViaRest, lookupFields, readOnly, methodName,
                getRangeLookupFields(), getSetLookupFields(), customOperators, useGenericParams);
    }

    public final void update(LookupDto lookupDto, List<Field> lookupFields) {
        singleObjectReturn = lookupDto.isSingleObjectReturn();
        exposedViaRest = lookupDto.isExposedViaRest();
        setLookupName(lookupDto.getLookupName());
        fields = lookupFields;
        methodName = lookupDto.getMethodName();
        readOnly = lookupDto.isReadOnly();

        updateCustomOperators(lookupDto);
        updateLookupFields(lookupDto);
        updateUseGenericParams(lookupDto);
    }

    public boolean isRangeParam(Field field) {
        return getRangeLookupFields().contains(field.getName());
    }

    public boolean isSetParam(Field field) {
        return getSetLookupFields().contains(field.getName());
    }

    private void updateLookupFields(LookupDto lookupDto) {
        getRangeLookupFields().clear();
        getSetLookupFields().clear();
        for (LookupFieldDto lookupFieldDto : lookupDto.getLookupFields()) {
            String name = (lookupFieldDto.getId() == null) ?
                    lookupFieldDto.getName() :
                    getLookupFieldById(lookupFieldDto.getId()).getName();

            if (lookupFieldDto.getType() == LookupFieldDto.Type.RANGE) {
                getRangeLookupFields().add(name);
            } else if (lookupFieldDto.getType() == LookupFieldDto.Type.SET) {
                getSetLookupFields().add(name);
            }
        }
    }

    private void updateCustomOperators(LookupDto lookupDto) {
        getCustomOperators().clear();
        for (LookupFieldDto lookupField : lookupDto.getLookupFields()) {
            String customOperator = lookupField.getCustomOperator();
            if (StringUtils.isNotBlank(customOperator)) {
                getCustomOperators().put(lookupField.getName(), customOperator);
            }
        }
    }

    private void updateUseGenericParams(LookupDto lookupDto) {
        getUseGenericParams().clear();

        for (LookupFieldDto lookupField : lookupDto.getLookupFields()) {
            getUseGenericParams().put(lookupField.getName(), lookupField.isUseGenericParam());
        }

    }

}
