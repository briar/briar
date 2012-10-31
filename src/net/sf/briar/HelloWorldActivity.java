package net.sf.briar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class HelloWorldActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text = new TextView(this);
		text.setText("Hello world");
		setContentView(text);
		Intent intent = new Intent("net.sf.briar.HelloWorldService");
		startService(intent);
	}
}
