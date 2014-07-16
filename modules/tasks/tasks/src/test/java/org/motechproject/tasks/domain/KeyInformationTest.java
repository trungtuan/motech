package org.motechproject.tasks.domain;

import org.joda.time.DateTime;
import org.junit.Test;
import org.motechproject.commons.api.MotechException;
import org.motechproject.commons.date.util.DateUtil;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.motechproject.tasks.domain.KeyInformation.ADDITIONAL_DATA_PREFIX;
import static org.motechproject.tasks.domain.KeyInformation.TRIGGER_PREFIX;

public class KeyInformationTest {
    private static final String KEY_VALUE = "key";
    private static final Long DATA_PROVIDER_ID = 12345L;
    private static final String OBJECT_TYPE = "Test";
    private static final Long OBJECT_ID = 1L;

    @Test
    public void shouldGetInformationFromTriggerKey() {
        String original = String.format("%s.%s", TRIGGER_PREFIX, KEY_VALUE);
        KeyInformation key = KeyInformation.parse(original);

        assertKeyFromTrigger(original, key);
    }

    @Test
    public void shouldGetInformationFromTriggerKeyWithManipulations() {
        String original = String.format("%s.%s?toupper?join(-)", TRIGGER_PREFIX, KEY_VALUE);
        KeyInformation key = KeyInformation.parse(original);

        assertKeyFromTrigger(original, key);
    }

    @Test
    public void shouldGetInformationFromAdditionalDataKey() {
        String original = String.format("%s.%s.%s#%d.%s", ADDITIONAL_DATA_PREFIX, DATA_PROVIDER_ID, OBJECT_TYPE, OBJECT_ID, KEY_VALUE);
        KeyInformation key = KeyInformation.parse(original);

        assertKeyFromAdditionalData(original, key);
    }

    @Test
    public void shouldGetInformationFromAdditionalDataKeyWithManipulations() {
        String original = String.format("%s.%s.%s#%d.%s?toupper?join(-)", ADDITIONAL_DATA_PREFIX, DATA_PROVIDER_ID, OBJECT_TYPE, OBJECT_ID, KEY_VALUE);
        KeyInformation key = KeyInformation.parse(original);

        assertKeyFromAdditionalData(original, key);
    }

    @Test
    public void shouldFindAllKeysInString() {
        String trigger = String.format("%s.%s?toupper?join(-)", TRIGGER_PREFIX, KEY_VALUE);
        String additionalData = String.format("%s.%s.%s#%d.%s?toupper?join(-)", ADDITIONAL_DATA_PREFIX, DATA_PROVIDER_ID, OBJECT_TYPE, OBJECT_ID, KEY_VALUE);
        String original = String.format("Trigger: {{%s}} Additional data: {{%s}}", trigger, additionalData);

        List<KeyInformation> keys = KeyInformation.parseAll(original);

        assertEquals(2, keys.size());
        assertKeyFromTrigger(trigger, keys.get(0));
        assertKeyFromAdditionalData(additionalData, keys.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenKeyIsFromUnknownSource() {
        KeyInformation.parse("test");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenAdditionalDataKeyHasIncorrectFormat() {
        String original = String.format("%s.%s.%s#.%s?toupper?join(-)", ADDITIONAL_DATA_PREFIX, DATA_PROVIDER_ID, OBJECT_TYPE, KEY_VALUE);
        KeyInformation.parse(original);
    }

    private void assertManipulations(KeyInformation key) {
        assertNotNull(key.getManipulations());

        if (!key.getManipulations().isEmpty()) {
            List<String> manipulations = key.getManipulations();

            assertEquals(2, manipulations.size());
            assertEquals("toupper", manipulations.get(0));
            assertEquals("join(-)", manipulations.get(1));
        }
    }

    private void assertKeyFromTrigger(String original, KeyInformation triggerKey) {
        assertTrue(triggerKey.fromTrigger());
        assertFalse(triggerKey.fromAdditionalData());

        assertEquals(KEY_VALUE, triggerKey.getKey());
        assertEquals(original, triggerKey.getOriginalKey());

        assertNull(triggerKey.getObjectId());
        assertNull(triggerKey.getObjectType());
        assertNull(triggerKey.getDataProviderId());

        assertManipulations(triggerKey);
    }

    private void assertKeyFromAdditionalData(String original, KeyInformation additionalDataKey) {
        assertTrue(additionalDataKey.fromAdditionalData());
        assertFalse(additionalDataKey.fromTrigger());

        assertEquals(DATA_PROVIDER_ID, additionalDataKey.getDataProviderId());
        assertEquals(OBJECT_TYPE, additionalDataKey.getObjectType());
        assertEquals(OBJECT_ID, additionalDataKey.getObjectId());
        assertEquals(KEY_VALUE, additionalDataKey.getKey());
        assertEquals(original, additionalDataKey.getOriginalKey());

        assertManipulations(additionalDataKey);
    }

}
