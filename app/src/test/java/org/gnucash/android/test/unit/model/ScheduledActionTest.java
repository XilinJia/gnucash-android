/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.test.unit.model;

import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Test scheduled actions
 */
public class ScheduledActionTest {

    @Test
    public void settingStartTime_shouldSetRecurrenceStart(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        long startTime = getTimeInMillis(2014, 8, 26);
        scheduledAction.setMStartTime(startTime);
        assertThat(scheduledAction.getMRecurrence()).isNull();

        Recurrence recurrence = new Recurrence(PeriodType.MONTH);
        assertThat(recurrence.getMPeriodStart().getTime()).isNotEqualTo(startTime);
        scheduledAction.setMRecurrence(recurrence);
        assertThat(recurrence.getMPeriodStart().getTime()).isEqualTo(startTime);

        long newStartTime = getTimeInMillis(2015, 6, 6);
        scheduledAction.setMStartTime(newStartTime);
        assertThat(recurrence.getMPeriodStart().getTime()).isEqualTo(newStartTime);
    }

    @Test
    public void settingEndTime_shouldSetRecurrenceEnd(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        long endTime = getTimeInMillis(2014, 8, 26);
        scheduledAction.setMEndDate(endTime);
        assertThat(scheduledAction.getMRecurrence()).isNull();

        Recurrence recurrence = new Recurrence(PeriodType.MONTH);
        assertThat(recurrence.getMPeriodEnd()).isNull();
        scheduledAction.setMRecurrence(recurrence);
        assertThat(recurrence.getMPeriodEnd().getTime()).isEqualTo(endTime);

        long newEndTime = getTimeInMillis(2015, 6, 6);
        scheduledAction.setMEndDate(newEndTime);
        assertThat(recurrence.getMPeriodEnd().getTime()).isEqualTo(newEndTime);
    }

    @Test
    public void settingRecurrence_shouldSetScheduledActionStartTime(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
        assertThat(scheduledAction.getMStartTime()).isEqualTo(0);

        long startTime = getTimeInMillis(2014, 8, 26);
        Recurrence recurrence = new Recurrence(PeriodType.WEEK);
        recurrence.setMPeriodStart(new Timestamp(startTime));
        scheduledAction.setMRecurrence(recurrence);
        assertThat(scheduledAction.getMStartTime()).isEqualTo(startTime);
    }

    @Test
    public void settingRecurrence_shouldSetEndTime(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
        assertThat(scheduledAction.getMStartTime()).isEqualTo(0);

        long endTime = getTimeInMillis(2017, 8, 26);
        Recurrence recurrence = new Recurrence(PeriodType.WEEK);
        recurrence.setMPeriodEnd(new Timestamp(endTime));
        scheduledAction.setMRecurrence(recurrence);

        assertThat(scheduledAction.getMEndDate()).isEqualTo(endTime);
    }

    /**
     * Checks that scheduled actions accurately compute the next run time based on the start date
     * and the last time the action was run
     */
    @Test
    public void testComputingNextScheduledExecution(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        PeriodType periodType = PeriodType.MONTH;

        Recurrence recurrence = new Recurrence(periodType);
        recurrence.setMMultiplier(2);
        DateTime startDate = new DateTime(2015, 8, 15, 12, 0);
        recurrence.setMPeriodStart(new Timestamp(startDate.getMillis()));
        scheduledAction.setMRecurrence(recurrence);

        assertThat(scheduledAction.computeNextCountBasedScheduledExecutionTime()).isEqualTo(startDate.getMillis());

        scheduledAction.setMExecutionCount(3);
        DateTime expectedTime = new DateTime(2016, 2, 15, 12, 0);
        assertThat(scheduledAction.computeNextCountBasedScheduledExecutionTime()).isEqualTo(expectedTime.getMillis());
    }

    @Test
    public void testComputingTimeOfLastSchedule(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        PeriodType periodType = PeriodType.WEEK;
        Recurrence recurrence = new Recurrence(periodType);
        recurrence.setMMultiplier(2);
        scheduledAction.setMRecurrence(recurrence);
        DateTime startDate = new DateTime(2016, 6, 6, 9, 0);
        scheduledAction.setMStartTime(startDate.getMillis());

        assertThat(scheduledAction.timeOfLastSchedule()).isEqualTo(-1L);

        scheduledAction.setMExecutionCount(3);
        DateTime expectedDate = new DateTime(2016, 7, 4, 9, 0);
        assertThat(scheduledAction.timeOfLastSchedule()).isEqualTo(expectedDate.getMillis());

    }

    /**
     * Weekly actions scheduled to run on multiple days of the week should be due
     * in each of them in the same week.
     *
     * For an action scheduled on Mondays and Thursdays, we test that, if
     * the last run was on Monday, the next should be due on the Thursday
     * of the same week instead of the following week.
     */
    @Test
    public void multiDayOfWeekWeeklyActions_shouldBeDueOnEachDayOfWeekSet() {
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
        Recurrence recurrence = new Recurrence(PeriodType.WEEK);
        recurrence.byDays(Arrays.asList(Calendar.MONDAY, Calendar.THURSDAY));
        scheduledAction.setMRecurrence(recurrence);
        scheduledAction.setMStartTime(new DateTime(2016, 6, 6, 9, 0).getMillis());
        scheduledAction.setMLastRun(new DateTime(2017, 4, 17, 9, 0).getMillis()); // Monday

        long expectedNextDueDate = new DateTime(2017, 4, 20, 9, 0).getMillis(); // Thursday
        assertThat(scheduledAction.computeNextTimeBasedScheduledExecutionTime())
                .isEqualTo(expectedNextDueDate);
    }

    /**
     * Weekly actions scheduled with multiplier should skip intermediate
     * weeks and be due in the specified day of the week.
     */
    @Test
    public void weeklyActionsWithMultiplier_shouldBeDueOnTheDayOfWeekSet() {
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
        Recurrence recurrence = new Recurrence(PeriodType.WEEK);
        recurrence.setMMultiplier(2);
        recurrence.byDays(Collections.singletonList(Calendar.WEDNESDAY));
        scheduledAction.setMRecurrence(recurrence);
        scheduledAction.setMStartTime(new DateTime(2016, 6, 6, 9, 0).getMillis());
        scheduledAction.setMLastRun(new DateTime(2017, 4, 12, 9, 0).getMillis()); // Wednesday

        // Wednesday, 2 weeks after the last run
        long expectedNextDueDate = new DateTime(2017, 4, 26, 9, 0).getMillis();
        assertThat(scheduledAction.computeNextTimeBasedScheduledExecutionTime())
                .isEqualTo(expectedNextDueDate);
    }

    /**
     * Weekly actions should return a date in the future when no
     * days of the week have been set in the recurrence.
     *
     * See ScheduledAction.computeNextTimeBasedScheduledExecutionTime()
     */
    @Test
    public void weeklyActionsWithoutDayOfWeekSet_shouldReturnDateInTheFuture() {
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
        Recurrence recurrence = new Recurrence(PeriodType.WEEK);
        recurrence.byDays(Collections.<Integer>emptyList());
        scheduledAction.setMRecurrence(recurrence);
        scheduledAction.setMStartTime(new DateTime(2016, 6, 6, 9, 0).getMillis());
        scheduledAction.setMLastRun(new DateTime(2017, 4, 12, 9, 0).getMillis());

        long now = LocalDateTime.now().toDate().getTime();
        assertThat(scheduledAction.computeNextTimeBasedScheduledExecutionTime()).isGreaterThan(now);
    }

    private long getTimeInMillis(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day);
        return calendar.getTimeInMillis();
    }

    //todo add test for computing the scheduledaction endtime from the recurrence count
}
