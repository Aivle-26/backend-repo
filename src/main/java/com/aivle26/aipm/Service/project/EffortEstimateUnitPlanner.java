package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class EffortEstimateUnitPlanner {

    private static final int SMALL_TASK_HOURS = 8;
    private static final int MAX_PACKAGE_HOURS = 80;

    Plan plan(
            List<ProjectWbsTask> confirmedTasks,
            Map<Long, ProjectSchedule> schedules,
            Map<Long, ProjectTaskAssignment> assignments
    ) {
        Set<Long> parentIds = confirmedTasks.stream()
                .map(ProjectWbsTask::getParentTask)
                .filter(parent -> parent != null)
                .map(ProjectWbsTask::getId)
                .collect(Collectors.toSet());
        List<ProjectWbsTask> leaves = confirmedTasks.stream()
                .filter(task -> !parentIds.contains(task.getId()))
                .sorted(taskOrder())
                .toList();

        Map<Long, List<ProjectWbsTask>> leavesByParent = leaves.stream()
                .filter(task -> task.getParentTask() != null)
                .collect(Collectors.groupingBy(
                        task -> task.getParentTask().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Set<Long> groupedLeafIds = new HashSet<>();
        List<Unit> units = new ArrayList<>();

        for (List<ProjectWbsTask> siblings : leavesByParent.values()) {
            if (canGroup(siblings, confirmedTasks, assignments)) {
                units.add(toPackageUnit(siblings, schedules, assignments));
                siblings.forEach(task -> groupedLeafIds.add(task.getId()));
            }
        }
        leaves.stream()
                .filter(task -> !groupedLeafIds.contains(task.getId()))
                .map(task -> toTaskUnit(task, schedules, assignments))
                .forEach(units::add);
        units.sort(Comparator.comparingInt(Unit::orderIndex).thenComparing(Unit::anchorWbsId));
        validateCoverage(leaves, units);
        return new Plan(List.copyOf(leaves), List.copyOf(units));
    }

    private boolean canGroup(
            List<ProjectWbsTask> siblings,
            List<ProjectWbsTask> allTasks,
            Map<Long, ProjectTaskAssignment> assignments
    ) {
        if (siblings.size() < 2) {
            return false;
        }
        ProjectWbsTask parent = siblings.get(0).getParentTask();
        long directChildCount = allTasks.stream()
                .filter(task -> task.getParentTask() != null && parent.getId().equals(task.getParentTask().getId()))
                .count();
        if (directChildCount != siblings.size()) {
            return false;
        }
        Set<String> assignees = siblings.stream()
                .map(task -> assignments.get(task.getId()))
                .filter(assignment -> assignment != null)
                .map(ProjectTaskAssignment::getEmployeeNumber)
                .collect(Collectors.toSet());
        int totalHours = siblings.stream().mapToInt(ProjectWbsTask::getEstimatedHours).sum();
        return assignees.size() == 1
                && siblings.stream().allMatch(task -> assignments.containsKey(task.getId()))
                && siblings.stream().allMatch(task -> task.getEstimatedHours() < SMALL_TASK_HOURS)
                && totalHours <= MAX_PACKAGE_HOURS;
    }

    private Unit toPackageUnit(
            List<ProjectWbsTask> tasks,
            Map<Long, ProjectSchedule> schedules,
            Map<Long, ProjectTaskAssignment> assignments
    ) {
        ProjectWbsTask parent = tasks.get(0).getParentTask();
        List<Long> sourceIds = tasks.stream().map(ProjectWbsTask::getId).toList();
        String description = tasks.stream()
                .map(task -> task.getTaskName() + ": " + task.getDescription())
                .collect(Collectors.joining("\n"));
        return new Unit(
                parent.getId(), parent.getParentTask() == null ? null : parent.getParentTask().getId(),
                levelOf(parent), "WORK_PACKAGE", parent.getId(), parent.getTaskName(),
                "PACKAGE-" + parent.getId(), sourceIds, parent.getTaskName(), description,
                minStart(sourceIds, schedules), maxEnd(sourceIds, schedules),
                assignments.get(tasks.get(0).getId()).getEmployeeNumber(), parent.getOrderIndex()
        );
    }

    private Unit toTaskUnit(
            ProjectWbsTask task,
            Map<Long, ProjectSchedule> schedules,
            Map<Long, ProjectTaskAssignment> assignments
    ) {
        ProjectWbsTask workPackage = nearestWorkPackage(task);
        ProjectSchedule schedule = schedules.get(task.getId());
        return new Unit(
                task.getId(), task.getParentTask() == null ? null : task.getParentTask().getId(),
                levelOf(task), "TASK", workPackage == null ? null : workPackage.getId(),
                workPackage == null ? null : workPackage.getTaskName(), "WBS-" + task.getId(),
                List.of(task.getId()), task.getTaskName(), task.getDescription(),
                schedule == null ? null : schedule.getStartDate(), schedule == null ? null : schedule.getEndDate(),
                assignments.get(task.getId()).getEmployeeNumber(), task.getOrderIndex()
        );
    }

    private ProjectWbsTask nearestWorkPackage(ProjectWbsTask task) {
        return task.getParentTask();
    }

    private int levelOf(ProjectWbsTask task) {
        int level = 1;
        ProjectWbsTask current = task.getParentTask();
        while (current != null) {
            level++;
            current = current.getParentTask();
        }
        return Math.min(level, 3);
    }

    private LocalDate minStart(List<Long> sourceIds, Map<Long, ProjectSchedule> schedules) {
        return sourceIds.stream().map(schedules::get).filter(schedule -> schedule != null)
                .map(ProjectSchedule::getStartDate).min(LocalDate::compareTo).orElse(null);
    }

    private LocalDate maxEnd(List<Long> sourceIds, Map<Long, ProjectSchedule> schedules) {
        return sourceIds.stream().map(schedules::get).filter(schedule -> schedule != null)
                .map(ProjectSchedule::getEndDate).max(LocalDate::compareTo).orElse(null);
    }

    private void validateCoverage(List<ProjectWbsTask> leaves, List<Unit> units) {
        Map<Long, Integer> counts = new HashMap<>();
        units.forEach(unit -> unit.sourceWbsIds().forEach(id -> counts.merge(id, 1, Integer::sum)));
        Set<Long> expected = leaves.stream().map(ProjectWbsTask::getId).collect(Collectors.toSet());
        if (!counts.keySet().equals(expected) || counts.values().stream().anyMatch(count -> count != 1)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_EFFORT_ESTIMATE_UNITS",
                    "공수 산정 단위에 누락되거나 중복된 WBS가 있습니다."
            );
        }
    }

    private Comparator<ProjectWbsTask> taskOrder() {
        return Comparator.comparingInt(ProjectWbsTask::getOrderIndex).thenComparing(ProjectWbsTask::getId);
    }

    record Plan(List<ProjectWbsTask> leafTasks, List<Unit> units) {
    }

    record Unit(
            Long anchorWbsId,
            Long parentWbsId,
            int level,
            String itemType,
            Long workPackageId,
            String workPackageName,
            String estimateUnitId,
            List<Long> sourceWbsIds,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            String employeeNumber,
            int orderIndex
    ) {
    }
}
