package org.motechproject.tasks.web;

import org.motechproject.tasks.domain.TaskActivity;
import org.motechproject.tasks.service.TaskActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@Controller
public class ActivityController {
    private TaskActivityService activityService;

    @Autowired
    public ActivityController(final TaskActivityService activityService) {
        this.activityService = activityService;
    }

    @RequestMapping(value = "/activity", method = RequestMethod.GET)
    @ResponseBody
    public List<TaskActivity> getAllActivities() {
        return activityService.getAllActivities();
    }

    @RequestMapping(value = "/activity/{taskId}", method = RequestMethod.GET)
    @ResponseBody
    public List<TaskActivity> getTaskActivities(@PathVariable Long taskId) {
        return activityService.getTaskActivities(taskId);
    }

    @RequestMapping(value = "/activity/{taskId}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    public void deleteActivitiesForTask(@PathVariable Long taskId) {
        activityService.deleteActivitiesForTask(taskId);
    }
}
