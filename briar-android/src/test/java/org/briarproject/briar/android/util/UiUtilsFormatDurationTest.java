package org.briarproject.briar.android.util;

import android.content.Context;
import android.content.res.Resources;

import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.briar.R;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.briarproject.briar.android.util.UiUtils.formatDuration;

public class UiUtilsFormatDurationTest extends BrambleMockTestCase {

	private final Context ctx;
	private final Resources r;
	private final int strHours = R.plurals.duration_hours;
	private final int strDays = R.plurals.duration_days;

	public UiUtilsFormatDurationTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		ctx = context.mock(Context.class);
		r = context.mock(Resources.class);
	}

	@Test
	public void testOneHour() {
		expectHourString(1);
		formatDuration(ctx, HOURS.toMillis(1));
	}

	@Test
	public void testOneDay() {
		expectDayString(1);
		formatDuration(ctx, DAYS.toMillis(1));
	}

	@Test
	public void test10Minutes() {
		expectHourString(1);
		formatDuration(ctx, MINUTES.toMillis(10));
	}

	@Test
	public void testSevenDays() {
		expectDayString(7);
		formatDuration(ctx, DAYS.toMillis(7));
	}

	@Test
	public void testSevenDays2Hours() {
		expectDayString(7);
		expectHourString(2);
		formatDuration(ctx, DAYS.toMillis(7) + HOURS.toMillis(2));
	}

	@Test
	public void testSevenDays20Minutes() {
		expectDayString(7);
		formatDuration(ctx, DAYS.toMillis(7) + MINUTES.toMillis(20));
	}

	@Test
	public void testSevenDays40Minutes() {
		expectDayString(7);
		expectHourString(1);
		formatDuration(ctx, DAYS.toMillis(7) + MINUTES.toMillis(40));
	}

	@Test
	public void testTwoDays11Hours() {
		expectDayString(2);
		expectHourString(11);
		formatDuration(ctx, DAYS.toMillis(2) + HOURS.toMillis(11));
	}

	@Test
	public void testTwoDays12Hours() {
		expectDayString(2);
		expectHourString(12);
		formatDuration(ctx, DAYS.toMillis(2) + HOURS.toMillis(12));
	}

	@Test
	public void testTwoDays13Hours() {
		expectDayString(2);
		expectHourString(13);
		formatDuration(ctx, DAYS.toMillis(2) + HOURS.toMillis(13));
	}

	private void expectHourString(int hours) {
		context.checking(new Expectations() {{
			oneOf(ctx).getResources();
			will(returnValue(r));
			oneOf(r).getQuantityString(strHours, hours, hours);
		}});
	}

	private void expectDayString(int days) {
		context.checking(new Expectations() {{
			oneOf(ctx).getResources();
			will(returnValue(r));
			oneOf(r).getQuantityString(strDays, days, days);
		}});
	}

}
