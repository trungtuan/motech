package org.motechproject.mds.builder;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.motechproject.commons.date.model.Time;
import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.mds.builder.impl.EntityBuilderImpl;
import org.motechproject.mds.domain.ClassData;
import org.motechproject.mds.domain.Entity;
import org.motechproject.mds.domain.Field;
import org.motechproject.mds.domain.FieldMetadata;
import org.motechproject.mds.domain.OneToManyRelationship;
import org.motechproject.mds.domain.OneToOneRelationship;
import org.motechproject.mds.testutil.EntBuilderTestClass;
import org.motechproject.mds.testutil.RelatedClass;
import org.motechproject.mds.util.Constants;
import org.motechproject.mds.util.MDSClassLoader;
import org.osgi.framework.Bundle;
import org.springframework.test.util.ReflectionTestUtils;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Unique;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.WordUtils.uncapitalize;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.motechproject.mds.testutil.FieldTestHelper.field;
import static org.motechproject.mds.testutil.FieldTestHelper.newVal;
import static org.motechproject.mds.util.Constants.Util.MODIFICATION_DATE_FIELD_NAME;
import static org.motechproject.mds.util.Constants.Util.MODIFIED_BY_FIELD_NAME;

@RunWith(MockitoJUnitRunner.class)
public class EntityBuilderTest {

    private static final String ENTITY_NAME = "xx.yy.BuilderTest";

    private EntityBuilder entityBuilder = new EntityBuilderImpl();

    private MDSClassLoader mdsClassLoader;

    @Mock
    private Entity entity;

    @Mock
    private Bundle bundle;

    @Before
    public void setUp() {
        mdsClassLoader = MDSClassLoader.getStandaloneInstance(getClass().getClassLoader());
        when(entity.getClassName()).thenReturn(ENTITY_NAME);
    }

    @Test
    public void shouldBuildAnEntityWithFields() throws Exception {
        when(entity.getFields()).thenReturn(asList(field("count", Integer.class),
                field("time", Time.class), field("str", String.class), field("dec", Double.class),
                field("bool", Boolean.class), field("date", Date.class), field("dt", DateTime.class),
                field("ld", LocalDate.class), field("locale", Locale.class),
                field(MODIFICATION_DATE_FIELD_NAME, DateTime.class), field(MODIFIED_BY_FIELD_NAME, String.class)));

        Class<?> clazz = buildClass();

        assertNotNull(clazz);
        assertField(clazz, "count", Integer.class);
        assertField(clazz, "time", Time.class);
        assertField(clazz, "str", String.class);
        assertField(clazz, "dec", Double.class);
        assertField(clazz, "bool", Boolean.class);
        assertField(clazz, "date", Date.class);
        assertField(clazz, "dt", DateTime.class);
        assertField(clazz, "ld", LocalDate.class);
        assertField(clazz, "locale", Locale.class);

        assertMethod(clazz, "updateModificationData", Modifier.PRIVATE, Void.TYPE);
    }

    @Test
    public void shouldBuildAnEntityWithFieldsWithDefaultValues() throws Exception {
        final Date date = new Date();
        final DateTime dateTime = DateUtil.now();
        final LocalDate localDate = DateUtil.now().plusYears(1).toLocalDate();

        when(entity.getFields()).thenReturn(asList(field("count", Integer.class, 1),
                field("time", Time.class, new Time(10, 10)), field("str", String.class, "defStr"),
                field("dec", Double.class, 3.1), field("bool", Boolean.class, true),
                field("date", Date.class, date), field("dt", DateTime.class, dateTime),
                field("ld", LocalDate.class, localDate), field("locale", Locale.class, Locale.CANADA_FRENCH),
                field(MODIFICATION_DATE_FIELD_NAME, DateTime.class), field(MODIFIED_BY_FIELD_NAME, String.class)));

        Class<?> clazz = buildClass();

        assertNotNull(clazz);
        assertField(clazz, "count", Integer.class, 1);
        assertField(clazz, "time", Time.class, new Time(10, 10));
        assertField(clazz, "str", String.class, "defStr");
        assertField(clazz, "dec", Double.class, 3.1);
        assertField(clazz, "bool", Boolean.class, true);
        assertField(clazz, "date", Date.class, date);
        assertField(clazz, "dt", DateTime.class, dateTime);
        assertField(clazz, "ld", LocalDate.class, localDate);
        assertField(clazz, "locale", Locale.class, Locale.CANADA_FRENCH);

        assertMethod(clazz, "updateModificationData", Modifier.PRIVATE, Void.TYPE);
    }

    @Test
    public void shouldEditClasses() throws Exception {
        when(entity.getFields()).thenReturn(asList(field("name", Integer.class), field(MODIFICATION_DATE_FIELD_NAME, DateTime.class), field(MODIFIED_BY_FIELD_NAME, String.class)));

        Class<?> clazz = buildClass();
        assertField(clazz, "name", Integer.class);

        when(entity.getFields()).thenReturn(asList(field("name2", String.class), field(MODIFICATION_DATE_FIELD_NAME, DateTime.class), field(MODIFIED_BY_FIELD_NAME, String.class)));

        // reload the classloader for class edit
        mdsClassLoader = MDSClassLoader.getStandaloneInstance(getClass().getClassLoader());

        clazz = buildClass();
        assertField(clazz, "name2", String.class);

        // assert that the first field no longer exists
        try {
            clazz.getDeclaredField("name");
            fail("Field 'name' was preserved in the class, although it was removed from the entity");
        } catch (NoSuchFieldException e) {
            // expected
        }
    }

    @Test
    public void shouldBuildHistoryClass() throws Exception {
        when(entity.getFields()).thenReturn(asList(field("id", Long.class),
                field("count", Integer.class), field("time", Time.class),
                field("str", String.class), field("dec", Double.class),
                field("bool", Boolean.class), field("date", Date.class),
                field("dt", DateTime.class), field("list", List.class)));

        when(entity.getField("id")).thenReturn(field("id", Long.class));

        ClassData classData = entityBuilder.buildHistory(entity);
        assertEquals("xx.yy.history.BuilderTest__History", classData.getClassName());

        Class<?> clazz = mdsClassLoader.safeDefineClass(classData.getClassName(), classData.getBytecode());

        assertNotNull(clazz);
        assertField(clazz, clazz.getSimpleName() + "CurrentVersion", Long.class);
        assertField(clazz, clazz.getSimpleName() + "IsLast", Boolean.class);
        assertField(clazz, "id", Long.class);
        assertField(clazz, "count", Integer.class);
        assertField(clazz, "time", Time.class);
        assertField(clazz, "str", String.class);
        assertField(clazz, "dec", Double.class);
        assertField(clazz, "bool", Boolean.class);
        assertField(clazz, "date", Date.class);
        assertField(clazz, "dt", DateTime.class);
        assertField(clazz, "list", List.class);
    }

    @Test
    public void shouldBuildEnhancedDDE() throws Exception {
        Field strField = field("testStr", String.class);
        Field boolField = field("testBool", Boolean.class);
        strField.setReadOnly(true);
        boolField.setReadOnly(true);

        when(entity.getFields()).thenReturn(asList(
                strField, boolField, field("fromUser", DateTime.class),
                field(MODIFICATION_DATE_FIELD_NAME, DateTime.class),
                field(MODIFIED_BY_FIELD_NAME, String.class)));
        when(entity.getClassName()).thenReturn(EntBuilderTestClass.class.getName());

        ClassData classData = entityBuilder.buildDDE(entity, bundle);
        Class<?> builtClass = MDSClassLoader.getStandaloneInstance()
                .defineClass(classData.getClassName(), classData.getBytecode());

        assertField(builtClass, "testStr", String.class, "defValForTestStr");
        assertField(builtClass, "testBool", boolean.class, false, "is");
        assertField(builtClass, "fromUser", DateTime.class);

        // check annotations
        assertNotNull(builtClass.getAnnotation(PersistenceCapable.class));
        java.lang.reflect.Field field = builtClass.getDeclaredField("testStr");
        assertNotNull(field.getAnnotation(Unique.class));
    }

    @Test
    public void shouldBuildRelationshipFields() throws Exception {
        Field oneToOneField = field("oto", OneToOneRelationship.class);
        oneToOneField.setReadOnly(true);
        oneToOneField.addMetadata(new FieldMetadata(oneToOneField,
        Constants.MetadataKeys.RELATED_CLASS, RelatedClass.class.getName()));
        Field oneToManyField = field("otm", OneToManyRelationship.class);
        oneToManyField.setReadOnly(true);
        oneToManyField.addMetadata(new FieldMetadata(oneToManyField,
        Constants.MetadataKeys.RELATED_CLASS, RelatedClass.class.getName()));

        when(entity.getFields()).thenReturn(asList(oneToOneField, oneToManyField,
                field(MODIFICATION_DATE_FIELD_NAME, DateTime.class),
                field(MODIFIED_BY_FIELD_NAME, String.class)));
        when(entity.getClassName()).thenReturn(EntBuilderTestClass.class.getName());

        ClassData classData = entityBuilder.buildDDE(entity, bundle);
        Class<?> builtClass = MDSClassLoader.getStandaloneInstance()
                .defineClass(classData.getClassName(), classData.getBytecode());

        assertField(builtClass, "oto", RelatedClass.class);
        assertField(builtClass, "otm", List.class);
        assertGenericType(builtClass, "otm", List.class, RelatedClass.class);
    }

    private Class<?> buildClass() {
        ClassData classData = entityBuilder.build(entity);

        assertEquals(ENTITY_NAME, classData.getClassName());

        return mdsClassLoader.safeDefineClass(classData.getClassName(), classData.getBytecode());
    }

    private void assertField(Class<?> clazz, String name, Class<?> fieldType) throws Exception {
        assertField(clazz, name, fieldType, null);
    }

    private void assertField(Class<?> clazz, String name, Class<?> fieldType, Object expectedDefaultVal) throws Exception {
        assertField(clazz, name, fieldType, expectedDefaultVal, "get");
    }

    private void assertField(Class<?> clazz, String name, Class<?> fieldType, Object expectedDefaultVal, String getterPrefix)
            throws Exception {
        String uncapitalizeName = uncapitalize(name);
        java.lang.reflect.Field field = clazz.getDeclaredField(uncapitalizeName);

        // make sure this does not fail
        field.getGenericType();

        assertNotNull(field);
        assertEquals(Modifier.PRIVATE, field.getModifiers());
        assertEquals(fieldType, field.getType());

        Object instance = clazz.newInstance();
        Object val = ReflectionTestUtils.getField(instance, uncapitalizeName);
        assertEquals(expectedDefaultVal, val);

        // assert getters and setters

        Method getter = clazz.getMethod(getterPrefix + StringUtils.capitalize(uncapitalizeName));
        assertEquals(fieldType, getter.getReturnType());
        assertEquals(Modifier.PUBLIC, getter.getModifiers());

        Method setter = clazz.getMethod("set" + StringUtils.capitalize(uncapitalizeName), fieldType);
        assertEquals(Void.TYPE, setter.getReturnType());
        assertEquals(Modifier.PUBLIC, setter.getModifiers());

        // getter returns default value
        assertEquals(expectedDefaultVal, getter.invoke(instance));

        // set then get
        Object newVal = newVal(fieldType);
        setter.invoke(instance, newVal);

        assertEquals(newVal, getter.invoke(instance));
    }

    private void assertMethod(Class<?> clazz, String name, int modifiers, Class<?> returnType, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(name, parameterTypes);

        assertNotNull(method);
        assertEquals(modifiers, method.getModifiers());
        assertEquals(returnType, method.getReturnType());
        assertArrayEquals(parameterTypes, method.getParameterTypes());
    }

    public void assertGenericType(Class<?> clazz, String fieldName,
                                  Class<?> fieldClass, Class<?> genericTypeClass) throws Exception {
        java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
        assertNotNull(field);

        assertEquals(fieldClass, field.getType());
        assertEquals(genericTypeClass, ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]);
    }
}
