package org.briarproject.android.forum;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.util.StringUtils;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;

public class CreateForumActivity extends BriarActivity
implements OnEditorActionListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CreateForumActivity.class.getName());

	private EditText nameEntry = null;
	private Button createForumButton = null;
	private ProgressBar progress = null;
	private TextView feedback = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ForumSharingManager forumSharingManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, pad, pad, pad);

		TextView chooseName = new TextView(this);
		chooseName.setGravity(CENTER);
		chooseName.setTextSize(18);
		chooseName.setText(R.string.choose_forum_name);
		layout.addView(chooseName);

		nameEntry = new EditText(this) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				enableOrDisableCreateButton();
			}
		};
		nameEntry.setId(1);
		nameEntry.setMaxLines(1);
		nameEntry.setInputType(TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_SENTENCES);
		nameEntry.setOnEditorActionListener(this);
		layout.addView(nameEntry);

		feedback = new TextView(this);
		feedback.setGravity(CENTER);
		feedback.setPadding(0, pad, 0, pad);
		layout.addView(feedback);

		createForumButton = new Button(this);
		createForumButton.setLayoutParams(WRAP_WRAP);
		createForumButton.setText(R.string.create_forum_button);
		createForumButton.setOnClickListener(this);
		layout.addView(createForumButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);

		setContentView(layout);
	}

	private void enableOrDisableCreateButton() {
		if (progress == null) return; // Not created yet
		createForumButton.setEnabled(validateName());
	}

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
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Forum f = forumSharingManager.createForum(name);
					forumSharingManager.addForum(f);
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
		runOnUiThread(new Runnable() {
			public void run() {
				Intent i = new Intent(CreateForumActivity.this,
						ForumActivity.class);
				i.putExtra("briar.GROUP_ID", f.getId().getBytes());
				i.putExtra("briar.FORUM_NAME", f.getName());
				startActivity(i);
				Toast.makeText(CreateForumActivity.this,
						R.string.forum_created_toast, LENGTH_LONG).show();
				finish();
			}
		});
	}
}
