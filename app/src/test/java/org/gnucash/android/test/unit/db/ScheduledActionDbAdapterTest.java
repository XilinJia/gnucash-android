package org.gnucash.android.test.unit.db;

import android.content.res.Resources;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the scheduled actions database adapter
 */
@RunWith(RobolectricTestRunner.class) //package is required so that resources can be found in dev mode
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class ScheduledActionDbAdapterTest {

    ScheduledActionDbAdapter mScheduledActionDbAdapter;

    @Before
    public void setUp(){
        mScheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();
    }

    public void shouldFetchOnlyEnabledScheduledActions(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        scheduledAction.setMRecurrence(new Recurrence(PeriodType.MONTH));
        scheduledAction.setMIsEnabled(false);

        mScheduledActionDbAdapter.addRecord(scheduledAction);

        scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        scheduledAction.setMRecurrence(new Recurrence(PeriodType.WEEK));
        mScheduledActionDbAdapter.addRecord(scheduledAction);

        assertThat(mScheduledActionDbAdapter.getAllRecords()).hasSize(2);

        List<ScheduledAction> enabledActions = mScheduledActionDbAdapter.getAllEnabledScheduledActions();
        assertThat(enabledActions).hasSize(1);
        assertThat(enabledActions.get(0).getMRecurrence().getMPeriodType()).isEqualTo(PeriodType.WEEK);
    }

    @Test(expected = NullPointerException.class) //no recurrence is set
    public void everyScheduledActionShouldHaveRecurrence(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        scheduledAction.setMActionUID(BaseModel.generateUID());
        mScheduledActionDbAdapter.addRecord(scheduledAction);
    }

    @Test
    public void testGenerateRepeatString(){
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        PeriodType periodType = PeriodType.MONTH;
        Recurrence recurrence = new Recurrence(periodType);
        recurrence.setMMultiplier(2);
        scheduledAction.setMRecurrence(recurrence);
        scheduledAction.setMTotalFrequency(4);
        Resources res = GnuCashApplication.getAppContext().getResources();
        String repeatString = res.getQuantityString(R.plurals.label_every_x_months, 2, 2) + ", " +
                res.getString(R.string.repeat_x_times, 4);

        assertThat(scheduledAction.repeatString().trim()).isEqualTo(repeatString);

    }

    @Test
    public void testAddGetRecord() {
        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
        scheduledAction.setMActionUID("Some UID");
        scheduledAction.setMAdvanceCreateDays(1);
        scheduledAction.setMAdvanceNotifyDays(2);
        scheduledAction.setMAutoCreate(true);
        scheduledAction.setMAutoNotify(true);
        scheduledAction.setMIsEnabled(true);
        scheduledAction.setMStartTime(11111);
        scheduledAction.setMEndDate(33333);
        scheduledAction.setMLastRun(22222);
        scheduledAction.setMExecutionCount(3);
        scheduledAction.setMRecurrence(new Recurrence(PeriodType.MONTH));
        scheduledAction.setMTag("QIF;SD_CARD;2016-06-25 12:56:07.175;false");
        mScheduledActionDbAdapter.addRecord(scheduledAction);

        ScheduledAction scheduledActionFromDb =
                mScheduledActionDbAdapter.getRecord(scheduledAction.getMUID());
        assertThat(scheduledActionFromDb.getMUID()).isEqualTo(
                scheduledAction.getMUID());
        assertThat(scheduledActionFromDb.getMActionUID()).isEqualTo(
                scheduledAction.getMActionUID());
        assertThat(scheduledActionFromDb.getMAdvanceCreateDays()).isEqualTo(
                scheduledAction.getMAdvanceCreateDays());
        assertThat(scheduledActionFromDb.getMAdvanceNotifyDays()).isEqualTo(
                scheduledAction.getMAdvanceNotifyDays());
        assertThat(scheduledActionFromDb.shouldAutoCreate()).isEqualTo(
                scheduledAction.shouldAutoCreate());
        assertThat(scheduledActionFromDb.shouldAutoNotify()).isEqualTo(
                scheduledAction.shouldAutoNotify());
        assertThat(scheduledActionFromDb.isEnabled()).isEqualTo(
                scheduledAction.isEnabled());
        assertThat(scheduledActionFromDb.getMStartTime()).isEqualTo(
                scheduledAction.getMStartTime());
        assertThat(scheduledActionFromDb.getMEndDate()).isEqualTo(
                scheduledAction.getMEndDate());
        assertThat(scheduledActionFromDb.getMLastRun()).isEqualTo(
                scheduledAction.getMLastRun());
        assertThat(scheduledActionFromDb.getMExecutionCount()).isEqualTo(
                scheduledAction.getMExecutionCount());
        assertThat(scheduledActionFromDb.getMRecurrence()).isEqualTo(
                scheduledAction.getMRecurrence());
        assertThat(scheduledActionFromDb.getMTag()).isEqualTo(
                scheduledAction.getMTag());
    }
}
