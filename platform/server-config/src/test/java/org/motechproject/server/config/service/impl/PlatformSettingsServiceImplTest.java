package org.motechproject.server.config.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.motechproject.commons.couchdb.service.impl.CouchDbManagerImpl;
import org.motechproject.config.core.constants.ConfigurationConstants;
import org.motechproject.server.config.domain.SettingsRecord;
import org.motechproject.server.config.service.ConfigLoader;
import org.motechproject.server.config.service.PlatformSettingsService;
import org.motechproject.server.config.service.SettingService;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class PlatformSettingsServiceImplTest {

    @Mock
    CouchDbManagerImpl couchDbManager;

    @Mock
    private SettingService settingService;

    @Mock
    ConfigLoader configLoader;

    @InjectMocks
    PlatformSettingsService platformSettingsService = new PlatformSettingsServiceImpl();

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testExport() throws IOException {
        SettingsRecord settings = new SettingsRecord();
        settings.savePlatformSetting(ConfigurationConstants.LANGUAGE, "en");
        when(configLoader.loadMotechSettings()).thenReturn(settings);
        when(settingService.retrieve("id", 1)).thenReturn(settings);

        Properties p = platformSettingsService.exportPlatformSettings();

        assertTrue(p.containsKey(ConfigurationConstants.LANGUAGE));
        assertEquals(settings.getLanguage(), p.getProperty(ConfigurationConstants.LANGUAGE));
    }
}