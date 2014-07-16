package org.motechproject.mds.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.motechproject.commons.api.Range;
import org.motechproject.commons.date.model.Time;
import org.motechproject.commons.date.util.DateUtil;

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TypeHelperTest {

    @Test
    public void shouldParseStrings() {
        final DateTime dt = DateUtil.now();
        final DateTime dt2 = new DateTime(2009, 6, 7, 12, 11, 0, 0, DateTimeZone.forOffsetHours(1));
        final LocalDate ld = DateUtil.now().toLocalDate();
        final LocalDate ld2 = new LocalDate(2000, 8, 22);

        assertEquals(3, TypeHelper.parse(3, Integer.class));

        assertEquals("test", TypeHelper.parse("test", String.class));

        assertEquals(new Time(16, 4), TypeHelper.parse("16:04", Time.class));

        assertEquals(dt, TypeHelper.parse(dt.toString(), DateTime.class));
        assertEquals(dt.toDate(), TypeHelper.parse(dt.toString(), Date.class));
        assertEquals(DateUtil.setTimeZoneUTC(dt2),
                DateUtil.setTimeZoneUTC((DateTime) TypeHelper.parse("2009-06-07 12:11 +01:00", DateTime.class)));
        assertEquals(dt2.toDate(), TypeHelper.parse("2009-06-07 12:11 +01:00", Date.class));

        assertEquals(asList("one", "2", "three"), TypeHelper.parse("one\n2\nthree", List.class));

        assertEquals(true, TypeHelper.parse("true", Boolean.class));

        assertEquals(ld, TypeHelper.parse(ld, LocalDate.class));
        assertEquals(ld, TypeHelper.parse(ld.toString(), LocalDate.class));
        assertEquals(ld2, TypeHelper.parse("2000-08-22", LocalDate.class));
        // TODO: do not send such values from the UI
        assertEquals(ld2, TypeHelper.parse("2000-08-22T23:00:00.000Z", LocalDate.class));
    }

    @Test
    public void shouldReturnCorrectInstances() {
        final DateTime dt = DateUtil.now();
        assertEquals(new Time(10, 10), TypeHelper.parse(new Time(10, 10), Time.class));
        assertEquals(dt, TypeHelper.parse(dt, DateTime.class));
        assertEquals(dt.toDate(), TypeHelper.parse(dt.toDate(), Date.class));
        assertEquals(11, TypeHelper.parse(11, Integer.class));
        assertEquals(asList(1, 2), TypeHelper.parse(asList(1, 2), List.class));
    }

    @Test
    public void shouldParseIntToBool() {
        assertEquals(true, TypeHelper.parse(1, Boolean.class));
        assertEquals(true, TypeHelper.parse(200, Boolean.class));
        assertEquals(false, TypeHelper.parse(0, Boolean.class));
        assertEquals(false, TypeHelper.parse(-1, Boolean.class));
        assertEquals(null, TypeHelper.parse(null, Boolean.class));
    }

    @Test
    public void shouldIdentifyTypesWithPrimitives() {
        assertTrue(TypeHelper.hasPrimitive(Boolean.class));
        assertTrue(TypeHelper.hasPrimitive(Integer.class));
        assertTrue(TypeHelper.hasPrimitive(Long.class));
        assertTrue(TypeHelper.hasPrimitive(Short.class));
        assertTrue(TypeHelper.hasPrimitive(Byte.class));
        assertTrue(TypeHelper.hasPrimitive(Double.class));
        assertTrue(TypeHelper.hasPrimitive(Float.class));
        assertTrue(TypeHelper.hasPrimitive(Character.class));

        assertFalse(TypeHelper.hasPrimitive(String.class));
        assertFalse(TypeHelper.hasPrimitive(Date.class));
        assertFalse(TypeHelper.hasPrimitive(Time.class));
    }


    @Test
    public void shouldReturnCorrectWrappersAndPrimitives() {
        assertEquals(Boolean.class, TypeHelper.getWrapperForPrimitive(boolean.class));
        assertEquals(Character.class, TypeHelper.getWrapperForPrimitive(char.class));
        assertEquals(Byte.class, TypeHelper.getWrapperForPrimitive(byte.class));
        assertEquals(Integer.class, TypeHelper.getWrapperForPrimitive(int.class));
        assertEquals(Long.class, TypeHelper.getWrapperForPrimitive(long.class));
        assertEquals(Short.class, TypeHelper.getWrapperForPrimitive(short.class));
        assertEquals(Double.class, TypeHelper.getWrapperForPrimitive(double.class));
        assertEquals(Float.class, TypeHelper.getWrapperForPrimitive(float.class));

        assertEquals(boolean.class, TypeHelper.getPrimitive(Boolean.class));
        assertEquals(char.class, TypeHelper.getPrimitive(Character.class));
        assertEquals(byte.class, TypeHelper.getPrimitive(Byte.class));
        assertEquals(int.class, TypeHelper.getPrimitive(Integer.class));
        assertEquals(long.class, TypeHelper.getPrimitive(Long.class));
        assertEquals(short.class, TypeHelper.getPrimitive(Short.class));
        assertEquals(double.class, TypeHelper.getPrimitive(Double.class));
        assertEquals(float.class, TypeHelper.getPrimitive(Float.class));
    }

    @Test
    public void shouldBuildRanges() {
        DateTime now = DateUtil.now();
        Range dtRange = new Range<>(now, now.plusHours(1));

        assertEquals(new Range<>(1, 5), TypeHelper.toRange(new Range<>(1, 5), Integer.class.getName()));
        assertEquals(dtRange, TypeHelper.toRange(new Range<>(now, now.plusHours(1)), DateTime.class.getName()));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("min", now);
        map.put("max", now.plusHours(1));

        assertEquals(dtRange, TypeHelper.toRange(map, DateTime.class.getName()));

        map.put("min", now.toString());
        map.put("max", now.plusHours(1).toString());

        assertEquals(dtRange, TypeHelper.toRange(map, DateTime.class.getName()));
    }

    @Test
    public void shouldBuildSets() {
        Set<String> expectedSet = new HashSet<>(asList("one", "nine", "three"));

        assertEquals(expectedSet, TypeHelper.toSet(expectedSet, String.class.getName()));

        List<String> regularCollection = asList("one", "nine", "three");
        assertEquals(expectedSet, TypeHelper.toSet(regularCollection, String.class.getName()));

        List<Map<String, String>> listOfMapsFromUI = asList(mapFromUI("one"), mapFromUI("nine"), mapFromUI("three"));
        assertEquals(expectedSet, TypeHelper.toSet(listOfMapsFromUI, String.class.getName()));
    }

    @Test
    public void shouldParseStringsToMaps() {
        String str = "{key1:value,key2:,key3:test}";

        Map map = TypeHelper.parseStringToMap(str);

        assertEquals("value", map.get("key1"));
        assertEquals("", map.get("key2"));
        assertEquals("test", map.get("key3"));
    }

    private Map<String, String> mapFromUI(String value) {
        Map<String, String> mapFromUI = new LinkedHashMap<>();
        mapFromUI.put("val", value);
        return mapFromUI;
    }
}
