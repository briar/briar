package org.briarproject.briar.android.reporting;

import android.content.Context;

import org.acra.config.ACRAConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;
import org.briarproject.briar.android.BriarApplication;

import androidx.annotation.NonNull;

public class BriarReportSenderFactory implements ReportSenderFactory {

	@NonNull
	@Override
	public ReportSender create(@NonNull Context ctx,
			@NonNull ACRAConfiguration config) {
		// ACRA passes in the Application as context
		BriarApplication app = (BriarApplication) ctx;
		return new BriarReportSender(app.getApplicationComponent());
	}
}
