package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.widget.LinearLayout.HORIZONTAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CodesDoNotMatchActivity extends Activity
implements OnClickListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_codes_do_not_match);
		LinearLayout outerLayout = (LinearLayout) findViewById(
				R.id.codes_do_not_match_container);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);
		ImageView icon = new ImageView(this);
		icon.setImageResource(R.drawable.iconic_x_alt_red);
		icon.setPadding(10, 10, 10, 10);
		innerLayout.addView(icon);
		TextView failed = new TextView(this);
		failed.setTextSize(20);
		failed.setText(R.string.codes_do_not_match);
		innerLayout.addView(failed);
		outerLayout.addView(innerLayout);

		TextView interfering = new TextView(this);
		interfering.setText(R.string.interfering);
		outerLayout.addView(interfering);
		Button tryAgain = new Button(this);
		tryAgain.setText(R.string.try_again_button);
		tryAgain.setOnClickListener(this);
		outerLayout.addView(tryAgain);
	}

	public void onClick(View view) {
		Intent intent = new Intent(this, InvitationCodeActivity.class);
		intent.putExtras(getIntent().getExtras());
		startActivity(intent);
		finish();
	}
}
