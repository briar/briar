package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CodesDoNotMatchActivity extends Activity
implements OnClickListener {

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
		icon.setPadding(10, 10, 10, 10);
		icon.setImageResource(R.drawable.alerts_and_states_error);
		innerLayout.addView(icon);

		TextView failed = new TextView(this);
		failed.setTextSize(20);
		failed.setText(R.string.codes_do_not_match);
		innerLayout.addView(failed);
		layout.addView(innerLayout);

		TextView interfering = new TextView(this);
		interfering.setText(R.string.interfering);
		layout.addView(interfering);

		Button tryAgain = new Button(this);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		tryAgain.setLayoutParams(lp);
		tryAgain.setText(R.string.try_again_button);
		tryAgain.setOnClickListener(this);
		layout.addView(tryAgain);

		setContentView(layout);
	}

	public void onClick(View view) {
		Intent intent = new Intent(this, InvitationCodeActivity.class);
		intent.putExtras(getIntent().getExtras());
		startActivity(intent);
		finish();
	}
}
