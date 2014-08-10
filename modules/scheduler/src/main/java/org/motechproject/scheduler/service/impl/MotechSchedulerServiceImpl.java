package org.motechproject.scheduler.service.impl;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.motechproject.commons.date.model.Time;
import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.event.MotechEvent;
import org.motechproject.scheduler.contract.CronJobId;
import org.motechproject.scheduler.contract.CronSchedulableJob;
import org.motechproject.scheduler.contract.DayOfWeekSchedulableJob;
import org.motechproject.scheduler.contract.EventInfo;
import org.motechproject.scheduler.contract.JobBasicInfo;
import org.motechproject.scheduler.contract.JobDetailedInfo;
import org.motechproject.scheduler.contract.JobId;
import org.motechproject.scheduler.contract.RepeatingJobId;
import org.motechproject.scheduler.contract.RepeatingSchedulableJob;
import org.motechproject.scheduler.contract.RunOnceJobId;
import org.motechproject.scheduler.contract.RunOnceSchedulableJob;
import org.motechproject.scheduler.exception.MotechSchedulerException;
import org.motechproject.scheduler.factory.MotechSchedulerFactoryBean;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.motechproject.server.config.SettingsFacade;
import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CalendarIntervalTrigger;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.TriggerUtils;
import org.quartz.impl.calendar.BaseCalendar;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.quartz.spi.OperableTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.motechproject.commons.date.util.DateUtil.newDateTime;
import static org.motechproject.commons.date.util.DateUtil.now;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

/**
 * Motech Scheduler Service implementation
 *
 * @see MotechSchedulerService
 */
@Service("schedulerService")
public class MotechSchedulerServiceImpl implements MotechSchedulerService {

    public static final String JOB_GROUP_NAME = "default";
    private static final int MAX_REPEAT_COUNT = 999999;
    private static final int MILLISECOND = 1000;
    private static final String LOG_SUBJECT_EXTERNALID = "subject: %s, externalId: %s";

    private SettingsFacade schedulerSettings;

    private Scheduler scheduler;

    private Map<String, Integer> cronTriggerMisfirePolicies;
    private Map<String, Integer> simpleTriggerMisfirePolicies;

    private Logger logger = LoggerFactory.getLogger(MotechSchedulerServiceImpl.class);

    @Autowired
    public MotechSchedulerServiceImpl(MotechSchedulerFactoryBean motechSchedulerFactoryBean, SettingsFacade schedulerSettings) {
        this.schedulerSettings = schedulerSettings;
        this.scheduler = motechSchedulerFactoryBean.getQuartzScheduler();
        constructMisfirePoliciesMaps();
    }

    private void constructMisfirePoliciesMaps() {
        cronTriggerMisfirePolicies = new HashMap<>();
        cronTriggerMisfirePolicies.put("do_nothing", CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        cronTriggerMisfirePolicies.put("fire_once_now", CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        cronTriggerMisfirePolicies.put("ignore", CronTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);

        simpleTriggerMisfirePolicies = new HashMap<>();
        simpleTriggerMisfirePolicies.put("fire_now", SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        simpleTriggerMisfirePolicies.put("ignore", SimpleTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
        simpleTriggerMisfirePolicies.put("reschedule_next_with_existing_count", SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT);
        simpleTriggerMisfirePolicies.put("reschedule_next_with_remaining_count", SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        simpleTriggerMisfirePolicies.put("reschedule_now_with_existing_count", SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT);
        simpleTriggerMisfirePolicies.put("reschedule_now_with_remaining_count", SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT);
    }

    @Override
    public void scheduleJob(CronSchedulableJob cronSchedulableJob) {
        logObjectIfNotNull(cronSchedulableJob);

        MotechEvent motechEvent = assertCronJob(cronSchedulableJob);

        JobId jobId = new CronJobId(motechEvent);

        JobDetail jobDetail = newJob(MotechScheduledJob.class)
                .withIdentity(jobKey(jobId.value(), JOB_GROUP_NAME))
                .build();

        putMotechEventDataToJobDataMap(jobDetail.getJobDataMap(), motechEvent);

        CronScheduleBuilder cronSchedule;
        try {
            cronSchedule = cronSchedule(cronSchedulableJob.getCronExpression());
        } catch (Exception e) {
            String errorMessage = format("Can not schedule job %s; invalid Cron expression: %s", jobId, cronSchedulableJob.getCronExpression());
            logger.error(errorMessage);
            throw new MotechSchedulerException(errorMessage, e);
        }

        // TODO: should take readable names rather than integers
        cronSchedule = setMisfirePolicyForCronTrigger(cronSchedule,  schedulerSettings.getProperty("scheduler.cron.trigger.misfire.policy"));

        CronTrigger trigger = newTrigger()
                .withIdentity(triggerKey(jobId.value(), JOB_GROUP_NAME))
                .forJob(jobDetail)
                .withSchedule(cronSchedule)
                .startAt(cronSchedulableJob.getStartTime() != null ? cronSchedulableJob.getStartTime() : now().toDate())
                .endAt(cronSchedulableJob.getEndTime())
                .build();

        Trigger existingTrigger;
        try {
            existingTrigger = scheduler.getTrigger(triggerKey(jobId.value(), JOB_GROUP_NAME));
        } catch (SchedulerException e) {
            String errorMessage = format("Schedule or reschedule the job: %s.\n%s", jobId, e.getMessage());
            logger.error(errorMessage, e);
            throw new MotechSchedulerException(errorMessage, e);
        }
        if (existingTrigger != null) {
            unscheduleJob(jobId.value());
        }

        DateTime now = now();

        if (cronSchedulableJob.isIgnorePastFiresAtStart() && newDateTime(cronSchedulableJob.getStartTime()).isBefore(now)) {

            Date newStartTime = trigger.getFireTimeAfter(now.toDate());
            if (newStartTime == null) {
                newStartTime = now.toDate();
            }

            trigger = newTrigger()
                .withIdentity(triggerKey(jobId.value(), JOB_GROUP_NAME))
                .forJob(jobDetail)
                .withSchedule(cronSchedule)
                .startAt(newStartTime)
                .endAt(cronSchedulableJob.getEndTime())
                .build();
        }

        scheduleJob(jobDetail, trigger);
    }

    private MotechEvent assertCronJob(CronSchedulableJob cronSchedulableJob) {
        assertArgumentNotNull("SchedulableJob", cronSchedulableJob);
        MotechEvent motechEvent = cronSchedulableJob.getMotechEvent();
        assertArgumentNotNull("MotechEvent of the SchedulableJob", motechEvent);
        return motechEvent;
    }

    private CronScheduleBuilder setMisfirePolicyForCronTrigger(CronScheduleBuilder cronSchedule, String motechMisfireProperty) {
        Integer misfirePolicyAsInt = cronTriggerMisfirePolicies.get(motechMisfireProperty);
        if (misfirePolicyAsInt == null || misfirePolicyAsInt.equals(CronTrigger.MISFIRE_INSTRUCTION_SMART_POLICY)) {
            return cronSchedule;
        }
        if (misfirePolicyAsInt.equals(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING)) {
            return cronSchedule.withMisfireHandlingInstructionDoNothing();
        }
        if (misfirePolicyAsInt.equals(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW)) {
            return cronSchedule.withMisfireHandlingInstructionFireAndProceed();
        }
        if (misfirePolicyAsInt.equals(CronTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY)) {
            return cronSchedule.withMisfireHandlingInstructionIgnoreMisfires();
        }
        return cronSchedule;
    }

    @Override
    public void safeScheduleJob(CronSchedulableJob cronSchedulableJob) {
        logObjectIfNotNull(cronSchedulableJob);

        assertCronJob(cronSchedulableJob);

        JobId jobId = new CronJobId(cronSchedulableJob.getMotechEvent());
        try {
            unscheduleJob(jobId.value());
        } catch (MotechSchedulerException e) {
            logger.error(e.getMessage());
        }
        scheduleJob(cronSchedulableJob);
    }

    @Override
    @Deprecated // nobody's using it and can't imagine a case for this
    public void updateScheduledJob(MotechEvent motechEvent) {
        logObjectIfNotNull(motechEvent);

        assertArgumentNotNull("MotechEvent", motechEvent);

        JobId jobId = new CronJobId(motechEvent);
        Trigger trigger;

        try {
            trigger = scheduler.getTrigger(triggerKey(jobId.value(), JOB_GROUP_NAME));

            if (trigger == null) {
                String errorMessage = "Can not update the job: " + jobId + " The job does not exist (not scheduled)";
                logger.error(errorMessage);
                throw new MotechSchedulerException(errorMessage);
            }

        } catch (SchedulerException e) {
            String errorMessage = "Can not update the job: " + jobId +
                    ".\n Can not get a trigger associated with that job " + e.getMessage();
            logger.error(errorMessage, e);
            throw new MotechSchedulerException(errorMessage, e);
        }

        try {
            scheduler.deleteJob(jobKey(jobId.value(), JOB_GROUP_NAME));
        } catch (SchedulerException e) {
            handleException(String.format("Can not update the job: %s.\n Can not delete old instance of the job %s", jobId, e.getMessage()), e);
        }

        JobDetail jobDetail = newJob(MotechScheduledJob.class).withIdentity(jobId.value(), JOB_GROUP_NAME).build();
        putMotechEventDataToJobDataMap(jobDetail.getJobDataMap(), motechEvent);

        scheduleJob(jobDetail, trigger);
    }

    @Override
    public void rescheduleJob(String subject, String externalId, String cronExpression) {
        if (logger.isDebugEnabled()) {
            logger.debug(subject + " " + externalId + " " + cronExpression);
        }
        assertArgumentNotNull("Subject", subject);
        assertArgumentNotNull("ExternalId", externalId);
        assertArgumentNotNull("Cron expression", cronExpression);

        JobId jobId = new CronJobId(subject, externalId);
        if (logger.isDebugEnabled()) {
            logger.debug(format("Rescheduling the Job: %s new cron expression: %s", jobId, cronExpression));
        }

        CronTrigger trigger = null;
        JobDetail job = null;
        try {
            trigger = (CronTrigger) scheduler.getTrigger(triggerKey(jobId.value(), JOB_GROUP_NAME));
            if (trigger == null) {
                logger.error(format("Can not reschedule the job: %s The job does not exist (not scheduled)", jobId));
                throw new MotechSchedulerException();
            }
            job = scheduler.getJobDetail(trigger.getJobKey());
        } catch (SchedulerException e) {
            handleException(String.format("Can not reschedule the job: %s.\n Can not get a trigger associated with that job %s", jobId, e.getMessage()), e);
        } catch (ClassCastException e) {
            handleException(String.format("Can not reschedule the job: %s.\n The trigger associated with that job is not a CronTrigger", jobId), e);
        }

        CronScheduleBuilder newCronSchedule = null;
        try {
            newCronSchedule = cronSchedule(cronExpression);
        } catch (Exception e) {
            handleException(String.format("Can not reschedule the job: %s Invalid Cron expression: %s", jobId, cronExpression), e);
        }

        CronTrigger newTrigger = newTrigger()
                .withIdentity(trigger.getKey())
                .forJob(job)
                .withSchedule(newCronSchedule)
                .startAt(trigger.getStartTime())
                .endAt(trigger.getEndTime())
                .build();

        try {
            scheduler.rescheduleJob(triggerKey(jobId.value(), JOB_GROUP_NAME), newTrigger);
        } catch (SchedulerException e) {
            handleException(String.format("Can not reschedule the job: %s %s", jobId, e.getMessage()), e);
        }
    }

    private void handleException(String errorMessage, Exception e) {
        logger.error(errorMessage, e);
        throw new MotechSchedulerException(errorMessage, e);
    }

    @Override
    public void scheduleRepeatingJob(RepeatingSchedulableJob repeatingSchedulableJob) {
        logObjectIfNotNull(repeatingSchedulableJob);

        MotechEvent motechEvent = assertArgumentNotNull(repeatingSchedulableJob);

        Date jobStartTime = repeatingSchedulableJob.getStartTime();
        Date jobEndTime = repeatingSchedulableJob.getEndTime();
        assertArgumentNotNull("Job start date", jobStartTime);

        long repeatIntervalInMilliSeconds = repeatingSchedulableJob.getRepeatIntervalInMilliSeconds();
        if (repeatIntervalInMilliSeconds == 0) {
            String errorMessage = "Invalid RepeatingSchedulableJob. The job repeat interval can not be 0";
            logger.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        Integer jobRepeatCount = repeatingSchedulableJob.getRepeatCount();
        if (null == jobRepeatCount) {
            jobRepeatCount = MAX_REPEAT_COUNT;
        }

        JobId jobId = new RepeatingJobId(motechEvent);
        JobDetail jobDetail = newJob(MotechScheduledJob.class)
                .withIdentity(jobKey(jobId.value(), JOB_GROUP_NAME))
                .build();

        putMotechEventDataToJobDataMap(jobDetail.getJobDataMap(), motechEvent);

        ScheduleBuilder scheduleBuilder;
        if (!repeatingSchedulableJob.isUseOriginalFireTimeAfterMisfire()) {
            SimpleScheduleBuilder simpleSchedule = simpleSchedule()
                    .withIntervalInMilliseconds(repeatIntervalInMilliSeconds)
                    .withRepeatCount(jobRepeatCount);

            simpleSchedule = setMisfirePolicyForSimpleTrigger(simpleSchedule, schedulerSettings.getProperty("scheduler.repeating.trigger.misfire.policy"));

            scheduleBuilder = simpleSchedule;
        } else {
            if (repeatingSchedulableJob.getRepeatCount() != null) {
                final double half = 0.5;
                jobEndTime = new Date((long) (repeatingSchedulableJob.getStartTime().getTime() + repeatIntervalInMilliSeconds * (repeatingSchedulableJob.getRepeatCount() + half)));
            }
            scheduleBuilder = CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
                    .withIntervalInSeconds((int) (repeatIntervalInMilliSeconds / MILLISECOND))
                    .withMisfireHandlingInstructionFireAndProceed();
        }

        Trigger trigger = buildJobDetail(repeatingSchedulableJob, jobStartTime, jobEndTime, jobId, jobDetail, scheduleBuilder);
        scheduleJob(jobDetail, trigger);
    }

    private Trigger buildJobDetail(RepeatingSchedulableJob repeatingSchedulableJob, Date jobStartTime, Date jobEndTime, JobId jobId, JobDetail jobDetail, ScheduleBuilder scheduleBuilder) {
        Trigger trigger = newTrigger()
                .withIdentity(triggerKey(jobId.value(), JOB_GROUP_NAME))
                .forJob(jobDetail)
                .withSchedule(scheduleBuilder)
                .startAt(jobStartTime)
                .endAt(jobEndTime)
                .build();
        DateTime now = now();
        if (repeatingSchedulableJob.isIgnorePastFiresAtStart() && newDateTime(jobStartTime).isBefore(now)) {

            List<Date> pastTriggers = TriggerUtils.computeFireTimesBetween((OperableTrigger) trigger, null, jobStartTime, now.toDate());

            if (pastTriggers.size() > 0) {
                if (scheduleBuilder instanceof SimpleScheduleBuilder && repeatingSchedulableJob.getRepeatCount() != null) {
                    ((SimpleScheduleBuilder) scheduleBuilder).withRepeatCount(repeatingSchedulableJob.getRepeatCount() - pastTriggers.size());
                }
                Date newStartTime = getFirstTriggerInFuture(trigger, now);
                trigger = newTrigger()
                        .withIdentity(triggerKey(jobId.value(), JOB_GROUP_NAME))
                        .forJob(jobDetail)
                        .withSchedule(scheduleBuilder)
                        .startAt(newStartTime)
                        .endAt(jobEndTime)
                        .build();
            }
        }
        return trigger;
    }

    private Date getFirstTriggerInFuture(Trigger trigger, DateTime now) {   // extracted away because of checkstyle :(
        Date newStartTime = trigger.getFireTimeAfter(now.toDate());
        if (newStartTime == null) {
            newStartTime = now.toDate();
        }
        return newStartTime;
    }

    private SimpleScheduleBuilder setMisfirePolicyForSimpleTrigger(SimpleScheduleBuilder simpleSchedule, String motechMisfireProperty) {
        Integer misfirePolicy = simpleTriggerMisfirePolicies.get(motechMisfireProperty);
        if (misfirePolicy == null) {
            misfirePolicy = SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT;
        }
        if (misfirePolicy.equals(SimpleTrigger.MISFIRE_INSTRUCTION_SMART_POLICY)) {
            return simpleSchedule;
        }
        if (misfirePolicy.equals(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW)) {
            return simpleSchedule.withMisfireHandlingInstructionFireNow();
        }
        if (misfirePolicy.equals(SimpleTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY)) {
            return simpleSchedule.withMisfireHandlingInstructionIgnoreMisfires();
        }
        if (misfirePolicy.equals(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT)) {
            return simpleSchedule.withMisfireHandlingInstructionNextWithExistingCount();
        }
        if (misfirePolicy.equals(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT)) {
            return simpleSchedule.withMisfireHandlingInstructionNextWithRemainingCount();
        }
        if (misfirePolicy.equals(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT)) {
            return simpleSchedule.withMisfireHandlingInstructionNowWithExistingCount();
        }
        if (misfirePolicy.equals(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT)) {
            return simpleSchedule.withMisfireHandlingInstructionNowWithRemainingCount();
        }
        return simpleSchedule;
    }

    @Override
    public void safeScheduleRepeatingJob(RepeatingSchedulableJob repeatingSchedulableJob) {
        logObjectIfNotNull(repeatingSchedulableJob);

        assertArgumentNotNull(repeatingSchedulableJob);
        try {
            unscheduleJob(new RepeatingJobId(repeatingSchedulableJob.getMotechEvent()).value());
        } catch (MotechSchedulerException e) {
            logger.error(e.getMessage());
        }
        scheduleRepeatingJob(repeatingSchedulableJob);
    }

    @Override
    public void scheduleRunOnceJob(RunOnceSchedulableJob schedulableJob) {
        logObjectIfNotNull(schedulableJob);

        assertArgumentNotNull("RunOnceSchedulableJob", schedulableJob);
        MotechEvent motechEvent = schedulableJob.getMotechEvent();

        Date jobStartDate = schedulableJob.getStartDate();
        assertArgumentNotNull("Job start date", jobStartDate);
        Date currentDate = DateUtil.now().toDate();
        if (jobStartDate.before(currentDate)) {
            String errorMessage = "Invalid RunOnceSchedulableJob. The job start date can not be in the past. \n" +
                    " Job start date: " + jobStartDate.toString() +
                    " Attempted to schedule at:" + currentDate.toString();
            logger.error(errorMessage);
            throw new IllegalArgumentException();
        }

        JobId jobId = new RunOnceJobId(motechEvent);
        JobDetail jobDetail = newJob(MotechScheduledJob.class)
                .withIdentity(jobId.value(), JOB_GROUP_NAME)
                .build();

        putMotechEventDataToJobDataMap(jobDetail.getJobDataMap(), motechEvent);

        SimpleScheduleBuilder simpleSchedule = simpleSchedule()
                .withRepeatCount(0)
                .withIntervalInSeconds(0)
                .withMisfireHandlingInstructionFireNow();

        Trigger trigger = newTrigger()
                .withIdentity(triggerKey(jobId.value(), JOB_GROUP_NAME))
                .forJob(jobDetail)
                .withSchedule(simpleSchedule)
                .startAt(jobStartDate)
                .build();

        scheduleJob(jobDetail, trigger);
    }

    private MotechEvent assertArgumentNotNull(RepeatingSchedulableJob repeatingSchedulableJob) {
        assertArgumentNotNull("SchedulableJob", repeatingSchedulableJob);
        MotechEvent motechEvent = repeatingSchedulableJob.getMotechEvent();
        assertArgumentNotNull("Invalid SchedulableJob. MotechEvent of the SchedulableJob", motechEvent);
        return motechEvent;
    }


    public void safeScheduleRunOnceJob(RunOnceSchedulableJob schedulableJob) {
        logObjectIfNotNull(schedulableJob);
        assertArgumentNotNull("RunOnceSchedulableJob", schedulableJob);

        JobId jobId = new RunOnceJobId(schedulableJob.getMotechEvent());
        try {
            unscheduleJob(jobId.value());
        } catch (MotechSchedulerException e) {
            logger.error(e.getMessage());
        }
        scheduleRunOnceJob(schedulableJob);
    }

    @Override
    public void scheduleDayOfWeekJob(DayOfWeekSchedulableJob dayOfWeekSchedulableJob) {
        logObjectIfNotNull(dayOfWeekSchedulableJob);

        MotechEvent motechEvent = dayOfWeekSchedulableJob.getMotechEvent();
        LocalDate start = dayOfWeekSchedulableJob.getStartDate();
        LocalDate end = dayOfWeekSchedulableJob.getEndDate();
        Time time = dayOfWeekSchedulableJob.getTime();

        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.atHourAndMinuteOnGivenDaysOfWeek(time.getHour(), time.getMinute(), dayOfWeekSchedulableJob.getCronDays().toArray(new Integer[0]));
        CronTriggerImpl cronTrigger = (CronTriggerImpl) cronScheduleBuilder.build();
        CronSchedulableJob cronSchedulableJob = new CronSchedulableJob(motechEvent, cronTrigger.getCronExpression(), start.toDate(), end.toDate(), dayOfWeekSchedulableJob.isIgnorePastFiresAtStart());

        scheduleJob(cronSchedulableJob);
    }

    @Override
    public void unscheduleRepeatingJob(String subject, String externalId) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("unscheduling repeating job: " + LOG_SUBJECT_EXTERNALID, subject, externalId));
        }

        JobId jobId = new RepeatingJobId(subject, externalId);
        logObjectIfNotNull(jobId);

        unscheduleJob(jobId.value());
    }

    @Override
    public void safeUnscheduleRepeatingJob(String subject, String externalId) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("safe unscheduling repeating job: " + LOG_SUBJECT_EXTERNALID, subject, externalId));
        }
        try {
            unscheduleRepeatingJob(subject, externalId);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void unscheduleRunOnceJob(String subject, String externalId) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("unscheduling run once job: " + LOG_SUBJECT_EXTERNALID, subject, externalId));
        }

        JobId jobId = new RunOnceJobId(subject, externalId);
        logObjectIfNotNull(jobId);

        unscheduleJob(jobId.value());
    }

    @Override
    public void safeUnscheduleRunOnceJob(String subject, String externalId) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("safe unscheduling run once job: " + LOG_SUBJECT_EXTERNALID, subject, externalId));
        }
        try {
            unscheduleRunOnceJob(subject, externalId);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void unscheduleJob(String subject, String externalId) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("unscheduling cron job: " + LOG_SUBJECT_EXTERNALID, subject, externalId));
        }
        unscheduleJob(new CronJobId(subject, externalId));
    }

    @Override
    public void unscheduleJob(JobId job) {
        logObjectIfNotNull(job);
        unscheduleJob(job.value());
    }

    @Override
    public void safeUnscheduleJob(String subject, String externalId) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("safe unscheduling cron job: " + LOG_SUBJECT_EXTERNALID, subject, externalId));
        }
        try {
            unscheduleJob(subject, externalId);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void unscheduleJob(String jobId) {
        if (logger.isDebugEnabled()) {
            logger.debug(jobId);
        }
        try {
            assertArgumentNotNull("ScheduledJobID", jobId);
            scheduler.unscheduleJob(triggerKey(jobId, JOB_GROUP_NAME));
        } catch (SchedulerException e) {
            handleException(String.format("Can not unschedule the job: %s %s", jobId, e.getMessage()), e);
        }
    }

    private void safeUnscheduleJob(String jobId) {
        if (logger.isDebugEnabled()) {
            logger.debug(jobId);
        }
        try {
            assertArgumentNotNull("ScheduledJobID", jobId);
            scheduler.unscheduleJob(triggerKey(jobId, JOB_GROUP_NAME));
        } catch (SchedulerException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void safeUnscheduleAllJobs(String jobIdPrefix) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(format("Safe unscheduling the Jobs given jobIdPrefix: %s", jobIdPrefix));
            }
            List<TriggerKey> triggerKeys = new ArrayList<TriggerKey>(scheduler.getTriggerKeys(GroupMatcher.triggerGroupContains(JOB_GROUP_NAME)));
            List<String> triggerNames = extractTriggerNames(triggerKeys);
            for (String triggerName : triggerNames) {
                if (StringUtils.isNotEmpty(jobIdPrefix) && triggerName.contains(jobIdPrefix)) {
                    safeUnscheduleJob(triggerName);
                }
            }
        } catch (SchedulerException e) {
            logger.error(e.getMessage());
        }
    }

    /*
     * Assumes that the externalJobId is non-repeating in nature. Thus the fetch is for jobId.value() and not
     * jobId.repeatingId()
     * Uses quartz API to fetch the exact triggers. Fast
     */
    @Override
    public List<Date> getScheduledJobTimings(String subject, String externalJobId, Date startDate, Date endDate) {
        JobId jobId = new CronJobId(subject, externalJobId);
        Trigger trigger;
        List<Date> messageTimings = null;
        try {
            trigger = scheduler.getTrigger(triggerKey(jobId.value(), JOB_GROUP_NAME));
            messageTimings = TriggerUtils.computeFireTimesBetween(
                    (OperableTrigger) trigger, new BaseCalendar(), startDate, endDate);

        } catch (SchedulerException e) {
            handleException(String.format(
                    "Can not get scheduled job timings given subject and externalJobId for dates : %s %s %s %s %s",
                    subject, externalJobId, startDate.toString(), endDate.toString(), e.getMessage()), e);
        }
        return messageTimings;
    }

    /*
     * Loads all triggers and then loops over them to find the applicable trigger using string comparison. This
     * will work regardless of the jobId being cron or repeating.
     */
    @Override
    public List<Date> getScheduledJobTimingsWithPrefix(
            String subject, String externalJobIdPrefix, Date startDate, Date endDate) {

        JobId jobId = new CronJobId(subject, externalJobIdPrefix);
        List<Date> messageTimings = new ArrayList<>();
        try {
            List<TriggerKey> triggerKeys = new ArrayList<TriggerKey>(
                    scheduler.getTriggerKeys(GroupMatcher.triggerGroupContains(JOB_GROUP_NAME)));
            for (TriggerKey triggerKey : triggerKeys) {
                if (StringUtils.isNotEmpty(externalJobIdPrefix) && triggerKey.getName().contains(jobId.value())) {
                    Trigger trigger = scheduler.getTrigger(triggerKey);
                    messageTimings.addAll(TriggerUtils.computeFireTimesBetween(
                            (OperableTrigger) trigger, new BaseCalendar(), startDate, endDate));
                }
            }

        } catch (SchedulerException e) {
            handleException(String.format(
                    "Can not get scheduled job timings given subject and externalJobIdPrefix for dates : %s %s %s %s %s",
                    subject, externalJobIdPrefix, startDate.toString(), endDate.toString(), e.getMessage()), e);
        }

        return messageTimings;
    }

    @Override
    public List<JobBasicInfo> getScheduledJobsBasicInfo() {
        List<JobBasicInfo> result = new ArrayList<>();

        try {
            for (String groupName : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    Trigger trigger = scheduler.getTriggersOfJob(jobKey).get(0);
                    String jobName = jobKey.getName();
                    String jobType = getJobType(jobKey);
                    String activity = getJobActivity(jobKey);
                    String info = getJobInfo(jobKey, jobType);
                    String status = getJobStatus(jobKey);
                    String startDate = getStartDate(jobKey);
                    String nextFireDate = DateTimeFormat.forPattern("Y-MM-dd HH:mm:ss").print(trigger.getNextFireTime().getTime());
                    String endDate = getEndDate(jobKey, jobType);

                    result.add(
                            new JobBasicInfo(
                                    activity,
                                    status,
                                    jobName,
                                    startDate,
                                    nextFireDate,
                                    endDate,
                                    jobType,
                                    info
                            )
                    );
                }
            }
        } catch (SchedulerException e) {
            logger.error(e.toString());
        }

        return result;
    }

    @Override
    public JobDetailedInfo getScheduledJobDetailedInfo(JobBasicInfo jobBasicInfo) {
        JobDetailedInfo jobDetailedInfo = new JobDetailedInfo();
        List<EventInfo> eventInfos = new ArrayList<>();

        try {
            for (String groupName : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    if (jobKey.getName().equals(jobBasicInfo.getName())) {
                        EventInfo eventInfo = new EventInfo();
                        String subject;

                        eventInfo.setParameters(
                                scheduler.getJobDetail(jobKey).getJobDataMap().getWrappedMap()
                        );

                        if (eventInfo.getParameters().containsKey(MotechEvent.EVENT_TYPE_KEY_NAME)) {
                            subject = eventInfo.getParameters().get(MotechEvent.EVENT_TYPE_KEY_NAME).toString();
                            eventInfo.getParameters().remove(MotechEvent.EVENT_TYPE_KEY_NAME);
                        } else {
                            subject = jobKey.getName().substring(0, jobKey.getName().indexOf('-'));
                        }

                        eventInfo.setSubject(subject);

                        eventInfos.add(eventInfo);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        jobDetailedInfo.setEventInfoList(eventInfos);
        return jobDetailedInfo;
    }

    @Override
    public void unscheduleAllJobs(String jobIdPrefix) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(jobIdPrefix);
            }
            List<TriggerKey> triggerKeys = new ArrayList<>(scheduler.getTriggerKeys(GroupMatcher.triggerGroupContains(JOB_GROUP_NAME)));
            List<String> triggerNames = extractTriggerNames(triggerKeys);
            for (String triggerName : triggerNames) {
                if (StringUtils.isNotEmpty(jobIdPrefix) && triggerName.contains(jobIdPrefix)) {
                    unscheduleJob(triggerName);
                }
            }
        } catch (SchedulerException e) {
            handleException(String.format("Can not unschedule jobs given jobIdPrefix: %s %s", jobIdPrefix, e.getMessage()), e);
        }
    }

    private void scheduleJob(JobDetail jobDetail, Trigger trigger) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling job:" + jobDetail);
        }
        try {
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            handleException(String.format("Can not schedule the job:\n %s\n%s\n%s", jobDetail.toString(), trigger.toString(), e.getMessage()), e);
        }
    }

    private void putMotechEventDataToJobDataMap(JobDataMap jobDataMap, MotechEvent motechEvent) {
        jobDataMap.putAll(motechEvent.getParameters());
        jobDataMap.put(MotechEvent.EVENT_TYPE_KEY_NAME, motechEvent.getSubject());
    }

    private List<String> extractTriggerNames(List<TriggerKey> triggerKeys) {
        List<String> names = new ArrayList<>();
        for (TriggerKey key : triggerKeys) {
            names.add(key.getName());
        }
        return names;
    }

    private String getStartDate(JobKey jobKey) throws SchedulerException {
        Trigger trigger = scheduler.getTriggersOfJob(jobKey).get(0);

        return DateTimeFormat.forPattern("Y-MM-dd HH:mm:ss").print(trigger.getStartTime().getTime());
    }

    private String getEndDate(JobKey jobKey, String jobType) throws SchedulerException {
        Trigger trigger = scheduler.getTriggersOfJob(jobKey).get(0);
        DateTime endDateTime = new DateTime(trigger.getEndTime());
        String startDate = getStartDate(jobKey);
        String endDate;

        if (!endDateTime.isAfterNow()) {
            if (jobType.equals(JobBasicInfo.JOBTYPE_RUNONCE)) {
                endDate = startDate;
            } else {
                endDate = "-";
            }
        } else {
            endDate = DateTimeFormat.forPattern("Y-MM-dd HH:mm:ss").print(endDateTime);
        }

        return endDate;
    }

    private String getJobStatus(JobKey jobKey) throws SchedulerException {
        TriggerKey triggerKey = scheduler.getTriggersOfJob(jobKey).get(0).getKey();

        if (scheduler.getTriggerState(triggerKey) == Trigger.TriggerState.ERROR) {
            return JobBasicInfo.STATUS_ERROR;
        } else if (scheduler.getTriggerState(triggerKey) == Trigger.TriggerState.BLOCKED) {
            return JobBasicInfo.STATUS_BLOCKED;
        } else if (scheduler.getTriggerState(triggerKey) == Trigger.TriggerState.PAUSED) {
            return JobBasicInfo.STATUS_PAUSED;
        } else {
            return JobBasicInfo.STATUS_OK;
        }
    }

    private String getJobActivity(JobKey jobKey) throws SchedulerException {
        Trigger trigger = scheduler.getTriggersOfJob(jobKey).get(0);
        DateTime startDateTime = new DateTime(trigger.getStartTime());
        DateTime endDateTime = new DateTime(trigger.getEndTime());

        if (startDateTime.isAfterNow()) {
            return JobBasicInfo.ACTIVITY_NOTSTARTED;
        } else if (endDateTime.isBeforeNow()) {
            return  JobBasicInfo.ACTIVITY_FINISHED;
        } else {
            return JobBasicInfo.ACTIVITY_ACTIVE;
        }
    }

    private String getJobType(JobKey jobKey) throws SchedulerException {
        if (jobKey.getName().endsWith(RunOnceJobId.SUFFIX_RUNONCEJOBID)) {
            return JobBasicInfo.JOBTYPE_RUNONCE;
        } else if (jobKey.getName().endsWith(RepeatingJobId.SUFFIX_REPEATJOBID)) {
            return JobBasicInfo.JOBTYPE_REPEATING;
        } else {
            return JobBasicInfo.JOBTYPE_CRON;
        }
    }

    private String getJobInfo(JobKey jobKey, String jobType) throws SchedulerException {
        Trigger trigger = scheduler.getTriggersOfJob(jobKey).get(0);

        if (jobType.equals(JobBasicInfo.JOBTYPE_REPEATING)) {
            Integer timesTriggered = 0;
            String repeatMaxCount = "-";

            if (trigger instanceof CalendarIntervalTrigger) {
                CalendarIntervalTrigger calendarIntervalTrigger = (CalendarIntervalTrigger) trigger;

                timesTriggered = calendarIntervalTrigger.getTimesTriggered();
            } else if (trigger instanceof SimpleTrigger) {
                SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;

                timesTriggered = simpleTrigger.getTimesTriggered();
            }

            if (trigger.getEndTime() != null) {
                repeatMaxCount = Integer.toString(TriggerUtils.computeFireTimesBetween(
                        (OperableTrigger) trigger, null, trigger.getStartTime(), trigger.getEndTime()
                ).size() + timesTriggered);
            }

            return String.format("%d/%s", timesTriggered, repeatMaxCount);
        } else if (jobType.equals(JobBasicInfo.JOBTYPE_CRON)) {
            CronScheduleBuilder cronScheduleBuilder = (CronScheduleBuilder) trigger.getScheduleBuilder();

            CronTrigger cronTrigger = (CronTrigger) cronScheduleBuilder.build();

            return cronTrigger.getCronExpression();
        } else {
            return "-";
        }
    }

    protected void assertArgumentNotNull(String objectName, Object object) {
        if (object == null) {
            String message = String.format("%s cannot be null", objectName);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    protected void logObjectIfNotNull(Object obj) {
        if (logger.isDebugEnabled() && obj != null) {
            logger.debug(obj.toString());
        }
    }
}
