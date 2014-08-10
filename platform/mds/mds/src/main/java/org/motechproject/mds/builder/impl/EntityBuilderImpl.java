package org.motechproject.mds.builder.impl;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import org.motechproject.mds.builder.EntityBuilder;
import org.motechproject.mds.domain.ClassData;
import org.motechproject.mds.domain.ComboboxHolder;
import org.motechproject.mds.domain.Entity;
import org.motechproject.mds.domain.EntityType;
import org.motechproject.mds.domain.Field;
import org.motechproject.mds.domain.Relationship;
import org.motechproject.mds.domain.Type;
import org.motechproject.mds.ex.EntityCreationException;
import org.motechproject.mds.javassist.JavassistBuilder;
import org.motechproject.mds.javassist.JavassistHelper;
import org.motechproject.mds.javassist.MotechClassPool;
import org.motechproject.mds.util.ClassName;
import org.motechproject.mds.util.EnumHelper;
import org.motechproject.mds.util.TypeHelper;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.uncapitalize;

/**
 * The <code>EntityBuilderImpl</code> is used build classes for a given entity.
 * This implementation relies on Javassist to build the class definition.
 */
@Component
public class EntityBuilderImpl implements EntityBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(EntityBuilderImpl.class);
    private final ClassPool classPool = MotechClassPool.getDefault();

    @Override
    public ClassData build(Entity entity) {
        LOG.info("Building EUDE: " + entity.getName());
        return build(entity, EntityType.STANDARD, null);
    }

    @Override
    public ClassData buildDDE(Entity entity, Bundle bundle) {
        LOG.info("Building DDE: " + entity.getClassName());
        return build(entity, EntityType.STANDARD, bundle);
    }

    @Override
    public void prepareHistoryClass(Entity entity) {
        String className = entity.getClassName();
        LOG.info("Building empty history class for: {}", className);

        String historyClassName = ClassName.getHistoryClassName(className);
        CtClass historyClass = classPool.getOrNull(historyClassName);

        // we can edit classes
        if (historyClass != null) {
            historyClass.defrost();
        }

        //build empty history class
        classPool.makeClass(historyClassName);
    }

    @Override
    public void prepareTrashClass(Entity entity) {
        String className = entity.getClassName();
        LOG.info("Building empty trash class for: {}", className);

        String trashClassName = ClassName.getTrashClassName(className);
        CtClass trashClass = classPool.getOrNull(trashClassName);

        // we can edit classes
        if (trashClass != null) {
            trashClass.defrost();
        }

        classPool.makeClass(trashClassName);
    }

    @Override
    public ClassData buildHistory(Entity entity) {
        LOG.info("Building history class for: {}", entity.getClassName());
        return build(entity, EntityType.HISTORY, null);
    }

    @Override
    public ClassData buildTrash(Entity entity) {
        LOG.info("Building trash class for: {}", entity.getClassName());
        return build(entity, EntityType.TRASH, null);
    }

    private ClassData build(Entity entity, EntityType type, Bundle bundle) {
        try {
            CtClass declaring = makeClass(entity, type, bundle);

            switch (type) {
                case HISTORY:
                    String className = type.getName(entity.getClassName());
                    String simpleName = ClassName.getSimpleName(className);
                    Type idType = entity.getField("id").getType();

                    // add 4 extra fields to history class definition

                    // this field is related with id field in entity
                    addProperty(
                            declaring, idType.getTypeClassName(), simpleName + "CurrentVersion",
                            null
                    );

                    // this field is a flag that inform whether the instance with id (field above)
                    // is in trash or not.
                    addProperty(
                            declaring, Boolean.class.getName(), simpleName + "FromTrash", "false"
                    );

                    // this field is a flag informing whether this history record is a current
                    // revision of an instance
                    addProperty(
                            declaring, Boolean.class.getName(), simpleName + "IsLast", null
                    );

                    // this field contains information about the schema version of an entity
                    addProperty(
                            declaring, Long.class.getName(), simpleName + "SchemaVersion", null
                    );
                    break;
                case TRASH:
                    // this field contains information about the schema version of an entity
                    addProperty(declaring, Long.class.getName(), "schemaVersion", null);
                    break;
                default:
            }

            return new ClassData(
                    declaring.getName(), entity.getModule(), entity.getNamespace(),
                    declaring.toBytecode(), type
            );
        } catch (Exception e) {
            LOG.error("Error while building {} entity {}", new String[]{type.name(), entity.getName()});
            throw new EntityCreationException(e);
        }
    }

    private CtClass makeClass(Entity entity, EntityType type, Bundle bundle)
            throws NotFoundException, CannotCompileException, ReflectiveOperationException {
        // try to get declaring class
        CtClass declaring = getDeclaringClass(entity, type, bundle);

        // create properties (add fields, getters and setters)
        for (Field field : entity.getFields()) {
            try {
                String fieldName = field.getName();
                CtField ctField;

                if (!shouldLeaveExistingField(field, declaring)) {
                    JavassistHelper.removeFieldIfExists(declaring, fieldName);
                    ctField = createField(declaring, entity, field, type);

                    if (isBlank(field.getDefaultValue())) {
                        declaring.addField(ctField);
                    } else {
                        declaring.addField(ctField, createInitializer(entity, field));
                    }
                } else {
                    ctField = JavassistHelper.findField(declaring, fieldName);
                }

                String getter = JavassistBuilder.getGetterName(fieldName, declaring);
                String setter = JavassistBuilder.getSetterName(fieldName);

                if (!shouldLeaveExistingMethod(field, getter, declaring)) {
                    createGetter(declaring, fieldName, ctField);
                }

                if (!shouldLeaveExistingMethod(field, setter, declaring)) {
                    createSetter(declaring, fieldName, ctField);
                }
            } catch (Exception e) {
                LOG.error("Error while processing field {}", field.getName());
                throw e;
            }
        }

        return declaring;
    }

    private CtClass getDeclaringClass(Entity entity, EntityType type, Bundle bundle)
            throws NotFoundException {
        String className = type.getName(entity.getClassName());
        boolean isDDE = null != bundle;

        CtClass declaring = classPool.getOrNull(className);

        if (null != declaring) {
            // we can edit classes
            declaring.defrost();
        } else if (isDDE) {
            try {
                declaring = JavassistHelper.loadClass(bundle, entity.getClassName(), classPool);
            } catch (IOException e) {
                throw new NotFoundException(e.getMessage(), e);
            }
        }

        return isDDE ? declaring : classPool.makeClass(className);
    }

    private void addProperty(CtClass declaring, String typeClassName, String propertyName,
                             String defaultValue)
            throws CannotCompileException, NotFoundException {
        try {
            String name = uncapitalize(propertyName);
            JavassistHelper.removeFieldIfExists(declaring, propertyName);

            CtClass type = classPool.getOrNull(typeClassName);
            CtField field = JavassistBuilder.createField(declaring, type, propertyName, null);

            if (isBlank(defaultValue)) {
                declaring.addField(field);
            } else {
                CtField.Initializer initializer = JavassistBuilder.createInitializer(
                        typeClassName, defaultValue
                );
                declaring.addField(field, initializer);
            }

            createGetter(declaring, name, field);
            createSetter(declaring, name, field);
        } catch (Exception e) {
            LOG.error("Error while creating property {}", propertyName);
            throw e;
        }
    }

    private CtField createField(CtClass declaring, Entity entity, Field field,
                                EntityType entityType)
            throws IllegalAccessException, InstantiationException, CannotCompileException {
        Type fieldType = field.getType();
        String genericSignature = null;
        CtClass type = null;

        if (fieldType.isCombobox()) {
            ComboboxHolder holder = new ComboboxHolder(entity, field);

            if (holder.isEnum() || holder.isEnumList()) {
                type = classPool.getOrNull(holder.getEnumName());

                if (holder.isEnumList()) {
                    genericSignature = JavassistHelper.genericSignature(
                            List.class, holder.getEnumName()
                    );
                    type = classPool.getOrNull(List.class.getName());
                }
            } else if (holder.isStringList()) {
                genericSignature = JavassistHelper.genericSignature(List.class, String.class);
                type = classPool.getOrNull(List.class.getName());
            } else if (holder.isString()) {
                type = classPool.getOrNull(String.class.getName());
            }
        } else if (fieldType.isRelationship()) {
            Relationship relationshipType = (Relationship) fieldType.getTypeClass().newInstance();

            genericSignature = relationshipType.getGenericSignature(field, entityType);
            type = classPool.getOrNull(relationshipType.getFieldType(field, entityType));
        } else {
            type = classPool.getOrNull(fieldType.getTypeClassName());
        }

        return JavassistBuilder.createField(declaring, type, field.getName(), genericSignature);
    }

    private void createGetter(CtClass declaring, String fieldName, CtField ctField)
            throws CannotCompileException {
        CtMethod getter = JavassistBuilder.createGetter(fieldName, declaring, ctField);
        JavassistHelper.removeMethodIfExists(declaring, getter.getName());
        declaring.addMethod(getter);
    }

    private void createSetter(CtClass declaring, String fieldName, CtField field)
            throws CannotCompileException {
        CtMethod setter = JavassistBuilder.createSetter(fieldName, field);
        JavassistHelper.removeMethodIfExists(declaring, setter.getName());
        declaring.addMethod(setter);
    }

    private CtField.Initializer createInitializer(Entity entity, Field field) {
        Type type = field.getType();
        CtField.Initializer initializer = null;

        if (type.isCombobox()) {
            ComboboxHolder holder = new ComboboxHolder(entity, field);

            if (holder.isStringList()) {
                Object defaultValue = TypeHelper.parse(field.getDefaultValue(), List.class);
                initializer = JavassistBuilder.createListInitializer(
                        String.class.getName(), defaultValue
                );
            } else if (holder.isEnumList()) {
                Object defaultValue = TypeHelper.parse(field.getDefaultValue(), List.class);
                initializer = JavassistBuilder.createListInitializer(
                        holder.getEnumName(), EnumHelper.prefixEnumValues((List) defaultValue)
                );
            } else if (holder.isString()) {
                initializer = JavassistBuilder.createInitializer(
                        String.class.getName(), field.getDefaultValue()
                );
            } else if (holder.isEnum()) {
                initializer = JavassistBuilder.createEnumInitializer(
                        holder.getEnumName(), EnumHelper.prefixEnumValue(field.getDefaultValue())
                );
            }
        } else if (!type.isRelationship()) {
            initializer = JavassistBuilder.createInitializer(
                    type.getTypeClassName(), field.getDefaultValue()
            );
        }

        return initializer;
    }

    private boolean shouldLeaveExistingField(Field field, CtClass declaring) {
        return field.isReadOnly()
                && (JavassistHelper.containsField(declaring, field.getName()) ||
                    JavassistHelper.containsDeclaredField(declaring, field.getName()));
    }

    private boolean shouldLeaveExistingMethod(Field field, String methodName, CtClass declaring) {
        return field.isReadOnly()
                && (JavassistHelper.containsMethod(declaring, methodName) ||
                    JavassistHelper.containsDeclaredMethod(declaring, methodName));
    }
}
