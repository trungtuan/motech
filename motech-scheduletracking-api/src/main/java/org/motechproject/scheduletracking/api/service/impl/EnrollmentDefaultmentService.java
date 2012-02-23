package org.motechproject.scheduletracking.api.service.impl;

import org.joda.time.LocalDate;
import org.motechproject.model.MotechEvent;
import org.motechproject.model.RunOnceSchedulableJob;
import org.motechproject.scheduler.MotechSchedulerService;
import org.motechproject.scheduletracking.api.domain.Enrollment;
import org.motechproject.scheduletracking.api.domain.Milestone;
import org.motechproject.scheduletracking.api.domain.Schedule;
import org.motechproject.scheduletracking.api.events.DefaultmentCaptureEvent;
import org.motechproject.scheduletracking.api.events.constants.EventSubjects;
import org.motechproject.scheduletracking.api.repository.AllTrackedSchedules;
import org.motechproject.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentDefaultmentService {
    public static final String JOB_ID_PREFIX = "defaultment.capture";

    private AllTrackedSchedules allTrackedSchedules;
    private MotechSchedulerService schedulerService;

    @Autowired
    public EnrollmentDefaultmentService(AllTrackedSchedules allTrackedSchedules, MotechSchedulerService schedulerService) {
        this.allTrackedSchedules = allTrackedSchedules;
        this.schedulerService = schedulerService;
    }

    public void scheduleJobToCaptureDefaultment(Enrollment enrollment) {
        Schedule schedule = allTrackedSchedules.getByName(enrollment.getScheduleName());
        Milestone currentMilestone = schedule.getMilestone(enrollment.getCurrentMilestoneName());
        if (currentMilestone == null)
            return;
        LocalDate currentMilestoneStartDate = enrollment.getCurrentMilestoneStartDate(schedule.getFirstMilestone().getName());
        LocalDate milestoneEndDate = currentMilestoneStartDate.plusDays(currentMilestone.getMaximumDurationInDays());

        if (milestoneEndDate.isBefore(DateUtil.today()))
            return;

        MotechEvent event = new DefaultmentCaptureEvent(enrollment.getId(), enrollment.getId()).toMotechEvent();
        schedulerService.safeScheduleRunOnceJob(new RunOnceSchedulableJob(event, milestoneEndDate.toDate()));
    }

    public void unscheduleDefaultmentCaptureJob(Enrollment enrollment) {
        schedulerService.safeUnscheduleAllJobs(String.format("%s-%s", EventSubjects.DEFAULTMENT_CAPTURE, enrollment.getId()));
    }
}
