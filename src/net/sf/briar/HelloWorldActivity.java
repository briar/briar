package net.sf.briar;

import net.sf.briar.android.invitation.NetworkSetupActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HelloWorldActivity extends Activity implements OnClickListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_contact);
		LinearLayout layout = (LinearLayout) findViewById(
				R.id.add_contact_container);

		TextView welcome = new TextView(this);
		welcome.setText(R.string.welcome);
		layout.addView(welcome);
		Button addContact = new Button(this);
		addContact.setText(R.string.add_contact_button);
		addContact.setOnClickListener(this);
		layout.addView(addContact);
		TextView faceToFace = new TextView(this);
		faceToFace.setText(R.string.face_to_face);
		layout.addView(faceToFace);

		Intent intent = new Intent("net.sf.briar.HelloWorldService");
		startService(intent);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, NetworkSetupActivity.class));
		finish();
	}
}
