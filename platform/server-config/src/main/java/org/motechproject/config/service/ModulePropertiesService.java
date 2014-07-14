package org.motechproject.config.service;

import org.motechproject.config.domain.ModulePropertiesRecord;
import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.service.MotechDataService;

import java.util.List;

public interface ModulePropertiesService extends MotechDataService<ModulePropertiesRecord> {

    @Lookup(name = "By module and file name")
    List<ModulePropertiesRecord> findByModuleAndFileName(@LookupField(name = "module") String module,
                                                         @LookupField(name = "filename") String filename);

    @Lookup(name = "By module")
    List<ModulePropertiesRecord> findByModule(@LookupField(name = "module") String module);

    @Lookup(name = "By bundle")
    List<ModulePropertiesRecord> findByBundle(@LookupField(name = "bundle") String bundle);

}
