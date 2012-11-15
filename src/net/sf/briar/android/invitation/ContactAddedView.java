package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ContactAddedView extends AddContactView implements OnClickListener,
OnEditorActionListener {

	private Button done = null;

	ContactAddedView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(ctx);
		icon.setImageResource(R.drawable.navigation_accept);
		icon.setPadding(10, 10, 10, 10);
		innerLayout.addView(icon);

		TextView failed = new TextView(ctx);
		failed.setText(R.string.contact_added);
		failed.setTextSize(20);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView enterNickname = new TextView(ctx);
		enterNickname.setGravity(CENTER_HORIZONTAL);
		enterNickname.setText(R.string.enter_nickname);
		addView(enterNickname);

		final Button addAnother = new Button(ctx);
		final Button done = new Button(ctx);
		this.done = done;
		EditText nicknameEntry = new EditText(ctx) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				addAnother.setEnabled(text.length() > 0);
				done.setEnabled(text.length() > 0);
			}
		};
		nicknameEntry.setMinEms(10);
		nicknameEntry.setMaxEms(20);
		nicknameEntry.setMaxLines(1);
		nicknameEntry.setOnEditorActionListener(this);
		addView(nicknameEntry);

		innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		addAnother.setText(R.string.add_another_contact_button);
		addAnother.setEnabled(false);
		addAnother.setOnClickListener(this);
		innerLayout.addView(addAnother);

		done.setText(R.string.done_button);
		done.setEnabled(false);
		done.setOnClickListener(this);
		innerLayout.addView(done);
		addView(innerLayout);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		if(textView.getText().length() > 0) container.finish();
		return true;
	}

	public void onClick(View view) {
		if(view == done) container.finish(); // Done
		else container.reset(new NetworkSetupView(container)); // Add another
	}
}
