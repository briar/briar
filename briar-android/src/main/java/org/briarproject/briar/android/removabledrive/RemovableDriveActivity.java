package org.briarproject.briar.android.removabledrive;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask.State;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

// TODO 19 will be our requirement for sneakernet support, right. The file apis
//  used require this.
@RequiresApi(api = 19)
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RemovableDriveActivity extends BriarActivity
		implements BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	private RemovableDriveViewModel viewModel;
	private TextView text;
	private Button writeButton;
	private Button readButton;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(RemovableDriveViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_removable_drive);
		text = findViewById(R.id.sneaker_text);
		writeButton = findViewById(R.id.sneaker_write);
		readButton = findViewById(R.id.sneaker_read);

		Intent intent = getIntent();
		int contactId = intent.getIntExtra(CONTACT_ID, -1);
		if (contactId == -1) {
			writeButton.setEnabled(false);
			readButton.setEnabled(false);
			return;
		}

		// TODO we can pass an extra named DocumentsContract.EXTRA_INITIAL_URI
		//  to have the filepicker start on the usb-stick -- if get hold of URI
		//  of the same. USB manager API?
		//  Overall, passing this extra requires extending the ready-made
		//  contracts and overriding createIntent.

		writeButton.setText("Write for contactId " + contactId);
		ActivityResultLauncher<String> createDocument =
				registerForActivityResult(
						new ActivityResultContracts.CreateDocument(),
						uri -> write(contactId, uri));
		writeButton.setOnClickListener(
				v -> createDocument.launch(viewModel.getFileName()));

		readButton.setText("Read for contactId " + contactId);
		ActivityResultLauncher<String> getContent =
				registerForActivityResult(
						new ActivityResultContracts.GetContent(),
						uri -> read(contactId, uri));
		readButton.setOnClickListener(
				v -> getContent.launch("application/octet-stream"));

		LiveData<State> state;
		state = viewModel.ongoingWrite(new ContactId(contactId));
		if (state == null) {
			writeButton.setEnabled(true);
		} else {
			say("\nOngoing write:");
			writeButton.setEnabled(false);
			state.observe(this, (taskState) -> handleState("write", taskState));
		}
		state = viewModel.ongoingRead(new ContactId(contactId));
		if (state == null) {
			readButton.setEnabled(true);
		} else {
			say("\nOngoing read:");
			readButton.setEnabled(false);
			state.observe(this, (taskState) -> handleState("read", taskState));
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void write(int contactId, @Nullable Uri uri) {
		if (contactId == -1) {
			throw new IllegalStateException();
		}
		if (uri == null) {
			say("no URI picked for write");
			return;
		}
		say("\nWriting to URI: " + uri);
		writeButton.setEnabled(false);
		LiveData<State> state = viewModel.write(new ContactId(contactId), uri);
		state.observe(this, (taskState) -> handleState("write", taskState));
	}

	private void read(int contactId, @Nullable Uri uri) {
		if (contactId == -1) {
			throw new IllegalStateException();
		}
		if (uri == null) {
			say("no URI picked for read");
			return;
		}
		say("\nReading from URI: " + uri);
		readButton.setEnabled(false);
		LiveData<State> state = viewModel.read(new ContactId(contactId), uri);
		state.observe(this, (taskState) -> handleState("read", taskState));
	}

	private void handleState(String action, State taskState) {
		say(String.format(Locale.getDefault(),
				"%s: bytes done: %d of %d. %s. %s.",
				action, taskState.getDone(), taskState.getTotal(),
				taskState.isFinished() ? "Finished" : "Ongoing",
				taskState.isFinished() ?
						(taskState.isSuccess() ? "Success" : "Failed") : ".."));
		if (taskState.isFinished()) {
			if (action.equals("write")) {
				writeButton.setEnabled(true);
			} else if (action.equals("read")) {
				readButton.setEnabled(true);
			}
		}
	}

	private void say(String txt) {
		String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
				.format(new Date());
		txt = String.format("%s %s\n", time, txt);
		text.setText(text.getText().toString().concat(txt));
	}
}
