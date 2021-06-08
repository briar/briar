package org.briarproject.briar.android.conversation;

import android.app.Application;
import android.net.Uri;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask.State;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.Locale.US;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.file.RemovableDriveConstants.PROP_URI;

@NotNullByDefault
public class RemovableDriveViewModel extends AndroidViewModel {

	private static final Logger LOG =
			getLogger(RemovableDriveViewModel.class.getName());

	private final RemovableDriveManager manager;

	private final ConcurrentHashMap<Consumer<State>, RemovableDriveTask>
			observers = new ConcurrentHashMap<>();

	@Inject
	RemovableDriveViewModel(Application app,
			RemovableDriveManager removableDriveManager) {
		super(app);

		this.manager = removableDriveManager;
	}

	String getFileName() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", US);
		return sdf.format(new Date());
	}


	LiveData<State> write(ContactId contactId, Uri uri) {
		TransportProperties p = new TransportProperties();
		p.put(PROP_URI, uri.toString());
		return observe(manager.startWriterTask(contactId, p));
	}

	LiveData<State> read(ContactId contactId, Uri uri) {
		TransportProperties p = new TransportProperties();
		p.put(PROP_URI, uri.toString());
		return observe(manager.startReaderTask(contactId, p));
	}

	@Nullable
	LiveData<State> ongoingWrite(ContactId contactId) {
		RemovableDriveTask task = manager.getCurrentWriterTask(contactId);
		if (task == null) {
			return null;
		}
		return observe(task);
	}

	@Nullable
	LiveData<State> ongoingRead(ContactId contactId) {
		RemovableDriveTask task = manager.getCurrentReaderTask(contactId);
		if (task == null) {
			return null;
		}
		return observe(task);
	}

	private LiveData<State> observe(RemovableDriveTask task) {
		MutableLiveData<State> state = new MutableLiveData<>();
		Consumer<State> observer = state::postValue;
		task.addObserver(observer);
		observers.put(observer, task);
		return state;
	}

	@Override
	protected void onCleared() {
		for (Map.Entry<Consumer<State>, RemovableDriveTask> entry
				: observers.entrySet()) {
			entry.getValue().removeObserver(entry.getKey());
		}
	}
}
