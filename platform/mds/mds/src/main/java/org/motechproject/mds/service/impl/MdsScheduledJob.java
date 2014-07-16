package org.motechproject.mds.service.impl;

import org.motechproject.bundle.extender.MotechOsgiConfigurableApplicationContext;
import org.motechproject.mds.service.TrashService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 *  Job responsible for emptying MDS trash.
 */
public class MdsScheduledJob implements Job {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    @SuppressWarnings("unchecked")
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        log.info("executing...");

        try {
            SchedulerContext schedulerContext;
            try {
                schedulerContext = jobExecutionContext.getScheduler().getContext();
            } catch (SchedulerException e) {
                log.error("Can not execute job. Can not get Scheduler Context", e);
                return;
            }
            ApplicationContext applicationContext = (ApplicationContext) schedulerContext.get("applicationContext");
            BundleContext bundleContext = ((MotechOsgiConfigurableApplicationContext) applicationContext).getBundleContext();
            WebApplicationContext webApplicationContext = null;
            ServiceReference[] references;

            references = bundleContext.getAllServiceReferences(WebApplicationContext.class.getName(), null);

            if (references != null) {
                for (ServiceReference ref : references) {
                    if ("org.motechproject.motech-platform-dataservices".equals(ref.getBundle().getSymbolicName())) {
                        webApplicationContext = (WebApplicationContext) bundleContext.getService(ref);
                        break;
                    }
                }
            }

            if (webApplicationContext != null) {
                TrashService trashService = (TrashService) webApplicationContext.getBean("trashServiceOSGi");
                trashService.emptyTrash();
            }

        } catch (Exception e) {
            log.error("Job execution failed.", e);
        }
    }
}
