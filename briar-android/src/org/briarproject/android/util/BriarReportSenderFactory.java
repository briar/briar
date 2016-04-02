package org.briarproject.android.util;

import android.content.Context;
import android.support.annotation.NonNull;

import org.acra.config.ACRAConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;
import org.briarproject.android.BriarApplication;

public class BriarReportSenderFactory implements ReportSenderFactory {
	@NonNull
	@Override
	public ReportSender create(@NonNull Context context,
			@NonNull ACRAConfiguration config) {
		// ACRA passes in the Application as context
		return new BriarReportSender(
				((BriarApplication) context).getApplicationComponent());
	}
}
