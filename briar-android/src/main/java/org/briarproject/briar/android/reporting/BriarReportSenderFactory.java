package org.briarproject.briar.android.reporting;

import android.content.Context;

import org.acra.config.ACRAConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.BriarApplication;

@NotNullByDefault
public class BriarReportSenderFactory implements ReportSenderFactory {

	@Override
	public ReportSender create(Context ctx, ACRAConfiguration config) {
		// ACRA passes in the Application as context
		BriarApplication app = (BriarApplication) ctx;
		return new BriarReportSender(app.getApplicationComponent());
	}
}
