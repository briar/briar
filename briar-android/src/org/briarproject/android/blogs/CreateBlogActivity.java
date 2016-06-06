package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.util.StringUtils;

import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_DESC_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_TITLE_LENGTH;

public class CreateBlogActivity extends BriarActivity
		implements OnEditorActionListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CreateBlogActivity.class.getName());

	private TextInputEditText titleInput, descInput;
	private Button button;
	private ProgressBar progress;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	volatile BlogManager blogManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_create_blog);

		TextInputLayout titleLayout =
				(TextInputLayout) findViewById(R.id.titleLayout);
		if (titleLayout != null) {
			titleLayout.setCounterMaxLength(MAX_BLOG_TITLE_LENGTH);
		}
		titleInput = (TextInputEditText) findViewById(R.id.titleInput);
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
		titleInput.setOnEditorActionListener(this);
		titleInput.addTextChangedListener(nameEntryWatcher);

		TextInputLayout descLayout =
				(TextInputLayout) findViewById(R.id.descLayout);
		if (descLayout != null) {
			descLayout.setCounterMaxLength(MAX_BLOG_DESC_LENGTH);
		}
		descInput = (TextInputEditText) findViewById(R.id.descInput);
		if (descInput != null) {
			descInput.addTextChangedListener(nameEntryWatcher);
		}

		button = (Button) findViewById(R.id.createBlogButton);
		if (button != null) {
			button.setOnClickListener(this);
		}

		progress = (ProgressBar) findViewById(R.id.createBlogProgressBar);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void enableOrDisableCreateButton() {
		if (progress == null) return; // Not created yet
		button.setEnabled(validateTitle() && validateDescription());
	}

	@Override
	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		descInput.requestFocus();
		return true;
	}

	private boolean validateTitle() {
		String name = titleInput.getText().toString();
		int length = StringUtils.toUtf8(name).length;
		return length <= MAX_BLOG_TITLE_LENGTH && length > 0;
	}

	private boolean validateDescription() {
		String name = descInput.getText().toString();
		int length = StringUtils.toUtf8(name).length;
		return length <= MAX_BLOG_DESC_LENGTH && length > 0;
	}

	@Override
	public void onClick(View view) {
		if (view == button) {
			hideSoftKeyboard(view);
			if (!validateTitle()) return;
			button.setVisibility(GONE);
			progress.setVisibility(VISIBLE);
			addBlog(titleInput.getText().toString(),
					descInput.getText().toString());
		}
	}

	private void addBlog(final String title, final String description) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<LocalAuthor> authors =
							identityManager.getLocalAuthors();
					// take first identity, don't support more for now
					LocalAuthor author = authors.iterator().next();
					Blog f = blogManager.addBlog(author, title, description);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing blog took " + duration + " ms");
					displayBlog(f);
				} catch (DbException e) {
					// TODO show error, e.g. blog with same title exists
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					finishOnUiThread();
				}
			}
		});
	}

	private void displayBlog(final Blog b) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// TODO
/*				Intent i = new Intent(CreateBlogActivity.this,
						BlogActivity.class);
				i.putExtra(GROUP_ID, b.getId().getBytes());
				i.putExtra(BLOG_NAME, b.getName());
				startActivity(i);
*/				Toast.makeText(CreateBlogActivity.this,
						R.string.blogs_my_blogs_created, LENGTH_LONG).show();
				supportFinishAfterTransition();
			}
		});
	}
}
