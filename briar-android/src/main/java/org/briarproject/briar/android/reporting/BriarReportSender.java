package org.briarproject.briar.android.reporting;

import android.content.Context;
import android.support.annotation.NonNull;

import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;
import org.acra.util.JSONReportBuilder;
import org.briarproject.bramble.api.reporting.DevReporter;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.briar.android.AndroidComponent;

import java.io.File;
import java.io.FileNotFoundException;

import javax.inject.Inject;

import static org.acra.ReportField.REPORT_ID;

public class BriarReportSender implements ReportSender {

	private final AndroidComponent component;

	@Inject
	DevReporter reporter;

	BriarReportSender(AndroidComponent component) {
		this.component = component;
	}

	@Override
	public void send(@NonNull Context ctx,
			@NonNull CrashReportData errorContent)
			throws ReportSenderException {
		component.inject(this);
		String crashReport;
		try {
			crashReport = errorContent.toJSON().toString();
		} catch (JSONReportBuilder.JSONReportException e) {
			throw new ReportSenderException("Couldn't create JSON", e);
		}
		try {
			File reportDir = AndroidUtils.getReportDir(ctx);
			String reportId = errorContent.getProperty(REPORT_ID);
			reporter.encryptReportToFile(reportDir, reportId, crashReport);
		} catch (FileNotFoundException e) {
			throw new ReportSenderException("Failed to encrypt report", e);
		}
	}
}
