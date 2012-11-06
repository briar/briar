package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ContactAddedActivity extends Activity implements OnClickListener,
OnEditorActionListener {

	private volatile Button done = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(this);
		icon.setImageResource(R.drawable.navigation_accept);
		icon.setPadding(10, 10, 10, 10);
		innerLayout.addView(icon);

		TextView failed = new TextView(this);
		failed.setText(R.string.contact_added);
		failed.setTextSize(20);
		innerLayout.addView(failed);
		layout.addView(innerLayout);

		TextView enterNickname = new TextView(this);
		enterNickname.setText(R.string.enter_nickname);
		layout.addView(enterNickname);

		final Button addAnother = new Button(this);
		final Button done = new Button(this);
		this.done = done;
		EditText nicknameEntry = new EditText(this) {
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
		layout.addView(nicknameEntry);

		innerLayout = new LinearLayout(this);
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
		layout.addView(innerLayout);

		setContentView(layout);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		if(textView.getText().length() > 0) finish();
		return true;
	}

	public void onClick(View view) {
		if(done == null) return;
		if(view != done)
			startActivity(new Intent(this, NetworkSetupActivity.class));
		finish();
	}
}
