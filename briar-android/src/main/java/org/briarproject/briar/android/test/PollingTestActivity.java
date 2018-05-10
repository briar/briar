package org.briarproject.briar.android.test;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.Button;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.event.TransportDisabledEvent;
import org.briarproject.bramble.api.plugin.event.TransportEnabledEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.api.test.TestDataCreator;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import javax.inject.Inject;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;

public class PollingTestActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(PollingTestActivity.class.getName());

	// Add one contact after each round up to:
	private static int NUMBER_OF_CONTACTS = 10;
	// Download x times per #contacts
	private static int NUMBER_OF_DOWNLOADS = 1;
	// File size to download. One of (5,10,50,100,500)MB.
	private static int DOWNLOAD_SIZE = 10;
	// Time to wait between each round in ms.
	private static int PAUSE = 5000;
	// Socket timeout in ms
	private static int SO_TIMEOUT = 60000;

	@Inject
	TestDataCreator testDataCreator;
	@Inject
	AndroidExecutor executor;
	@Inject
	EventBus eventBus;

	GraphView graphView;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
		setContentView(R.layout.activity_test_polling);
		Button button = findViewById(R.id.run_test_button);
		button.setOnClickListener(v -> {
			runTest();
		});
		graphView = findViewById(R.id.graph);
		graphView.getGridLabelRenderer()
				.setHorizontalAxisTitle("Number of contacts");
		graphView.getGridLabelRenderer().setVerticalAxisTitle("MBit/s");
		graphView.getViewport().setXAxisBoundsManual(true);
		graphView.getViewport().setMinX(0);
		graphView.getViewport().setMaxX(NUMBER_OF_CONTACTS);
	}

	private void runTest() {
		executor.runOnBackgroundThread(() -> {
			LineGraphSeries<DataPoint> series = new LineGraphSeries<>();
			graphView.addSeries(series);
			for (int i = 0; i < NUMBER_OF_CONTACTS; i++) {
				if (i != 0) {
					createContact();
					// Wait to let the previous round of polling settle.
					synchronized (this) {
						try {
							wait(PAUSE);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				// Reset BT polling.
				eventBus.broadcast(new TransportDisabledEvent(
						BluetoothConstants.ID));
				eventBus.broadcast(
						new TransportEnabledEvent(BluetoothConstants.ID));
				LOG.info("\n###########\nRound " + i + " (Contacts)");
				double y = run();
				series.appendData(new DataPoint(i, y), false,
						NUMBER_OF_CONTACTS);
			}
		});
	}

	private double run() {
		SpeedTestReport reports[] = new SpeedTestReport[3];
		double average = 0;
		for (int i = 0; i < NUMBER_OF_DOWNLOADS; i++) {
			final CountDownLatch cl = new CountDownLatch(1);
			SpeedTestSocket speedTestSocket = new SpeedTestSocket();
			speedTestSocket.setSocketTimeout(SO_TIMEOUT);
			int finalI = i;
			speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
				@Override
				public void onCompletion(SpeedTestReport report) {
					LOG.info("[COMPLETED]: rate in bit/s : " +
							report.getTransferRateBit() + " in " +
							(report.getReportTime() -
									report.getStartTime()) + "ms");
					reports[finalI] = report;
					cl.countDown();
				}

				@Override
				public void onProgress(float percent,
						SpeedTestReport report) {
					/*	LOG.info("[PROGRESS] rate in bit/s   : " +
							report.getTransferRateBit());
							*/
				}

				@Override
				public void onError(SpeedTestError speedTestError,
						String errorMessage) {
					LOG.info("Error: " + speedTestError.name() + " : " +
							errorMessage);

				}
			});
			LOG.info("Download " + (i + 1) + " of " + NUMBER_OF_DOWNLOADS);
			speedTestSocket
					.startDownload(
							"http://ikoula.testdebit.info/" + DOWNLOAD_SIZE +
									"M.iso");
			try {
				cl.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			average += reports[i].getTransferRateBit().doubleValue() / 1000000;
		}
		return average / NUMBER_OF_DOWNLOADS;
	}

	private void createContact() {
		testDataCreator.createTestData(1, 0, 0, 0, 0);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return false;
	}
}
