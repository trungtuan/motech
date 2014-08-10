package org.motechproject.mds.service.impl;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.motechproject.mds.builder.MDSConstructor;
import org.motechproject.mds.domain.ComboboxHolder;
import org.motechproject.mds.domain.Entity;
import org.motechproject.mds.domain.EntityDraft;
import org.motechproject.mds.domain.Field;
import org.motechproject.mds.domain.MdsEntity;
import org.motechproject.mds.domain.FieldMetadata;
import org.motechproject.mds.domain.FieldSetting;
import org.motechproject.mds.domain.FieldValidation;
import org.motechproject.mds.domain.Lookup;
import org.motechproject.mds.domain.Type;
import org.motechproject.mds.domain.TypeSetting;
import org.motechproject.mds.domain.TypeValidation;
import org.motechproject.mds.dto.AdvancedSettingsDto;
import org.motechproject.mds.dto.DraftData;
import org.motechproject.mds.dto.DraftResult;
import org.motechproject.mds.dto.EntityDto;
import org.motechproject.mds.dto.FieldBasicDto;
import org.motechproject.mds.dto.FieldDto;
import org.motechproject.mds.dto.FieldValidationDto;
import org.motechproject.mds.dto.LookupDto;
import org.motechproject.mds.dto.LookupFieldDto;
import org.motechproject.mds.dto.MetadataDto;
import org.motechproject.mds.dto.SettingDto;
import org.motechproject.mds.dto.TypeDto;
import org.motechproject.mds.dto.ValidationCriterionDto;
import org.motechproject.mds.ex.EntityAlreadyExistException;
import org.motechproject.mds.ex.EntityChangedException;
import org.motechproject.mds.ex.EntityNotFoundException;
import org.motechproject.mds.ex.EntityReadOnlyException;
import org.motechproject.mds.ex.FieldNotFoundException;
import org.motechproject.mds.ex.NoSuchTypeException;
import org.motechproject.mds.javassist.MotechClassPool;
import org.motechproject.mds.repository.AllEntities;
import org.motechproject.mds.repository.AllEntityAudits;
import org.motechproject.mds.repository.AllEntityDrafts;
import org.motechproject.mds.repository.AllTypes;
import org.motechproject.mds.service.EntityService;
import org.motechproject.mds.service.MotechDataService;
import org.motechproject.mds.util.ClassName;
import org.motechproject.mds.util.Constants;
import org.motechproject.mds.util.FieldHelper;
import org.motechproject.mds.util.LookupName;
import org.motechproject.mds.util.SecurityMode;
import org.motechproject.mds.util.ServiceUtil;
import org.motechproject.mds.validation.EntityValidator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.motechproject.mds.repository.query.DataSourceReferenceQueryExecutionHelper.createLookupReferenceQuery;
import static org.motechproject.mds.repository.query.DataSourceReferenceQueryExecutionHelper.DATA_SOURCE_CLASS_NAME;
import static org.motechproject.mds.util.Constants.Util;
import static org.motechproject.mds.util.Constants.Util.TRUE;
import static org.motechproject.mds.util.SecurityUtil.getUserRoles;
import static org.motechproject.mds.util.SecurityUtil.getUsername;
import static org.motechproject.mds.util.Constants.Util.AUTO_GENERATED;
import static org.motechproject.mds.util.Constants.Util.AUTO_GENERATED_EDITABLE;

/**
 * Default implementation of {@link org.motechproject.mds.service.EntityService} interface.
 */
@Service
public class EntityServiceImpl implements EntityService {

    private static final Logger LOG = LoggerFactory.getLogger(EntityServiceImpl.class);

    private static final String NAME_PATH = "basic.name";

    private AllEntities allEntities;
    private AllTypes allTypes;
    private AllEntityDrafts allEntityDrafts;
    private AllEntityAudits allEntityAudits;
    private MDSConstructor mdsConstructor;

    private BundleContext bundleContext;
    private EntityValidator entityValidator;

    @Override
    @Transactional
    public Long getCurrentSchemaVersion(String className) {
        Entity entity = allEntities.retrieveByClassName(className);
        assertEntityExists(entity);

        return entity.getEntityVersion();
    }

    @Override
    @Transactional
    public void updateComboboxValues(Long entityId, Map<String, Collection> fieldValuesToUpdate) {
        Entity entity = allEntities.retrieveById(entityId);
        assertEntityExists(entity);
        boolean doEntityUpdate = false;

        for (Map.Entry<String, Collection> fieldUpdate : fieldValuesToUpdate.entrySet()) {
            Field field = entity.getField(fieldUpdate.getKey());
            if (field == null) {
                throw new FieldNotFoundException();
            }

            ComboboxHolder cbHolder = new ComboboxHolder(field);
            List<String> cbValues = new ArrayList<>(Arrays.asList(cbHolder.getValues()));
            boolean updateField = false;

            for (Object peristedVal : fieldUpdate.getValue()) {
                String peristedValAsStr = peristedVal.toString();
                peristedValAsStr = peristedValAsStr.trim().replaceAll(" ", "%20");
                if (!cbValues.contains(peristedValAsStr)) {
                    cbValues.add(peristedValAsStr);
                    updateField = true;
                }
            }

            if (updateField) {
                FieldSetting cbValuesSetting = field.getSettingByName(Constants.Settings.COMBOBOX_VALUES);
                if (cbValuesSetting == null) {
                    throw new IllegalArgumentException("Field " + field.getName() + " is not a comboBox");
                }

                cbValuesSetting.setValue(StringUtils.join(cbValues, '\n'));

                doEntityUpdate = true;
            }
        }

        if (doEntityUpdate) {
            allEntities.update(entity);
        }
    }

    @Override
    @Transactional
    public EntityDto createEntity(EntityDto entityDto) throws IOException {
        String packageName = ClassName.getPackage(entityDto.getClassName());
        boolean fromUI = StringUtils.isEmpty(packageName);
        String username = getUsername();

        if (fromUI) {
            String className;
            if (entityDto.getName().contains(" ")) {
                entityDto.setName(entityDto.getName().trim());
                StringBuilder stringBuilder = new StringBuilder();
                String[] nameParts = entityDto.getName().split(" ");
                for (String part : nameParts) {
                    if (part.length() > 0) {
                        stringBuilder.append(Character.toUpperCase(part.charAt(0)));
                        if (part.length() > 1) {
                            stringBuilder.append(part.substring(1));
                        }
                    }
                }
                className = String.format("%s.%s", Constants.PackagesGenerated.ENTITY, stringBuilder.toString());
            } else {
                // in this situation entity.getName() returns a simple name of class
                className = String.format("%s.%s", Constants.PackagesGenerated.ENTITY, entityDto.getName());
            }
            entityDto.setClassName(className);
        }

        if (allEntities.contains(entityDto.getClassName())) {
            throw new EntityAlreadyExistException();
        }

        Entity entity = allEntities.create(entityDto);

        LOG.debug("Adding default fields to the entity which do not extend MdsEntity");
        if (!MdsEntity.class.getName().equalsIgnoreCase(entityDto.getSuperClass())) {
            addDefaultFields(entity);
        }

        if (username != null) {
            allEntityAudits.createAudit(entity, username);
        }

        return entity.toDto();
    }

    @Override
    @Transactional
    public DraftResult saveDraftEntityChanges(Long entityId, DraftData draftData, String username) {
        EntityDraft draft = getEntityDraft(entityId, username);

        if (draftData.isCreate()) {
            createFieldForDraft(draft, draftData);
        } else if (draftData.isEdit()) {
            draftEdit(draft, draftData);
        } else if (draftData.isRemove()) {
            draftRemove(draft, draftData);
        }

        return new DraftResult(draft.isChangesMade(), draft.isOutdated());
    }

    @Override
    @Transactional
    public DraftResult saveDraftEntityChanges(Long entityId, DraftData draftData) {
        return saveDraftEntityChanges(entityId, draftData, getUsername());
    }


    private void draftEdit(EntityDraft draft, DraftData draftData) {
        if (draftData.isForAdvanced()) {
            editAdvancedForDraft(draft, draftData);
        } else if (draftData.isForField()) {
            editFieldForDraft(draft, draftData);
        } else if (draftData.isForSecurity()) {
            editSecurityForDraft(draft, draftData);
        }
    }

    private void editSecurityForDraft(EntityDraft draft, DraftData draftData) {
        List value = (List) draftData.getValue(DraftData.VALUE);
        if (value != null) {
            String securityModeName = (String) value.get(0);
            SecurityMode securityMode = SecurityMode.getEnumByName(securityModeName);

            if (value.size() > 1) {
                List<String> list = (List<String>) value.get(1);
                draft.setSecurity(securityMode, list);
            } else {
                draft.setSecurityMode(securityMode);
            }

            allEntityDrafts.update(draft);
        }
    }

    private void editFieldForDraft(EntityDraft draft, DraftData draftData) {
        String fieldIdStr = draftData.getValue(DraftData.FIELD_ID).toString();
        if (StringUtils.isNotBlank(fieldIdStr)) {
            Long fieldId = Long.valueOf(fieldIdStr);
            Field field = draft.getField(fieldId);

            if (field != null) {
                String path = draftData.getPath();
                List value = (List) draftData.getValue(DraftData.VALUE);

                // Convert to dto for UI updates
                FieldDto dto = field.toDto();
                FieldHelper.setField(dto, path, value);

                //If field name was changed add this change to map
                if (NAME_PATH.equals(path)) {
                    Map<String, String> map = draft.getFieldNameChanges();

                    //Checking if field name was previously changed and updating new name in map or adding new entry
                    if (map.containsValue(field.getName())) {
                        for (String key : map.keySet()) {
                            if (field.getName().equals(map.get(key))) {
                                map.put(key, value.get(0).toString());
                            }
                        }
                    } else {
                        map.put(field.getName(), value.get(0).toString());
                    }
                    draft.setFieldNameChanges(map);
                }

                // Perform update
                field.update(dto);
                allEntityDrafts.update(draft);
            }
        }
    }

    private void editAdvancedForDraft(EntityDraft draft, DraftData draftData) {
        AdvancedSettingsDto advancedDto = draft.advancedSettingsDto();
        String path = draftData.getPath();
        List value = (List) draftData.getValue(DraftData.VALUE);
        entityValidator.validateAdvancedSettingsEdit(draft, path);
        FieldHelper.setField(advancedDto, path, value);
        setLookupMethodNames(advancedDto);

        draft.updateAdvancedSetting(advancedDto);

        allEntityDrafts.update(draft);
    }

    private void setLookupMethodNames(AdvancedSettingsDto advancedDto) {
        for (LookupDto lookup : advancedDto.getIndexes()) {
            lookup.setMethodName(LookupName.lookupMethod(lookup.getLookupName()));
        }
    }

    private void createFieldForDraft(EntityDraft draft, DraftData draftData) {
        String typeClass = draftData.getValue(DraftData.TYPE_CLASS).toString();
        String displayName = draftData.getValue(DraftData.DISPLAY_NAME).toString();
        String name = draftData.getValue(DraftData.NAME).toString();

        Type type = allTypes.retrieveByClassName(typeClass);

        if (type == null) {
            throw new NoSuchTypeException();
        }

        Set<Lookup> fieldLookups = new HashSet<>();

        Field field = new Field(draft, displayName, name, fieldLookups);
        field.setType(type);

        if (type.hasSettings()) {
            for (TypeSetting setting : type.getSettings()) {
                field.addSetting(new FieldSetting(field, setting));
            }
        }

        if (type.hasValidation()) {
            for (TypeValidation validation : type.getValidations()) {
                field.addValidation(new FieldValidation(field, validation));
            }
        }

        if (TypeDto.BLOB.getTypeClass().equals(typeClass)) {
            field.setUIDisplayable(false);
        } else {
            field.setUIDisplayable(true);
            field.setUIDisplayPosition((long) draft.getFields().size());
        }

        draft.addField(field);

        allEntityDrafts.update(draft);
    }


    private void draftRemove(EntityDraft draft, DraftData draftData) {
        Long fieldId = Long.valueOf(draftData.getValue(DraftData.FIELD_ID).toString());

        // will throw exception if it is used
        entityValidator.validateFieldNotUsedByLookups(draft, fieldId);

        draft.removeField(fieldId);
        allEntityDrafts.update(draft);
    }


    @Override
    @Transactional
    public void abandonChanges(Long entityId) {
        EntityDraft draft = getEntityDraft(entityId);
        if (draft != null) {
            allEntityDrafts.delete(draft);
        }
    }

    @Override
    @Transactional
    public void commitChanges(Long entityId, String changesOwner) {
        EntityDraft draft = getEntityDraft(entityId, changesOwner);
        if (draft.isOutdated()) {
            throw new EntityChangedException();
        }

        entityValidator.validateEntity(draft);

        Entity parent = draft.getParentEntity();
        String username = draft.getDraftOwnerUsername();

        mdsConstructor.updateFields(parent.getId(), draft.getFieldNameChanges());

        parent.updateFromDraft(draft);

        if (username != null) {
            allEntityAudits.createAudit(parent, username);
        }

        allEntityDrafts.delete(draft);
    }

    @Override
    @Transactional
    public void commitChanges(Long entityId) {
        commitChanges(entityId, getUsername());
    }

    @Override
    @Transactional
    public List<EntityDto> listWorkInProgress() {
        String username = getUsername();
        List<EntityDraft> drafts = allEntityDrafts.retrieveAll(username);

        List<EntityDto> entityDtoList = new ArrayList<>();
        for (EntityDraft draft : drafts) {
            if (draft.isChangesMade()) {
                entityDtoList.add(draft.toDto());
            }
        }

        return entityDtoList;
    }

    @Override
    @Transactional
    public AdvancedSettingsDto getAdvancedSettings(Long entityId) {
        return getAdvancedSettings(entityId, false);
    }

    @Override
    @Transactional
    public AdvancedSettingsDto getAdvancedSettings(Long entityId, boolean committed) {
        if (committed) {
            Entity entity = allEntities.retrieveById(entityId);
            return addNonPersistentAdvancedSettingsData(entity.advancedSettingsDto(), entity);
        } else {
            Entity entity = getEntityDraft(entityId);
            return addNonPersistentAdvancedSettingsData(entity.advancedSettingsDto(), entity);
        }
    }

    @Override
    @Transactional
    public void addLookups(Long entityId, Collection<LookupDto> lookups) {
        Entity entity = allEntities.retrieveById(entityId);
        assertEntityExists(entity);

        removeLookup(entity, lookups);
        addOrUpdateLookup(entity, lookups);
    }

    private void removeLookup(Entity entity, Collection<LookupDto> lookups) {
        Iterator<Lookup> iterator = entity.getLookups().iterator();

        while (iterator.hasNext()) {
            Lookup lookup = iterator.next();

            // don't remove user defined lookups
            if (!lookup.isReadOnly()) {
                continue;
            }

            boolean found = false;

            for (LookupDto lookupDto : lookups) {
                if (lookup.getLookupName().equalsIgnoreCase(lookupDto.getLookupName())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                iterator.remove();
            }
        }
    }

    private void addOrUpdateLookup(Entity entity, Collection<LookupDto> lookups) {
        for (LookupDto lookupDto : lookups) {
            Lookup lookup = entity.getLookupByName(lookupDto.getLookupName());
            List<Field> lookupFields = new ArrayList<>();
            for (LookupFieldDto lookupField : lookupDto.getLookupFields()) {
                String fieldName = lookupField.getName();

                Field field = entity.getField(fieldName);

                if (field == null) {
                    LOG.error("No field {} in entity {}", fieldName, entity.getClassName());
                } else {
                    lookupFields.add(field);
                }
            }

            if (lookup == null) {
                Lookup newLookup = new Lookup(lookupDto, lookupFields);
                entity.addLookup(newLookup);
            } else {
                lookup.update(lookupDto, lookupFields);
            }
        }
    }

    @Override
    @Transactional
    public void deleteEntity(Long entityId) {
        Entity entity = allEntities.retrieveById(entityId);

        assertWritableEntity(entity);

        if (entity.isDraft()) {
            entity = ((EntityDraft) entity).getParentEntity();
        }

        allEntityDrafts.deleteAll(entity);
        allEntities.delete(entity);
    }

    @Override
    @Transactional
    public List<EntityDto> listEntities() {
        return listEntities(false);
    }

    @Override
    @Transactional
    public List<EntityDto> listEntities(boolean withSecurityCheck) {
        List<EntityDto> entityDtos = new ArrayList<>();

        for (Entity entity : allEntities.retrieveAll()) {
            if (entity.isActualEntity()) {
                if (!withSecurityCheck || hasAccessToEntity(entity)) {
                    entityDtos.add(entity.toDto());
                }
            }
        }

        return entityDtos;
    }

    private boolean hasAccessToEntity(Entity entity) {
        SecurityMode mode = entity.getSecurityMode();
        Set<String> members = entity.getSecurityMembers();

        if (SecurityMode.USERS.equals(mode)) {
            return members.contains(getUsername());
        } else if (SecurityMode.ROLES.equals(mode)) {
            for (String role : getUserRoles()) {
                if (members.contains(role)) {
                    return true;
                }
            }

            // Only allowed roles can view, but current user
            // doesn't have any of the required roles
            return false;
        }

        // There's no user and role restriction, which means
        // the user can see this entity
        return true;
    }

    @Override
    @Transactional
    public EntityDto getEntity(Long entityId) {
        Entity entity = allEntities.retrieveById(entityId);
        return (entity == null) ? null : entity.toDto();
    }

    @Override
    @Transactional
    public EntityDto getEntityByClassName(String className) {
        Entity entity = allEntities.retrieveByClassName(className);
        return (entity == null) ? null : entity.toDto();
    }

    @Override
    @Transactional
    public List<EntityDto> getEntitiesWithLookups() {
        List<EntityDto> entities = new ArrayList<>();
        for (EntityDto entityDto : listEntities()) {
            if (!getEntityLookups(entityDto.getId()).isEmpty()) {
                entities.add(entityDto);
            }
        }
        return entities;
    }

    @Override
    @Transactional
    public List<LookupDto> getEntityLookups(Long entityId) {
        return getLookups(entityId, false);
    }

    private List<LookupDto> getLookups(Long entityId, boolean forDraft) {
        Entity entity = (forDraft) ? getEntityDraft(entityId) : allEntities.retrieveById(entityId);

        assertEntityExists(entity);

        List<LookupDto> lookupDtos = new ArrayList<>();
        for (Lookup lookup : entity.getLookups()) {
            lookupDtos.add(lookup.toDto());
        }

        return lookupDtos;
    }

    @Override
    @Transactional
    public List<FieldDto> getFields(Long entityId) {
        return getFields(entityId, true);
    }

    @Override
    @Transactional
    public List<FieldDto> getEntityFields(Long entityId) {
        return getFields(entityId, false);
    }

    private List<FieldDto> getFields(Long entityId, boolean forDraft) {
        Entity entity = (forDraft) ? getEntityDraft(entityId) : allEntities.retrieveById(entityId);

        assertEntityExists(entity);

        // the returned collection is unmodifiable
        List<Field> fields = new ArrayList<>(entity.getFields());

        // for data browser purposes, we sort the fields by their ui display order
        if (!forDraft) {
            Collections.sort(fields, new Comparator<Field>() {
                @Override
                public int compare(Field o1, Field o2) {
                    // check if one is displayable and the other isn't
                    if (o1.isUIDisplayable() && !o2.isUIDisplayable()) {
                        return -1;
                    } else if (!o1.isUIDisplayable() && o2.isUIDisplayable()) {
                        return 1;
                    }

                    // compare positions
                    Long position1 = o1.getUIDisplayPosition();
                    Long position2 = o2.getUIDisplayPosition();

                    if (position1 == null) {
                        return -1;
                    } else if (position2 == null) {
                        return 1;
                    } else {
                        return (position1 > position2) ? 1 : -1;
                    }
                }
            });
        }

        List<FieldDto> fieldDtos = new ArrayList<>();
        for (Field field : fields) {
            fieldDtos.add(field.toDto());
        }

        return addNonPersistentFieldsData(fieldDtos, entity);
    }

    @Override
    @Transactional
    public FieldDto findFieldByName(Long entityId, String name) {
        Entity entity = getEntityDraft(entityId);

        Field field = entity.getField(name);

        if (field == null) {
            throw new FieldNotFoundException();
        }

        return field.toDto();
    }

    @Override
    @Transactional
    public FieldDto findEntityFieldByName(Long entityId, String name) {
        Entity entity = allEntities.retrieveById(entityId);
        Field field = entity.getField(name);

        if (field == null) {
            throw new FieldNotFoundException();
        }

        return field.toDto();
    }

    @Override
    @Transactional
    public EntityDto getEntityForEdit(Long entityId) {
        Entity draft = getEntityDraft(entityId);
        return draft.toDto();
    }

    @Override
    @Transactional
    public EntityDraft getEntityDraft(Long entityId) {
        return getEntityDraft(entityId, getUsername());
    }

    @Override
    @Transactional
    public EntityDraft getEntityDraft(Long entityId, String username) {
        Entity entity = allEntities.retrieveById(entityId);

        assertEntityExists(entity);

        if (entity instanceof EntityDraft) {
            return (EntityDraft) entity;
        }

        if (username == null) {
            throw new AccessDeniedException("Cannot save draft - no user");
        }

        // get the draft
        EntityDraft draft = allEntityDrafts.retrieve(entity, username);

        if (draft == null) {
            draft = allEntityDrafts.create(entity, username);
        }

        return draft;
    }

    @Override
    @Transactional
    public void addFields(EntityDto entityDto, Collection<FieldDto> fields) {
        Entity entity = allEntities.retrieveById(entityDto.getId());

        assertEntityExists(entity);

        removeFields(entity, fields);

        for (FieldDto fieldDto : fields) {
            Field existing = entity.getField(fieldDto.getBasic().getName());

            if (null != existing) {
                existing.update(fieldDto);
            } else {
                addField(entity, fieldDto);
            }
        }
    }

    private void removeFields(Entity entity, Collection<FieldDto> fields) {
        Iterator<Field> iterator = entity.getFields().iterator();

        while (iterator.hasNext()) {
            Field field = iterator.next();

            // don't remove user defined fields
            if (!field.isReadOnly() || field.getMetadata(AUTO_GENERATED) != null ||
                    field.getMetadata(AUTO_GENERATED_EDITABLE) != null) {
                continue;
            }

            boolean found = false;

            for (FieldDto fieldDto : fields) {
                if (field.getName().equalsIgnoreCase(fieldDto.getBasic().getName())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                iterator.remove();
            }
        }
    }

    private void addField(Entity entity, FieldDto fieldDto) {
        FieldBasicDto basic = fieldDto.getBasic();
        String typeClass = fieldDto.getType().getTypeClass();

        Type type = allTypes.retrieveByClassName(typeClass);
        Field field = new Field(
                entity, basic.getDisplayName(), basic.getName(), basic.isRequired(), fieldDto.isReadOnly(),
                (String) basic.getDefaultValue(), basic.getTooltip(), null
        );
        field.setType(type);

        if (type.hasSettings()) {
            for (TypeSetting setting : type.getSettings()) {
                SettingDto settingDto = fieldDto.getSetting(setting.getName());
                FieldSetting fieldSetting = new FieldSetting(field, setting);

                if (null != settingDto) {
                    fieldSetting.setValue(settingDto.getValueAsString());
                }

                field.addSetting(fieldSetting);
            }
        }

        if (type.hasValidation()) {
            for (TypeValidation validation : type.getValidations()) {
                FieldValidation fieldValidation = new FieldValidation(field, validation);

                FieldValidationDto validationDto = fieldDto.getValidation();
                if (null != validationDto) {
                    ValidationCriterionDto criterion = validationDto
                            .getCriterion(validation.getDisplayName());

                    if (null != criterion) {
                        fieldValidation.setValue(criterion.valueAsString());
                        fieldValidation.setEnabled(criterion.isEnabled());
                    }
                }

                field.addValidation(fieldValidation);
            }
        }

        for (MetadataDto metadata : fieldDto.getMetadata()) {
            field.addMetadata(new FieldMetadata(metadata));
        }

        entity.addField(field);
    }

    @Override
    @Transactional
    public void addFilterableFields(EntityDto entityDto, Collection<String> fieldNames) {
        Entity entity = allEntities.retrieveById(entityDto.getId());

        assertEntityExists(entity);

        for (Field field : entity.getFields()) {
            boolean isUIFilterable = fieldNames.contains(field.getName());
            field.setUIFilterable(isUIFilterable);
        }
    }

    @Override
    @Transactional
    public EntityDto updateDraft(Long entityId) {
        Entity entity = allEntities.retrieveById(entityId);
        EntityDraft draft = getEntityDraft(entityId);

        allEntityDrafts.setProperties(draft, entity);

        return draft.toDto();
    }

    @Override
    @Transactional
    public LookupDto getLookupByName(Long entityId, String lookupName) {
        Entity entity = allEntities.retrieveById(entityId);
        assertEntityExists(entity);

        Lookup lookup = entity.getLookupByName(lookupName);
        return (lookup == null) ? null : lookup.toDto();
    }

    @Override
    @Transactional
    public List<FieldDto> getDisplayFields(Long entityId) {
        Entity entity = allEntities.retrieveById(entityId);
        assertEntityExists(entity);

        List<FieldDto> displayFields = new ArrayList<>();
        for (Field field : entity.getFields()) {
            if (field.isUIDisplayable()) {
                displayFields.add(field.toDto());
            }
        }

        return displayFields;
    }

    @Override
    @Transactional
    public void addDisplayedFields(EntityDto entityDto, Map<String, Long> positions) {
        Entity entity = allEntities.retrieveById(entityDto.getId());

        assertEntityExists(entity);

        List<Field> fields = entity.getFields();


        if (MapUtils.isEmpty(positions)) {
            // all fields will be added

            for (long i = 0; i < fields.size(); ++i) {
                Field field = fields.get((int) i);
                // user fields and auto generated fields are ignored
                if (isFieldFromDde(field)) {
                    field.setUIDisplayable(true);
                    field.setUIDisplayPosition(i);
                }
            }
        } else {
            // only fields in map should be added

            for (Field field : fields) {
                String fieldName = field.getName();

                boolean isUIDisplayable = positions.containsKey(fieldName);
                Long uiDisplayPosition = positions.get(fieldName);

                field.setUIDisplayable(isUIDisplayable);
                field.setUIDisplayPosition(uiDisplayPosition);
            }
        }
    }

    private void assertEntityExists(Entity entity) {
        if (entity == null) {
            throw new EntityNotFoundException();
        }
    }

    private void assertWritableEntity(Entity entity) {
        assertEntityExists(entity);

        if (entity.isDDE()) {
            throw new EntityReadOnlyException();
        }
    }

    private void addDefaultFields(Entity entity) {
        Type longType = allTypes.retrieveByClassName(Long.class.getName());
        Type stringType = allTypes.retrieveByClassName(String.class.getName());
        Type dateTimeType = allTypes.retrieveByClassName(DateTime.class.getName());

        Field idField = new Field(entity, Util.ID_FIELD_NAME, Util.ID_DISPLAY_FIELD_NAME, longType, true, true);
        idField.addMetadata(new FieldMetadata(idField, AUTO_GENERATED, TRUE));

        Field creatorField = new Field(entity, Util.CREATOR_FIELD_NAME, Util.CREATOR_DISPLAY_FIELD_NAME, stringType, true, true);
        creatorField.addMetadata(new FieldMetadata(creatorField, AUTO_GENERATED, TRUE));

        Field ownerField = new Field(entity, Util.OWNER_FIELD_NAME, Util.OWNER_DISPLAY_FIELD_NAME, stringType, false, true);
        ownerField.addMetadata(new FieldMetadata(ownerField, AUTO_GENERATED_EDITABLE, TRUE));

        Field modifiedByField = new Field(entity, Util.MODIFIED_BY_FIELD_NAME, Util.MODIFIED_BY_DISPLAY_FIELD_NAME, stringType, true, true);
        modifiedByField.addMetadata(new FieldMetadata(modifiedByField, AUTO_GENERATED, TRUE));

        Field modificationDateField = new Field(entity, Util.MODIFICATION_DATE_FIELD_NAME, Util.MODIFICATION_DATE_DISPLAY_FIELD_NAME, dateTimeType, true, true);
        modificationDateField.addMetadata(new FieldMetadata(modificationDateField, AUTO_GENERATED, TRUE));

        Field creationDateField = new Field(entity, Util.CREATION_DATE_FIELD_NAME, Util.CREATION_DATE_DISPLAY_FIELD_NAME, dateTimeType, true, true);
        creationDateField.addMetadata(new FieldMetadata(creationDateField, AUTO_GENERATED, TRUE));

        entity.addField(idField);
        entity.addField(creatorField);
        entity.addField(ownerField);
        entity.addField(modifiedByField);
        entity.addField(creationDateField);
        entity.addField(modificationDateField);
    }

    private boolean isFieldFromDde(Field field) {
        // only readonly fields are considered
        if (field.isReadOnly()) {
            // check metadata for auto generated
            for (String mdKey : Arrays.asList(AUTO_GENERATED, AUTO_GENERATED_EDITABLE)) {
                FieldMetadata metaData = field.getMetadata(mdKey);
                if (metaData != null && TRUE.equals(metaData.getValue())) {
                    return false;
                }
            }
            // readonly and no auto generated metadata
            return true;
        }
        // not readonly, defined by user
        return false;
    }

    private List<FieldDto> addNonPersistentFieldsData(List<FieldDto> fieldDtos, Entity entity) {
        List<LookupDto> lookupDtos = new ArrayList<>();
        for (FieldDto fieldDto : fieldDtos) {
            List<LookupDto> fieldLookups = fieldDto.getLookups();
            if (fieldLookups != null) {
                lookupDtos.addAll(fieldLookups);
            }
        }
        addLookupsReferences(lookupDtos, entity.getName());
        return fieldDtos;
    }

    private AdvancedSettingsDto addNonPersistentAdvancedSettingsData(AdvancedSettingsDto advancedSettingsDto, Entity entity) {
        addLookupsReferences(advancedSettingsDto.getIndexes(), entity.getName());
        return advancedSettingsDto;
    }

    private void addLookupsReferences(Collection<LookupDto> lookupDtos, String entityName) {
        MotechDataService dataSourceDataService = ServiceUtil.
                getServiceForInterfaceName(bundleContext, MotechClassPool.getInterfaceName(DATA_SOURCE_CLASS_NAME));
        if (dataSourceDataService != null) {
            for (LookupDto lookupDto : lookupDtos) {
                Long count = (Long) dataSourceDataService.executeQuery(createLookupReferenceQuery(lookupDto.getLookupName(), entityName));
                if (count > 0) {
                    lookupDto.setReferenced(true);
                }
            }
        }
    }

    @Autowired
    public void setAllEntities(AllEntities allEntities) {
        this.allEntities = allEntities;
    }

    @Autowired
    public void setAllTypes(AllTypes allTypes) {
        this.allTypes = allTypes;
    }

    @Autowired
    public void setAllEntityDrafts(AllEntityDrafts allEntityDrafts) {
        this.allEntityDrafts = allEntityDrafts;
    }

    @Autowired
    public void setAllEntityAudits(AllEntityAudits allEntityAudits) {
        this.allEntityAudits = allEntityAudits;
    }

    @Autowired
    public void setMDSConstructor(MDSConstructor mdsConstructor) {
        this.mdsConstructor = mdsConstructor;
    }

    @Autowired
    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Autowired
    public void setEntityValidator(EntityValidator entityValidator) {
        this.entityValidator = entityValidator;
    }
}
