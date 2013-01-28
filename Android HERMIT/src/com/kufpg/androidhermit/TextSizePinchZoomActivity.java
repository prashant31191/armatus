package com.kufpg.androidhermit;

import java.util.ArrayList;

import com.kufpg.androidhermit.util.ConsoleTextView;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;


public class TextSizePinchZoomActivity extends Activity implements OnTouchListener {

	private final static float STEP = 200;
	private final static int DEFAULT_FONT_SIZE = ConsoleTextView.DEFAULT_FONT_SIZE;
	private final static int MAX_FONT_SIZE = ConsoleTextView.MAX_FONT_SIZE;
	private final static int MIN_FONT_SIZE = ConsoleTextView.MIN_FONT_SIZE;

	private TextView mTextRatio1, mTextRatio2;
	private ArrayList<TextView> mTextViews = new ArrayList<TextView>();
	private float mRatio = 1.0f;
	private int mBaseDist;
	private float mBaseRatio;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.textsize_pinchzoom);

		mTextRatio1 = (TextView) findViewById(R.id.resize_me);
		mTextRatio2 = (TextView) findViewById(R.id.resize_me2);
		mTextViews.add(mTextRatio1);
		mTextViews.add(mTextRatio2);
		for (TextView tv : mTextViews) {
			tv.setTextSize(mRatio + DEFAULT_FONT_SIZE);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getPointerCount() == 2) {
			int action = event.getAction();
			int pureaction = action & MotionEvent.ACTION_MASK;
			if (pureaction == MotionEvent.ACTION_POINTER_DOWN) {
				mBaseDist = getDistance(event);
				mBaseRatio = mRatio;
			} else {
				float delta = (getDistance(event) - mBaseDist) / STEP;
				float multi = (float)Math.pow(2, delta);
				mRatio = Math.min(1024.0f, Math.max(0.1f, mBaseRatio * multi));
				int newFontSize = (int) mRatio + DEFAULT_FONT_SIZE;
				if (newFontSize > MAX_FONT_SIZE) {
					newFontSize = MAX_FONT_SIZE;
					mRatio = MAX_FONT_SIZE - DEFAULT_FONT_SIZE;
				} else if (newFontSize < MIN_FONT_SIZE) {
					newFontSize = MIN_FONT_SIZE;
					mRatio = MIN_FONT_SIZE + DEFAULT_FONT_SIZE;
				}
				for (TextView tv : mTextViews) {
					tv.setTextSize(newFontSize);
				}
			}
		}
		return true;
	}

	private int getDistance(MotionEvent event) {
		int dx = (int)(event.getX(0) - event.getX(1));
		int dy = (int)(event.getY(0) - event.getY(1));
		return (int)(Math.sqrt(dx * dx + dy * dy));
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return false;
	}
}