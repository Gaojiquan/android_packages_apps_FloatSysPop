package com.focus.statusbar.policy;

import android.app.Activity;
import android.app.Service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;

import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.DeadObjectException;
import android.os.ServiceManager;
import android.os.Vibrator;

import android.util.AttributeSet;
import android.util.Log;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.IWindowManager;

import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;

import com.focus.statusbar.R;

public class FloatSysbarService extends Service {

	private WindowManager.LayoutParams mWmParams = new WindowManager.LayoutParams();
	WindowManager mWm = null;

	View mContentLayout;
	FrameLayout mFuctionMenuLayout;// fcuntion_menu

	View mSwitchmeView;
	View mPopupView;

	// to save the last location
	private final static String KEY_LASTLOCATION_X = "x";
	private final static String KEY_LASTLOCATION_Y = "y";

	private final static String KEY_FLOAT_FLAG = "float_flag";
	private final static String KEY_FLOAT = "float";

	private SharedPreferences mSharedPreferences = null;
	
	private Vibrator mVibrator;
	private long[] mLongClickPattern;

	private int mLastMoveY, mLastMoveX;
	private int mCurMoveY, mCurMoveX;

	private int mState;
	private int mLongPressOffset = 2;
	private int mToggleOffset = 5;
	private int mDelaytime = 1000;
	private int mLongPressDelaytime = 1000;
	private int mAutoHideDelaytime = 1000 * 5;
	private boolean mIsPopup = true;

	/* The WindowManager capable of injecting keyStrokes. */
	final IWindowManager windowManager = IWindowManager.Stub
			.asInterface(ServiceManager.getService("window"));

	@Override
	public void onCreate() {

		super.onCreate();

		mContentLayout = LayoutInflater.from(this).inflate(R.layout.floating_sys_bar, null);

		mVibrator = (Vibrator) mContentLayout.getContext().getSystemService(Context.VIBRATOR_SERVICE);

        mLongClickPattern = getLongIntArray(mContentLayout.getContext().getResources(),
                com.android.internal.R.array.config_longPressVibePattern);

		mFuctionMenuLayout = (FrameLayout)mContentLayout.findViewById(R.id.function_menu);
		mFuctionMenuLayout.setOnTouchListener(mMovingTouchListener);

		for(int i = 0; i < mFuctionMenuLayout.getChildCount(); i++ ) {
			
			View view = mFuctionMenuLayout.getChildAt(i);

			if (view instanceof KeyButtonView) {
				((KeyButtonView)view).setGlobalTouchListener(new OnTouchListener() {

					@Override
					public boolean onTouch(View arg0, MotionEvent event) {
						// TODO Auto-generated method stub
						Log.i("LANPENG", "resetAutoHide on KeyButtonView");
						resetAutoHide();
						return true;
					}
				});
			}
		}

		mSwitchmeView = mContentLayout.findViewById(R.id.switchme);
		mSwitchmeView.setOnClickListener(mSysFunctionMenuClickListener);

		mPopupView = mContentLayout.findViewById(R.id.popupmenu);
		mPopupView.setOnTouchListener(mMovingTouchListener);

		createView();
		mHandler.postDelayed(task, mDelaytime);
		mHandler.postDelayed(autoHideTask, mAutoHideDelaytime);

	}

	private void createView() {

		mSharedPreferences = getSharedPreferences(KEY_FLOAT_FLAG,
				Activity.MODE_PRIVATE);
		SharedPreferences.Editor editor = mSharedPreferences.edit();
		editor.putInt(KEY_FLOAT, 1);
		editor.commit();

		mWm = (WindowManager) getApplicationContext()
				.getSystemService("window");
		mWmParams.type = 2003;
		mWmParams.flags |= 8;
		mWmParams.gravity = Gravity.LEFT | Gravity.TOP;
		mWmParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		mWmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		mWmParams.format = 1;

		int x = mSharedPreferences.getInt(KEY_LASTLOCATION_X, 0);
		int y = mSharedPreferences.getInt(KEY_LASTLOCATION_Y, 0);

		mWmParams.x = x;
		mWmParams.y = y;

		mWm.addView(mContentLayout, mWmParams);

	}

	private Handler mHandler = new Handler();
	private Runnable task = new Runnable() {
		public void run() {
			// TODO Auto-generated method stub
			mHandler.postDelayed(this, mDelaytime);
			mWm.updateViewLayout(mContentLayout, mWmParams);
		}
	};

	private Runnable autoHideTask = new Runnable() {
		public void run() {
			// TODO Auto-generated method stub
			mHandler.postDelayed(this, mAutoHideDelaytime);
			hideLayout();
		}
	};

	private Runnable sysSleepTask = new Runnable() {
		public void run() {
			// TODO Auto-generated method stub
			mVibrator.vibrate(mLongClickPattern, -1);
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			pm.goToSleep(SystemClock.uptimeMillis());
		}
	};

    static long[] getLongIntArray(Resources r, int resid) {
        int[] ar = r.getIntArray(resid);
        if (ar == null) {
            return null;
        }
        long[] out = new long[ar.length];
        for (int i=0; i < ar.length; i++) {
            out[i] = ar[i];
        }
        return out;
    }

	private void updateViewPosition() {
		
		mWmParams.x += mCurMoveX - mLastMoveX;
		mWmParams.y += mCurMoveY - mLastMoveY;

		mWm.updateViewLayout(mContentLayout, mWmParams);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		popupMenu();
	}

	@Override
	public void onDestroy() {
		mHandler.removeCallbacks(task);
		mHandler.removeCallbacks(autoHideTask);
		mHandler.removeCallbacks(sysSleepTask);
		mWm.removeView(mContentLayout);

		// save the last location
		SharedPreferences.Editor editor = mSharedPreferences.edit();
		editor.putInt(KEY_LASTLOCATION_X, mWmParams.x);
		editor.putInt(KEY_LASTLOCATION_Y, mWmParams.y);
		editor.commit();

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void popupMenu() {
		if (mIsPopup) {
			hideLayout();
		} else {
			showLayout();
		}
	}

	private void hideLayout() {
		mIsPopup = false;
		mPopupView.setVisibility(View.VISIBLE);
		mFuctionMenuLayout.setVisibility(View.GONE);
	}

	private void showLayout() {
		mIsPopup = true;
		mPopupView.setVisibility(View.GONE);
		mFuctionMenuLayout.setVisibility(View.VISIBLE);
		resetAutoHide();
	}

	public void resetAutoHide() {
		mHandler.removeCallbacks(autoHideTask);
		mHandler.postDelayed(autoHideTask, mAutoHideDelaytime);
	}

	private void resetSystemSleep() {
		mHandler.removeCallbacks(sysSleepTask);
		
	}

	OnTouchListener mMovingTouchListener = new OnTouchListener() {

		@Override
		public boolean onTouch(View arg0, MotionEvent event) {
			// TODO Auto-generated method stub

			resetAutoHide();

			int x = (int) event.getRawX();
			int y = (int) event.getRawY();
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:	

				mState = MotionEvent.ACTION_DOWN;
				mLastMoveY = y;
				mLastMoveX = x;
				mCurMoveY = y;
				mCurMoveX = x;

				if(arg0 == mPopupView) {
					mHandler.postDelayed(sysSleepTask, mLongPressDelaytime);
				}
				break;
			case MotionEvent.ACTION_MOVE:
				
				mState = MotionEvent.ACTION_MOVE;
				mLastMoveY = mCurMoveY;
				mLastMoveX = mCurMoveX;
				mCurMoveY = y;
				mCurMoveX = x;
				
				int offsetY = Math.abs(mCurMoveY - mLastMoveY);
				int offsetX = Math.abs(mCurMoveX - mLastMoveX);

				if(offsetX > mLongPressOffset || 
					offsetY > mLongPressOffset) {

					mHandler.removeCallbacks(sysSleepTask);

					if(offsetX > mToggleOffset ||
						offsetY > mToggleOffset) {

						showLayout();
					}
				}
				
				updateViewPosition();
				break;

			case MotionEvent.ACTION_UP:
				mState = MotionEvent.ACTION_UP;
				mLastMoveY = y;
				mLastMoveX = x;
				updateViewPosition();

				if(arg0 == mPopupView) {
					mHandler.removeCallbacks(sysSleepTask);
				}

				break;
			}
			return true;
		}
	};

	OnClickListener mSysFunctionMenuClickListener = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			// TODO Auto-generated method stub
			if (arg0 == mSwitchmeView) {
				popupMenu();
			}
		}
	};
}
