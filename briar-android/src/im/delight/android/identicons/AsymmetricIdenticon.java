package im.delight.android.identicons;

/**
 * Copyright 2014 www.delight.im <info@delight.im>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.util.AttributeSet;

import org.briarproject.api.crypto.CryptoComponent;

import javax.inject.Inject;

public class AsymmetricIdenticon extends IdenticonView {

	@Inject protected CryptoComponent mCrypto;
	private IdenticonBase mDelegate;

	public AsymmetricIdenticon(Context context) {
		super(context);
		initDelegate();
	}

	public AsymmetricIdenticon(Context context, AttributeSet attrs) {
		super(context, attrs);
		initDelegate();
	}

	public AsymmetricIdenticon(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initDelegate();
	}

	@Override
	protected IdenticonBase getDelegate() {
		return mDelegate;
	}

	private void initDelegate() {
		mDelegate = new IdenticonBase() {
			@Override
			protected CryptoComponent getCrypto() {
				return mCrypto;
			}

			@Override
			protected int getRowCount() {
				return 4;
			}

			@Override
			protected int getColumnCount() {
				return 4;
			}

			@Override
			protected boolean isCellVisible(int row, int column) {
				return getByte(3 + row * getColumnCount() + column) >= 0;
			}
		};
	}

}
