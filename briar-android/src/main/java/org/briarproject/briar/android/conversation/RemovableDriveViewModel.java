package org.briarproject.briar.android.conversation;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveConstants;
import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask.State;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.file.FileConstants.PROP_PATH;

@NotNullByDefault
public class RemovableDriveViewModel extends AndroidViewModel {

	private static final Logger LOG =
			getLogger(RemovableDriveViewModel.class.getName());

	private final SimplexPlugin removableDrivePlugin;
	private RemovableDriveManager manager;

	@Inject
	public RemovableDriveViewModel(Application app,
			PluginManager pluginManager,
			RemovableDriveManager removableDriveManager) {
		super(app);

		this.removableDrivePlugin = (SimplexPlugin)
				pluginManager.getPlugin(RemovableDriveConstants.ID);
		this.manager = removableDriveManager;
	}

	LiveData<State> write(ContactId contactId, Uri uri) {
		// TODO create a tempfile for now, the task will be able to deal with Uris
		//  later when there is an android extension of AbstractRemovableDrivePlugin
		File f;
		try {
			f = File.createTempFile("sync", ".tmp");
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		if (f == null) {
			return null;
		}

		TransportProperties p = new TransportProperties();
		Log.d("FOO", "write path: " + f.getAbsolutePath());
		p.put(PROP_PATH, f.getAbsolutePath());

		RemovableDriveTask task = manager.startWriterTask(contactId, p);
		MutableLiveData<State> state = new MutableLiveData<>();
		task.addObserver((taskState) -> {
			state.postValue(taskState);
		});

		return state;
	}

	String getFileName() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",
				Locale.getDefault());
		return sdf.format(new Date());
	}
}
