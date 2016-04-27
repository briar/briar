package org.briarproject.android.util;

import android.content.Context;
import android.support.annotation.NonNull;

import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;
import org.acra.util.JSONReportBuilder;
import org.briarproject.android.AndroidComponent;
import org.briarproject.api.reporting.DevReporter;

import java.io.FileNotFoundException;

import javax.inject.Inject;

public class BriarReportSender implements ReportSender {

	private final AndroidComponent component;

	@Inject
	protected DevReporter reporter;

	public BriarReportSender(AndroidComponent component) {
		this.component = component;
	}

	@Override
	public void send(@NonNull Context context,
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
			reporter.encryptCrashReportToFile(
					AndroidUtils.getReportDir(context),
					errorContent.getProperty(ReportField.REPORT_ID),
					crashReport);
		} catch (FileNotFoundException e) {
			throw new ReportSenderException("Failed to encrypt report", e);
		}
	}
}
