package org.briarproject.briar.android.forum;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumManager;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CreateForumActivity extends BriarActivity
		implements OnEditorActionListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CreateForumActivity.class.getName());

	private EditText nameEntry;
	private Button createForumButton;
	private ProgressBar progress;
	private TextView feedback;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumManager forumManager;

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_create_forum);

		nameEntry = (EditText) findViewById(R.id.createForumNameEntry);
		TextWatcher nameEntryWatcher = new TextWatcher() {

			@Override
			public void afterTextChanged(Editable s) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				enableOrDisableCreateButton();
			}
		};
		nameEntry.setOnEditorActionListener(this);
		nameEntry.addTextChangedListener(nameEntryWatcher);

		feedback = (TextView) findViewById(R.id.createForumFeedback);

		createForumButton = (Button) findViewById(R.id.createForumButton);
		createForumButton.setOnClickListener(this);

		progress = (ProgressBar) findViewById(R.id.createForumProgressBar);

	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void enableOrDisableCreateButton() {
		if (progress == null) return; // Not created yet
		createForumButton.setEnabled(validateName());
	}

	@Override
	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		hideSoftKeyboard(textView);
		return true;
	}

	private boolean validateName() {
		String name = nameEntry.getText().toString();
		int length = StringUtils.toUtf8(name).length;
		if (length > MAX_FORUM_NAME_LENGTH) {
			feedback.setText(R.string.name_too_long);
			return false;
		}
		feedback.setText("");
		return length > 0;
	}

	@Override
	public void onClick(View view) {
		if (view == createForumButton) {
			hideSoftKeyboard(view);
			if (!validateName()) return;
			createForumButton.setVisibility(GONE);
			progress.setVisibility(VISIBLE);
			storeForum(nameEntry.getText().toString());
		}
	}

	private void storeForum(final String name) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Forum f = forumManager.addForum(name);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing forum took " + duration + " ms");
					displayForum(f);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					finishOnUiThread();
				}
			}
		});
	}

	private void displayForum(final Forum f) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				Intent i = new Intent(CreateForumActivity.this,
						ForumActivity.class);
				i.putExtra(GROUP_ID, f.getId().getBytes());
				i.putExtra(GROUP_NAME, f.getName());
				startActivity(i);
				Toast.makeText(CreateForumActivity.this,
						R.string.forum_created_toast, LENGTH_LONG).show();
				supportFinishAfterTransition();
			}
		});
	}
}
