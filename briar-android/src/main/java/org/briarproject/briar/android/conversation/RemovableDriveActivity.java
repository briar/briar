package org.briarproject.briar.android.conversation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask.State;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import java.util.Locale;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import static android.app.Notification.EXTRA_TITLE;
import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_REMOVABLE_DRIVE_WRITE;
import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;


@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RemovableDriveActivity extends BriarActivity
		implements BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	private RemovableDriveViewModel viewModel;
	private TextView text;

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

		// if (savedInstanceState == null) {
		// 	showInitialFragment(RemovableDriveFragment.newInstance());
		// }

		text = findViewById(R.id.sneakertext);

		Intent intent = getIntent();
		int contactId = intent.getIntExtra(CONTACT_ID, -1);
		if (contactId == -1) {
			say("no specific contact");
		} else {
			say("contactId: " + contactId);
			askWrite(contactId);
		}
	}

	private void askWrite(int contactId) {
		if (SDK_INT >= 19) {
			Intent intent = getCreationIntent(contactId);
			// TODO should use registerForActivityResult?
			startActivityForResult(intent, REQUEST_REMOVABLE_DRIVE_WRITE);
		} else {
			// TODO we're only going to support 4.4+ right? so then no SDK_INT < 19
		}
	}

	@RequiresApi(api = 19)
	private Intent getCreationIntent(int contactId) {
		Intent intent = new Intent(ACTION_CREATE_DOCUMENT);
		intent.addCategory(CATEGORY_OPENABLE);
		intent.setType("application/octet-stream");
		// TODO eh, this doesn't put the filename in the save dialog
		intent.putExtra(EXTRA_TITLE, viewModel.getFileName());
		intent.putExtra(CONTACT_ID, contactId);
		return intent;
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_REMOVABLE_DRIVE_WRITE && result == RESULT_OK &&
				data != null) {
			// TODO can getData() be null?
			write(getIntent().getIntExtra(CONTACT_ID, -1), data.getData());
		}
	}

	private void write(int contactId, Uri uri) {
		if (contactId == -1) {
			throw new IllegalStateException();
		}
		say("uri: " + uri);

		LiveData<State> state = viewModel.write(new ContactId(contactId), uri);
		if (state == null) {
			say("fail start write");
			return;
		}

		state.observe(this, (taskState) -> {
			say(String.format(Locale.getDefault(), "done:%d total:%d %s %s",
					taskState.getDone(), taskState.getTotal(),
					taskState.isFinished() ? "FINISHED" : "",
					taskState.isSuccess() ? "SUCCESS" : "FAIL"));
		});
	}

	private void say(String txt) {
		String current = text.getText().toString();
		text.setText(
				String.format(Locale.getDefault(), "%s%s\n", current, txt));
	}
}
