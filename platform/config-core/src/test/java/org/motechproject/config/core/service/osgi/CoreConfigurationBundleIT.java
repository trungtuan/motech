package org.motechproject.config.core.service.osgi;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.config.core.domain.BootstrapConfig;
import org.motechproject.config.core.service.CoreConfigurationService;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import javax.inject.Inject;

import static org.junit.Assert.assertNotNull;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class CoreConfigurationBundleIT extends BasePaxIT {

    @Inject
    private CoreConfigurationService coreConfigurationService;

    @Override
    protected boolean shouldFakeModuleStartupEvent() {
        return false;
    }

    @Test
    public void testBootstrapConfigBundleIT() {
        BootstrapConfig bootstrapConfig = coreConfigurationService.loadBootstrapConfig();
        assertNotNull(bootstrapConfig);
        assertNotNull(bootstrapConfig.getSqlConfig());
        assertNotNull(bootstrapConfig.getConfigSource());
    }
}
