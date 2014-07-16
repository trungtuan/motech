package org.motechproject.mds.service.impl;

import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.mds.ex.MdsSchedulerException;
import org.motechproject.mds.service.MdsSchedulerService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.quartz.JobDetail;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import java.lang.reflect.Method;
import java.util.Date;

import static org.motechproject.mds.util.Constants.Config.EMPTY_TRASH_JOB;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

@Service("mdsSchedulerService")
public class MdsSchedulerServiceImpl implements MdsSchedulerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MdsSchedulerServiceImpl.class);

    public static final String JOB_GROUP_NAME = "default";
    public static final int MAX_REPEAT_COUNT = 999999;
    public static final int DEFAULT_WAIT_TIME = 5000;
    public static final int RETRIEVAL_RETRIES_COUNT = 10;
    public static final String SCHEDULER_SYMBOLIC_NAME = "org.motechproject.motech-scheduler";

    private BundleContext bundleContext;
    private Scheduler scheduler;
    private WebApplicationContext webApplicationContext;
    private Object motechSchedulerFactoryBean;

    @Autowired
    public MdsSchedulerServiceImpl(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void scheduleRepeatingJob(long interval) {
        Date jobStartTime = DateUtil.nowUTC().toDate();

        long repeatIntervalInMilliSeconds = interval;
        if (repeatIntervalInMilliSeconds == 0) {
            String errorMessage = "Invalid RepeatingSchedulableJob. The job repeat interval can not be 0";
            LOGGER.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        JobDetail jobDetail = newJob(MdsScheduledJob.class)
                .withIdentity(jobKey(EMPTY_TRASH_JOB, JOB_GROUP_NAME))
                .build();

        ScheduleBuilder scheduleBuilder;
            SimpleScheduleBuilder simpleSchedule = simpleSchedule()
                    .withIntervalInMilliseconds(repeatIntervalInMilliSeconds)
                    .withRepeatCount(MAX_REPEAT_COUNT);

            scheduleBuilder = simpleSchedule;

        Trigger trigger = buildJobDetail(jobStartTime, jobDetail, scheduleBuilder);
        scheduleJob(jobDetail, trigger);
    }

    private Trigger buildJobDetail(Date jobStartTime, JobDetail jobDetail, ScheduleBuilder scheduleBuilder) {
        Trigger trigger = newTrigger()
                .withIdentity(triggerKey(EMPTY_TRASH_JOB, JOB_GROUP_NAME))
                .forJob(jobDetail)
                .withSchedule(scheduleBuilder)
                .startAt(jobStartTime)
                .build();
        return trigger;
    }

    private void scheduleJob(JobDetail jobDetail, Trigger trigger) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Scheduling job:" + jobDetail);
        }
        try {
            if (scheduler == null) {
                findMotechSchedulerFactoryBean();
            }
            if (scheduler != null) {
                scheduler.scheduleJob(jobDetail, trigger);
            }
        } catch (SchedulerException e) {
            handleException(String.format("Can not schedule the job:\n %s\n%s\n%s", jobDetail.toString(), trigger.toString(), e.getMessage()), e);
        }
    }

    @Override
    public void unscheduleRepeatingJob() {
        try {
            if (scheduler == null) {
                findMotechSchedulerFactoryBean();
            }
            if (scheduler != null) {
                scheduler.unscheduleJob(triggerKey(EMPTY_TRASH_JOB, JOB_GROUP_NAME));
            }
        } catch (SchedulerException e) {
            handleException(String.format("Can not unschedule the job: %s %s", EMPTY_TRASH_JOB, e.getMessage()), e);
        }
    }

    private void handleException(String errorMessage, Exception e) {
        throw new MdsSchedulerException(errorMessage, e);
    }

    private void findMotechSchedulerFactoryBean() {
        ServiceReference[] references;
        int tries = 0;

        try {
            do {
                references = bundleContext.getAllServiceReferences(WebApplicationContext.class.getName(), null);

                if (references != null) {
                    for (ServiceReference ref : references) {
                        if (SCHEDULER_SYMBOLIC_NAME.equals(ref.getBundle().getSymbolicName())) {
                            webApplicationContext = (WebApplicationContext) bundleContext.getService(ref);
                            break;
                        }
                    }
                }
                ++tries;
                Thread.sleep(DEFAULT_WAIT_TIME);
            } while (webApplicationContext == null && tries < RETRIEVAL_RETRIES_COUNT);

            if (webApplicationContext != null) {
                motechSchedulerFactoryBean = webApplicationContext.getBean("motechSchedulerFactoryBean");
                Method method = motechSchedulerFactoryBean.getClass().getMethod("getQuartzScheduler");
                scheduler = (Scheduler) method.invoke(motechSchedulerFactoryBean);
            }
        } catch (Exception e) {
            handleException("Can't find motechSchedulerFactoryBean", e);
        }
    }
}
