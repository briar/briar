package net.sf.briar;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.android.invitation.NetworkSetupActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

public class HelloWorldActivity extends Activity implements OnClickListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		TextView welcome = new TextView(this);
		welcome.setPadding(0, 0, 0, 10);
		welcome.setText(R.string.welcome);
		layout.addView(welcome);

		TextView faceToFace = new TextView(this);
		faceToFace.setPadding(0, 0, 0, 10);
		faceToFace.setText(R.string.face_to_face);
		layout.addView(faceToFace);

		Button addContact = new Button(this);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		addContact.setLayoutParams(lp);
		addContact.setText(R.string.add_contact_button);
		addContact.setCompoundDrawablesWithIntrinsicBounds(
				R.drawable.social_add_person, 0, 0, 0);
		addContact.setOnClickListener(this);
		layout.addView(addContact);

		setContentView(layout);

		startService(new Intent("net.sf.briar.HelloWorldService"));
	}

	public void onClick(View view) {
		startActivity(new Intent(this, NetworkSetupActivity.class));
	}
}
