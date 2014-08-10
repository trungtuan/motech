package org.motechproject.scheduler.service;

import org.motechproject.event.MotechEvent;
import org.motechproject.scheduler.contract.CronSchedulableJob;
import org.motechproject.scheduler.contract.DayOfWeekSchedulableJob;
import org.motechproject.scheduler.contract.JobId;
import org.motechproject.scheduler.contract.RepeatingSchedulableJob;
import org.motechproject.scheduler.contract.RunOnceSchedulableJob;
import org.motechproject.scheduler.contract.JobBasicInfo;
import org.motechproject.scheduler.contract.JobDetailedInfo;

import java.util.Date;
import java.util.List;

/**
 * \defgroup scheduler Scheduler
 */

/**
 * \ingroup scheduler
 * Motech Scheduler Service Interface provides methods to schedule reschedule and unschedule a job
 *
 * Set a global policy that determines trigger fire behaviour for misfired triggers.
 * For details see quartz documentations for misfire policy
 *
 * do_nothing -> @see CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING
 * fire_once_now -> @see CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW
 * ignore -> @see CronTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY
 *
 * fire_now -> @see SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW
 * ignore -> @see SimpleTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY
 * reschedule_next_with_existing_count -> @see SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT
 * reschedule_next_with_remaining_count -> @see SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT
 * reschedule_now_with_existing_count -> @see SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT
 * reschedule_now_with_remaining_count -> @see SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT
 *
 * @author Igor (iopushnyev@2paths.com)
 * Date: 16/02/11
 *
 */
public interface MotechSchedulerService {
    String JOB_ID_KEY = "JobID";

    /**
     * Schedules the given schedulable job. The Job ID by which the job will be referencing in the future should be provided
     * in an Instance of MotechEvent in SchedulableJob (see MotechEvent.jobId)
     *
     * If a job with the same job ID as the given exists, this job will be unscheduled and the given schedulable job will be scheduled
     *
     * @param cronSchedulableJob
     */
    void scheduleJob(CronSchedulableJob cronSchedulableJob);

    /**
     * Same as scheduleJob, except that it would update existing job if one exists instead of creating a new one
     *
     * @param cronSchedulableJob
     */
    void safeScheduleJob(CronSchedulableJob cronSchedulableJob);

    /**
     * Updates MotechEvent data of the job defined by jobIb in the given instance of that class
     *
     * @param motechEvent
     */
    @Deprecated // nobody's using it and can't imagine a case for this
    void updateScheduledJob(MotechEvent motechEvent);

    /**
     * Reschedules a job with the given job ID to be fired according to the given Cron Expression
     *
     * Previous version of the configured Motech Scheduled Even that will be created when the job is fired remains us it was
     * @param subject
     * @param externalId
     * @param cronExpression
     */
    void rescheduleJob(String subject, String externalId, String cronExpression);

    /**
     * Schedules the given schedulable job. The Job ID by which the job will be referencing in the future should be provided
     * in an Instance of MotechEvent in SchedulableJob (see MotechEvent.jobId)
     *
     * If a job with the same job ID as the given exists, this job will be unscheduled and the given schedulable job will be scheduled
     *
     * @param repeatingSchedulableJob
     */
    void scheduleRepeatingJob(RepeatingSchedulableJob repeatingSchedulableJob);

    /**
     * Same as safeScheduleRepeatingJob with intervening = true
     * @param repeatingSchedulableJob
     */
    void safeScheduleRepeatingJob(RepeatingSchedulableJob repeatingSchedulableJob);

    void scheduleRunOnceJob(RunOnceSchedulableJob schedulableJob);

    /**
     * Same as scheduleRunOnceJob, except that it would update existing job if one exists instead of creating a new one
     * @param schedulableJob
     */
    void safeScheduleRunOnceJob(RunOnceSchedulableJob schedulableJob);

    /**
     * Same as safeScheduleDayOfWeekJob with intervening = true
     * @param dayOfWeekSchedulableJob
     */
    void scheduleDayOfWeekJob(DayOfWeekSchedulableJob dayOfWeekSchedulableJob);

    /**
     * Unschedules a job with the given job ID
     *  @param subject : String representing domain operation eg. "pill-reminder", "outbox-call" or motechEvent.getSubject()
     * @param externalId  : domain specific id as String.
     */
    void unscheduleJob(String subject, String externalId);

    void unscheduleJob(JobId job);

    /**
     * Same as unscheduleJob except that it would not throw an exception if the job doesn't exist
     * @param subject
     * @param externalId
     */
    void safeUnscheduleJob(String subject, String externalId);

    void unscheduleAllJobs(String jobIdPrefix);

    void safeUnscheduleAllJobs(String jobIdPrefix);

    void unscheduleRepeatingJob(String subject, String externalId);

    /**
     * Same as unscheduleRepeatingJob except that it would not throw an exception if the job doesn't exist
     * @param subject
     * @param externalId
     */
    void safeUnscheduleRepeatingJob(String subject, String externalId);

    /**
     * Unschedules a run once job with the given job ID
     *  @param subject : String representing domain operation eg. "pill-reminder", "outbox-call" or motechEvent.getSubject()
     * @param externalId  : domain specific id as String.
     */
    void unscheduleRunOnceJob(String subject, String externalId);

    /**
     * Same as unscheduleRunOnceJob except that it would not throw an exception if the job doesn't exist
     * @param subject
     * @param externalId
     */
    void safeUnscheduleRunOnceJob(String subject, String externalId);

    List<Date> getScheduledJobTimings(String subject, String externalJobId, Date startDate, Date endDate);

    List<Date> getScheduledJobTimingsWithPrefix(String subject, String externalJobIdPrefix, Date startDate, Date endDate);

    List<JobBasicInfo> getScheduledJobsBasicInfo();

    JobDetailedInfo getScheduledJobDetailedInfo(JobBasicInfo jobBasicInfo);
}
