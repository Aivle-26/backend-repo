package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EffortEstimateUnitPlannerTest {

    private final EffortEstimateUnitPlanner planner = new EffortEstimateUnitPlanner();

    @Test
    void groupsSmallSiblingTasksOnlyOnceWhenTheyHaveTheSameAssignee() {
        ProjectWbsTask parent = task(10L, null, 20, 1);
        ProjectWbsTask first = task(11L, parent, 2, 2);
        ProjectWbsTask second = task(12L, parent, 3, 3);

        EffortEstimateUnitPlanner.Plan plan = planner.plan(
                List.of(parent, first, second),
                Map.of(),
                Map.of(11L, assignment(first, "STAFF-1"), 12L, assignment(second, "STAFF-1"))
        );

        assertThat(plan.units()).hasSize(1);
        assertThat(plan.units().get(0).itemType()).isEqualTo("WORK_PACKAGE");
        assertThat(plan.units().get(0).sourceWbsIds()).containsExactly(11L, 12L);
    }

    @Test
    void keepsTasksSeparateWhenAssigneesDiffer() {
        ProjectWbsTask parent = task(20L, null, 20, 1);
        ProjectWbsTask first = task(21L, parent, 2, 2);
        ProjectWbsTask second = task(22L, parent, 3, 3);

        EffortEstimateUnitPlanner.Plan plan = planner.plan(
                List.of(parent, first, second),
                Map.of(),
                Map.of(21L, assignment(first, "STAFF-1"), 22L, assignment(second, "STAFF-2"))
        );

        assertThat(plan.units()).hasSize(2);
        assertThat(plan.units()).allMatch(unit -> unit.itemType().equals("TASK"));
        assertThat(plan.units()).flatExtracting(EffortEstimateUnitPlanner.Unit::sourceWbsIds)
                .containsExactly(21L, 22L);
    }

    private ProjectWbsTask task(Long id, ProjectWbsTask parent, int estimatedHours, int orderIndex) {
        ProjectWbsTask task = new ProjectWbsTask();
        task.setId(id);
        task.setParentTask(parent);
        task.setTaskName("WBS " + id);
        task.setDescription("description " + id);
        task.setEstimatedHours(estimatedHours);
        task.setOrderIndex(orderIndex);
        task.setConfirmed(true);
        return task;
    }

    private ProjectTaskAssignment assignment(ProjectWbsTask task, String employeeNumber) {
        ProjectTaskAssignment assignment = new ProjectTaskAssignment();
        assignment.setWbsTask(task);
        assignment.setEmployeeNumber(employeeNumber);
        return assignment;
    }
}
