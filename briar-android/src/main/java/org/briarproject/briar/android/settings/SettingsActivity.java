package org.briarproject.briar.android.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.api.logging.PersistentLogManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;

import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_TITLE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static android.os.Environment.getExternalStoragePublicDirectory;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_EXPORT_LOG;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_EXPORT_OLD_LOG;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsActivity extends BriarActivity {

	private static final Logger LOG =
			getLogger(SettingsActivity.class.getName());

	private static final String LOG_EXPORT_FILENAME = "briar-log.txt";

	@Inject
	@IoExecutor
	Executor ioExecutor;

	@Inject
	PersistentLogManager logManager;

	@Override
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_settings);
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

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_EXPORT_LOG && result == RESULT_OK &&
				data != null && data.getData() != null) {
			exportLog(false, data.getData());
		} else if (request == REQUEST_EXPORT_OLD_LOG && result == RESULT_OK &&
				data != null && data.getData() != null) {
			exportLog(true, data.getData());
		}
	}

	private void exportLog(boolean old, Uri uri) {
		copyLog(old, () -> getOutputStream(uri));
	}

	private void copyLog(boolean old, OutputStreamProvider osp) {
		ioExecutor.execute(() -> {
			try {
				PrintWriter w = new PrintWriter(osp.getOutputStream());
				File logDir = getApplication().getDir("log", MODE_PRIVATE);
				for (String line : logManager.getPersistedLog(logDir, old)) {
					w.println(line);
				}
				w.close();
				runOnUiThreadUnlessDestroyed(() ->
						Toast.makeText(getApplication(), "Log exported",
								LENGTH_LONG).show());
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				runOnUiThreadUnlessDestroyed(() ->
						Toast.makeText(getApplication(), "Failed to export log",
								LENGTH_LONG).show());
			}
		});
	}

	void onExportLogClick(boolean old) {
		if (SDK_INT >= 19) {
			Intent intent = getExportLogIntent();
			int request = old ? REQUEST_EXPORT_OLD_LOG : REQUEST_EXPORT_LOG;
			startActivityForResult(intent, request);
		} else {
			exportLog(old);
		}
	}

	@RequiresApi(api = 19)
	private Intent getExportLogIntent() {
		Intent intent = new Intent(ACTION_CREATE_DOCUMENT);
		intent.addCategory(CATEGORY_OPENABLE);
		intent.setType("text/plain");
		intent.putExtra(EXTRA_TITLE, LOG_EXPORT_FILENAME);
		return intent;
	}

	private void exportLog(boolean old) {
		File file = getLogOutputFile();
		copyLog(old, () -> getOutputStream(file));
	}

	private File getLogOutputFile() {
		File path = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
		//noinspection ResultOfMethodCallIgnored
		path.mkdirs();
		return new File(path, LOG_EXPORT_FILENAME);
	}

	private OutputStream getOutputStream(File file) throws IOException {
		return new FileOutputStream(file);
	}

	private OutputStream getOutputStream(Uri uri) throws IOException {
		OutputStream os =
				getApplication().getContentResolver().openOutputStream(uri);
		if (os == null) throw new IOException();
		return os;
	}

	private interface OutputStreamProvider {
		OutputStream getOutputStream() throws IOException;
	}
}
