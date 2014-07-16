package org.motechproject.mds.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.motechproject.mds.BaseInstanceIT;
import org.motechproject.mds.domain.ConfigSettings;
import org.motechproject.mds.dto.FieldDto;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.repository.AllConfigSettings;
import org.motechproject.mds.testutil.MockBundleContext;
import org.motechproject.mds.util.HistoryFieldUtil;
import org.motechproject.mds.util.MDSClassLoader;
import org.motechproject.mds.util.PropertyUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang.StringUtils.equalsIgnoreCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.motechproject.mds.testutil.FieldTestHelper.fieldDto;

public class HistoryServiceIT extends BaseInstanceIT {
    private static final String[] ORIGINAL_VALUES = {"Maecenas", "ut", "justo", "porta", "fermentum", "tellus"};

    private static final String LOREM = "Lorem";
    private static final String IPSUM = "ipsum";

    @Autowired
    private HistoryService historyService;

    @Autowired
    private TrashService trashService;

    // TODO: fix problem with access to instance of InstanceService
    // @Autowired
    // private InstanceService instanceService;

    @Autowired
    private MockBundleContext bundleContext;

    // Just a holder for the mock bundle from test context,
    // which is not used in this test
    private Bundle mockBundle;

    @Mock
    Bundle bundle;

    @Mock
    BundleWiring wiring;

    @Autowired
    private AllConfigSettings allConfigSettings;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        setUpForInstanceTesting();
        MockitoAnnotations.initMocks(this);
        bundleContext.setService(getService());

        // Preserve the mock bundle from test context
        mockBundle = bundleContext.getBundle();

        bundleContext.setBundle(bundle);
        Mockito.when(bundle.adapt(BundleWiring.class)).thenReturn(wiring);
        Mockito.when(wiring.getClassLoader()).thenReturn(MDSClassLoader.getInstance());

        createSettings();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Set back the mock bundle from test context for other tests
        bundleContext.setBundle(mockBundle);

        try {
            getPersistenceManager().deletePersistentAll(getAll(getEntityClass()));
            getPersistenceManager().deletePersistentAll(getAll(getHistoryClass()));
            getPersistenceManager().deletePersistentAll(getAll(getTrashClass()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void shouldCreateHistoricalRecord() throws Exception {
        Object instance = createInstance(ORIGINAL_VALUES[0]);
        QueryParams queryParams = new QueryParams(1,10,null);
        List records = historyService.getHistoryForInstance(instance, queryParams);
        assertRecords(records, 1);

        Object record = records.get(0);

        String currentValue = (String) PropertyUtil.safeGetProperty(instance, IPSUM);
        String historicalValue = (String) PropertyUtil.safeGetProperty(record, IPSUM);

        assertEquals(ORIGINAL_VALUES[0], currentValue);
        assertEquals(ORIGINAL_VALUES[0], historicalValue);
    }

    @Test
    public void shouldNotMixHistoricalRecords() throws Exception {
        // creates and updates instances one after another
        QueryParams queryParams = new QueryParams(1,10,null);
        Object instance1 = createInstance(ORIGINAL_VALUES[0]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[2]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[4]);

        Object instance2 = createInstance(ORIGINAL_VALUES[1]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[3]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[5]);

        // creates instances and then updates them alternately
        Object instance3 = createInstance(ORIGINAL_VALUES[0]);
        Object instance4 = createInstance(ORIGINAL_VALUES[1]);

        instance3 = updateInstance(instance3, ORIGINAL_VALUES[2]);
        instance4 = updateInstance(instance4, ORIGINAL_VALUES[3]);

        instance3 = updateInstance(instance3, ORIGINAL_VALUES[4]);
        instance4 = updateInstance(instance4, ORIGINAL_VALUES[5]);

        List records1 = historyService.getHistoryForInstance(instance1, queryParams);
        assertRecords(records1, 3);

        List records2 = historyService.getHistoryForInstance(instance2, queryParams);
        assertRecords(records2, 3);

        List records3 = historyService.getHistoryForInstance(instance3, queryParams);
        assertRecords(records3, 3);

        List records4 = historyService.getHistoryForInstance(instance4, queryParams);
        assertRecords(records4, 3);

        for (int i = 0; i < ORIGINAL_VALUES.length; ++i) {
            List list1 = i % 2 == 0 ? records1 : records2;
            hasRecord(list1, ORIGINAL_VALUES[i]);

            List list2 = i % 2 == 0 ? records3 : records4;
            hasRecord(list2, ORIGINAL_VALUES[i]);
        }
    }

    @Test
    public void shouldRemoveOnlyCorrectRecords() throws Exception {
        QueryParams queryParams = new QueryParams(1,10,null);
        Object instance1 = createInstance(ORIGINAL_VALUES[0]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[2]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[4]);

        Object instance2 = createInstance(ORIGINAL_VALUES[1]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[3]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[5]);

        historyService.remove(instance1);

        List records1 = historyService.getHistoryForInstance(instance1, queryParams);
        assertRecords(records1, 0);

        List records2 = historyService.getHistoryForInstance(instance2, null);
        assertRecords(records2, 3);

        for (int i = 1; i < ORIGINAL_VALUES.length; i += 2) {
            hasRecord(records2, ORIGINAL_VALUES[i]);
        }
    }

    @Test
    public void shouldConnectHistoricalRecordsWithTrashInstance() throws Exception {
        QueryParams queryParams = new QueryParams(1,10,null);
        Object instance1 = createInstance(ORIGINAL_VALUES[0]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[2]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[4]);

        Object instance2 = createInstance(ORIGINAL_VALUES[1]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[3]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[5]);

        List records1 = historyService.getHistoryForInstance(instance1, null);
        assertRecords(records1, 3);

        List records2 = historyService.getHistoryForInstance(instance2, queryParams);
        assertRecords(records2, 3);

        getService().delete(instance1);

        records1 = historyService.getHistoryForInstance(instance1, queryParams);
        assertRecords(records1, 0);

        records2 = historyService.getHistoryForInstance(instance2, queryParams);
        assertRecords(records2, 3);

        Class<?> historyClass = getHistoryClass();

        PersistenceManager manager = getPersistenceManager();
        Query query = manager.newQuery(historyClass);
        List collection = (List) query.execute();

        // by default deleted instances are moved to the MDS trash and their historical data are
        // still accessible by query
        assertRecords(collection, 6);

        for (int i = 0; i < ORIGINAL_VALUES.length; ++i) {
            Object record = hasRecord(collection, ORIGINAL_VALUES[i]);
            Object property = PropertyUtil.safeGetProperty(record, HistoryFieldUtil.trashFlag(historyClass));

            // even records should have set trash flag
            // odd records should have unset trash flag
            assertEquals(i % 2 == 0, property);
        }
    }

    @Test
    @Ignore
    public void shouldRevertPreviousVersion() throws Exception {
        QueryParams queryParams = new QueryParams(1,10,null);
        Object instance = createInstance(ORIGINAL_VALUES[0]);
        instance = updateInstance(instance, ORIGINAL_VALUES[2]);
        instance = updateInstance(instance, ORIGINAL_VALUES[4]);

        List records = historyService.getHistoryForInstance(instance, null);
        assertRecords(records, 3);

        Long entityId = getEntity().getId();
        Long instanceId = getInstanceId(instance);

        for (int i = 1; i <= records.size(); ++i) {
            Object record = records.get(i - 1);
            Long historyId = getInstanceId(record);

            // TODO: fix problem with access to instance of InstanceService
            // instanceService.revertPreviousVersion(entityId, instanceId, historyId);

            instance = getService().retrieve("id", instanceId);

            List collection = historyService.getHistoryForInstance(instance, queryParams);
            // 3 records are in database, 'i' represents number of reversions
            assertRecords(collection, 3 + i);

            String recordValue = (String) PropertyUtil.safeGetProperty(record, IPSUM);
            String instanceValue = (String) PropertyUtil.safeGetProperty(instance, IPSUM);

            assertEquals(recordValue, instanceValue);
        }
    }

    @Test
    @Ignore
    public void shouldProperlyAssignRecordsAfterMoveFromTrash() throws Exception {
        QueryParams queryParams = new QueryParams(1,10,null);
        Object instance1 = createInstance(ORIGINAL_VALUES[0]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[2]);
        instance1 = updateInstance(instance1, ORIGINAL_VALUES[4]);

        Object instance2 = createInstance(ORIGINAL_VALUES[1]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[3]);
        instance2 = updateInstance(instance2, ORIGINAL_VALUES[5]);

        List records1 = historyService.getHistoryForInstance(instance1, queryParams);
        assertRecords(records1, 3);

        List records2 = historyService.getHistoryForInstance(instance2, queryParams);
        assertRecords(records2, 3);

        Long instanceId = getInstanceId(instance1);

        // trash instance should have the same id as instance1
        PersistenceManager manager = getPersistenceManager();
        Query query = manager.newQuery(
                "javax.jdo.query.SQL",
                String.format(
                        "UPDATE SEQUENCE_TABLE SET NEXT_VAL=%d WHERE SEQUENCE_NAME LIKE '%s'",
                        instanceId, getEntityClassName()
                )
        );
        query.execute();

        getService().delete(instance2);

        records1 = historyService.getHistoryForInstance(instance1, queryParams);
        assertRecords(records1, 3);

        records2 = historyService.getHistoryForInstance(instance2, queryParams);
        assertRecords(records2, 0);

        Collection removed = trashService.getInstancesFromTrash(getEntityClassName(), null);
        assertRecords(removed, 1);

        Long entityId = getEntity().getId();
        instanceId = getInstanceId(removed.iterator().next());

        // TODO: fix problem with access to instance of InstanceService
        // instanceService.revertInstanceFromTrash(entityId, instanceId);

        // the ID of instance1 has been changed and we have to retrieve all instances from database
        // and found instance1 with new ID
        List list = getService().retrieveAll();
        assertRecords(list, 2);

        instanceId = getInstanceId(instance1);
        for (Object item : list) {
            Long itemId = (Long) PropertyUtil.safeGetProperty(item, "id");

            // if instanceId is equal to itemId, the item will be equal to instance2
            if (!instanceId.equals(itemId)) {
                instance2 = item;
                break;
            }
        }

        records1 = historyService.getHistoryForInstance(instance1, queryParams);
        assertRecords(records1, 3);

        records2 = historyService.getHistoryForInstance(instance2, null);
        assertRecords(records2, 3);
    }

    @Override
    protected String getEntityName() {
        return LOREM;
    }

    @Override
    protected List<FieldDto> getEntityFields() {
        List<FieldDto> fields = new ArrayList<>();
        fields.add(fieldDto(IPSUM, String.class.getName()));
        return fields;
    }

    @Override
    protected HistoryService getHistoryService() {
        return historyService;
    }

    @Override
    protected TrashService getTrashService() {
        return trashService;
    }

    private void assertRecords(Collection records, int size) {
        boolean isEmpty = size == 0;

        assertEquals(String.format("There should%s be records", isEmpty ? "n't" : ""), isEmpty, records.isEmpty());
        assertEquals(String.format("There should be exactly %d record(s)", size), size, records.size());
    }

    private Object hasRecord(List records, String value) {
        Object record = null;

        for (Object r : records) {
            String original = (String) PropertyUtil.safeGetProperty(r, IPSUM);

            if (equalsIgnoreCase(value, original)) {
                record = r;
                break;
            }
        }

        assertNotNull("There should be a record with " + IPSUM + " property equal to " + value, record);

        return record;
    }

    private Object createInstance(String value) throws Exception {
        Object instance = getEntityClass().newInstance();

        PropertyUtil.safeSetProperty(instance, IPSUM, value);

        return getService().create(instance);
    }

    private void createSettings() throws Exception {
        ConfigSettings configSetting = new ConfigSettings();

        if (allConfigSettings != null) {
            allConfigSettings.addOrUpdate(configSetting);
        }
    }

    private Object updateInstance(Object instance, String value) {
        PropertyUtil.safeSetProperty(instance, IPSUM, value);

        return getService().update(instance);
    }

    private Long getInstanceId(Object instance) {
        Object value = PropertyUtil.safeGetProperty(instance, "id");
        Number id = null;

        if (value instanceof Number) {
            id = (Number) value;
        }

        return null == id ? null : id.longValue();
    }

}
