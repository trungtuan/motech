package org.motechproject.mds.annotations.internal;

import com.thoughtworks.paranamer.Paranamer;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.motechproject.commons.api.Range;
import org.motechproject.commons.date.model.Time;
import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.dto.AdvancedSettingsDto;
import org.motechproject.mds.dto.EntityDto;
import org.motechproject.mds.dto.FieldDto;
import org.motechproject.mds.dto.LookupDto;
import org.motechproject.mds.dto.LookupFieldDto;
import org.motechproject.mds.dto.TypeDto;
import org.motechproject.mds.ex.LookupWrongParameterTypeException;
import org.motechproject.mds.service.EntityService;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.motechproject.mds.dto.LookupFieldDto.Type.RANGE;
import static org.motechproject.mds.dto.LookupFieldDto.Type.SET;
import static org.motechproject.mds.dto.LookupFieldDto.Type.VALUE;
import static org.motechproject.mds.testutil.FieldTestHelper.lookupFieldDto;
import static org.motechproject.mds.testutil.FieldTestHelper.lookupFieldDtos;

public class LookupProcessorTest {

    @InjectMocks
    LookupProcessor lookupProcessor;

    @Mock
    EntityService entityService;

    @Mock
    Reflections reflections;

    @Mock
    Paranamer paranamer;

    private String[] argNames = {"arg0", "arg1", "arg2"};

    @Before
    public void setUp() throws NoSuchMethodException {
        lookupProcessor = new LookupProcessor();
        initMocks(this);

        when(entityService.getEntityByClassName(String.class.getName())).thenReturn(getTestEntity());
        when(entityService.getEntityByClassName(TestClass.class.getName())).thenReturn(getTestEntity());
        when(entityService.getEntityByClassName(Integer.class.getName())).thenReturn(null);
        when(entityService.getAdvancedSettings(getTestEntity().getId(), true)).thenReturn(getAdvancedSettings());
    }

    @Test
    public void shouldProcessMethodWithLookupFields() throws NoSuchMethodException {
        FieldDto arg1Field = mock(FieldDto.class);
        FieldDto secondArgumentField = mock(FieldDto.class);

        when(arg1Field.getType()).thenReturn(TypeDto.INTEGER);
        when(secondArgumentField.getType()).thenReturn(TypeDto.STRING);
        when(paranamer.lookupParameterNames(getTestMethod(1))).thenReturn(argNames);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "arg1")).thenReturn(arg1Field);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "secondArgument")).thenReturn(secondArgumentField);

        Method method = getTestMethod(1);
        LookupDto dto = new LookupDto("Test Method 1", true, false,
                asList(lookupFieldDto("arg1"), lookupFieldDto("secondArgument", "LIKE")), true, "testMethod1");

        lookupProcessor.process(method);

        verify(entityService).getEntityByClassName(String.class.getName());

        Map<Long, List<LookupDto>> elements = lookupProcessor.getElements();
        assertTrue(elements.containsKey(getTestEntity().getId()));

        List<LookupDto> list = elements.get(getTestEntity().getId());
        assertEquals(1, list.size());
        assertEquals(dto, list.get(0));
    }

    @Test (expected = LookupWrongParameterTypeException.class)
    public void shouldNotProcessMethodWithLookupFieldsWithWrongType() throws NoSuchMethodException {
        FieldDto arg1Field = mock(FieldDto.class);
        FieldDto secondArgumentField = mock(FieldDto.class);

        when(arg1Field.getType()).thenReturn(TypeDto.STRING);
        when(secondArgumentField.getType()).thenReturn(TypeDto.STRING);
        when(paranamer.lookupParameterNames(getTestMethod(1))).thenReturn(argNames);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "arg1")).thenReturn(arg1Field);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "secondArgument")).thenReturn(secondArgumentField);

        Method method = getTestMethod(1);

        lookupProcessor.process(method);
    }

    @Test
    public void shouldProcessMethodWithNotAnnotatedParameters() throws NoSuchMethodException {
        when(paranamer.lookupParameterNames(getTestMethod(2))).thenReturn(argNames);

        Method method = getTestMethod(2);
        LookupDto dto = new LookupDto("Test Method 2", false, false,
                lookupFieldDtos(argNames), true, "testMethod2");

        lookupProcessor.process(method);

        verify(entityService).getEntityByClassName(TestClass.class.getName());

        Map<Long, List<LookupDto>> elements = lookupProcessor.getElements();
        assertTrue(elements.containsKey(getTestEntity().getId()));

        List<LookupDto> list = elements.get(getTestEntity().getId());
        assertEquals(1, list.size());
        assertEquals(dto, list.get(0));
    }

    @Test
    public void shouldProcessMethodWithCustomLookupName() throws NoSuchMethodException {
        when(paranamer.lookupParameterNames(getTestMethod(3))).thenReturn(argNames);

        Method method = getTestMethod(3);
        LookupDto dto = new LookupDto("My new custom lookup", false, false,
                lookupFieldDtos(argNames), true, "testMethod3");

        lookupProcessor.process(method);

        verify(entityService).getEntityByClassName(TestClass.class.getName());

        Map<Long, List<LookupDto>> elements = lookupProcessor.getElements();
        assertTrue(elements.containsKey(getTestEntity().getId()));

        List<LookupDto> list = elements.get(getTestEntity().getId());
        assertEquals(1, list.size());
        assertEquals(dto, list.get(0));
    }

    @Test
    public void shouldProcessMethodWithRangeParam() throws NoSuchMethodException {
        FieldDto arg0Field = mock(FieldDto.class);
        FieldDto rangeField = mock(FieldDto.class);
        FieldDto regularFieldField = mock(FieldDto.class);
        FieldDto rangeFieldField = mock(FieldDto.class);

        when(arg0Field.getType()).thenReturn(TypeDto.BOOLEAN);
        when(rangeField.getType()).thenReturn(TypeDto.STRING);
        when(regularFieldField.getType()).thenReturn(TypeDto.BOOLEAN);
        when(rangeFieldField.getType()).thenReturn(TypeDto.DOUBLE);

        LookupFieldDto[][] expectedFields = {{lookupFieldDto("arg0"), lookupFieldDto("range", RANGE)},
                {lookupFieldDto("regularField"), lookupFieldDto("rangeField", RANGE)}};

        when(entityService.findEntityFieldByName(getTestEntity().getId(), "arg0")).thenReturn(arg0Field);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "range")).thenReturn(rangeField);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "regularField")).thenReturn(regularFieldField);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "rangeField")).thenReturn(rangeFieldField);

        // test two methods, one with @LookupField annotations, second without
        for (int i = 0; i < 2; i++) {
            Method method = getTestMethodWithRangeParam(i);

            when(paranamer.lookupParameterNames(method)).thenReturn(new String[]{"arg0", "range"});

            LookupDto expectedLookup = new LookupDto("Test Method With Range Param " + i, false, false,
                    asList(expectedFields[i]), true, "testMethodWithRangeParam" + i);

            lookupProcessor.process(method);

            verify(entityService, times(i + 1)).getEntityByClassName(TestClass.class.getName());

            Map<Long, List<LookupDto>> elements = lookupProcessor.getElements();
            assertTrue(elements.containsKey(getTestEntity().getId()));

            List<LookupDto> list = elements.get(getTestEntity().getId());
            assertEquals(1, list.size());
            assertEquals(expectedLookup, list.get(0));

            assertEquals(asList(VALUE, RANGE), extract(list.get(0).getLookupFields(), on(LookupFieldDto.class).getType()));

            lookupProcessor.clear();
        }
    }

    @Test
    public void shouldProcessMethodWithSetParam() throws NoSuchMethodException {
        FieldDto arg0Field = mock(FieldDto.class);
        FieldDto setField = mock(FieldDto.class);
        FieldDto regularFieldField = mock(FieldDto.class);
        FieldDto setFieldField = mock(FieldDto.class);

        when(arg0Field.getType()).thenReturn(TypeDto.STRING);
        when(setField.getType()).thenReturn(TypeDto.STRING);
        when(regularFieldField.getType()).thenReturn(TypeDto.STRING);
        when(setFieldField.getType()).thenReturn(TypeDto.DOUBLE);

        LookupFieldDto[][] expectedFields = {{lookupFieldDto("arg0"), lookupFieldDto("set", SET)},
                {lookupFieldDto("regularField"), lookupFieldDto("setField", SET)}};

        when(entityService.findEntityFieldByName(getTestEntity().getId(), "arg0")).thenReturn(arg0Field);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "set")).thenReturn(setField);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "regularField")).thenReturn(regularFieldField);
        when(entityService.findEntityFieldByName(getTestEntity().getId(), "setField")).thenReturn(setFieldField);

        // test two methods, one with @LookupField annotations, second without
        for (int i = 0; i < 2; i++) {
            Method method = getTestMethodWithSetParam(i);

            when(paranamer.lookupParameterNames(method)).thenReturn(new String[]{"arg0", "set"});

            LookupDto expectedLookup = new LookupDto("Test Method With Set Param " + i, true, false,
                    asList(expectedFields[i]), true, "testMethodWithSetParam" + i);

            lookupProcessor.process(method);

            verify(entityService, times(i + 1)).getEntityByClassName(TestClass.class.getName());

            Map<Long, List<LookupDto>> elements = lookupProcessor.getElements();
            assertTrue(elements.containsKey(getTestEntity().getId()));

            List<LookupDto> list = elements.get(getTestEntity().getId());
            assertEquals(1, list.size());
            assertEquals(expectedLookup, list.get(0));

            assertEquals(asList(VALUE, SET), extract(list.get(0).getLookupFields(), on(LookupFieldDto.class).getType()));

            lookupProcessor.clear();
        }
    }

    @Test
    public void shouldBreakProcessingWhenEntityNotFound() throws NoSuchMethodException {
        when(paranamer.lookupParameterNames(getTestMethod(4))).thenReturn(argNames);

        Method method = getTestMethod(4);

        lookupProcessor.process(method);

        verify(entityService).getEntityByClassName(Integer.class.getName());
        verify(entityService, never()).getAdvancedSettings(anyLong(), eq(true));

        assertTrue(lookupProcessor.getElements().isEmpty());
    }

    @Test
    public void shouldReturnCorrectAnnotation() {
        assertEquals(Lookup.class, lookupProcessor.getAnnotationType());
    }

    private Method getTestMethod(int number) throws NoSuchMethodException {
        return TestClass.class.getMethod("testMethod" + number, String.class, Integer.class, String.class);
    }

    private Method getTestMethodWithRangeParam(int number) throws NoSuchMethodException {
        return TestClass.class.getMethod("testMethodWithRangeParam" + number, Boolean.class, Range.class);
    }

    private Method getTestMethodWithSetParam(int number) throws NoSuchMethodException {
        return TestClass.class.getMethod("testMethodWithSetParam" + number, String.class, Set.class);
    }

    private EntityDto getTestEntity() {
        EntityDto testEntity = new EntityDto();
        testEntity.setId(1L);
        return testEntity;
    }

    private List<LookupDto> getLookupList() {
        LookupDto lookup1 = new LookupDto();
        lookup1.setLookupName("Lookup 1");
        LookupDto lookup2 = new LookupDto();
        lookup2.setLookupName("Lookup 2");
        return asList(lookup1, lookup2);
    }

    private AdvancedSettingsDto getAdvancedSettings() {
        AdvancedSettingsDto settings = new AdvancedSettingsDto();
        settings.setIndexes(getLookupList());
        return settings;
    }

    private class TestClass {

        @Lookup
        public String testMethod1(String arg0, @LookupField Integer arg1,
                                  @LookupField(name = "secondArgument", customOperator = "LIKE") String arg2) {
            return "testString";
        }

        @Lookup
        public List<TestClass> testMethod2(String arg0, Integer arg1, String arg2) {
            return new ArrayList<>();
        }

        @Lookup(name = "My new custom lookup")
        public List<TestClass> testMethod3(String arg0, Integer arg1, String arg2) {
            return new ArrayList<>();
        }

        @Lookup
        public Integer testMethod4(String arg0, Integer arg1, String arg2) {
            return 42;
        }

        @Lookup
        public List<TestClass> testMethodWithRangeParam0(Boolean arg0, Range<DateTime> range) {
            return Collections.emptyList();
        }

        @Lookup
        public List<TestClass> testMethodWithRangeParam1(@LookupField(name = "regularField") Boolean arg0,
                                                         @LookupField(name = "rangeField") Range<DateTime> range) {
            return Collections.emptyList();
        }

        @Lookup
        public TestClass testMethodWithSetParam0(String arg0, Set<Time> set) {
            return null;
        }

        @Lookup
        public TestClass testMethodWithSetParam1(@LookupField(name = "regularField") String arg0,
                                                 @LookupField(name = "setField") Set<Time> range) {
            return null;
        }
    }
}
