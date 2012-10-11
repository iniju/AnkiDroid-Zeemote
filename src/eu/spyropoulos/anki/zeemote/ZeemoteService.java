package eu.spyropoulos.anki.zeemote;

import java.io.IOException;
import java.lang.ref.WeakReference;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.zeemote.zc.Controller;
import com.zeemote.zc.event.BatteryEvent;
import com.zeemote.zc.event.ButtonEvent;
import com.zeemote.zc.event.ControllerEvent;
import com.zeemote.zc.event.DisconnectEvent;
import com.zeemote.zc.event.IButtonListener;
import com.zeemote.zc.event.IStatusListener;
import com.zeemote.zc.ui.android.ControllerAndroidUi;
import com.zeemote.zc.util.JoystickToButtonAdapter;

public class ZeemoteService extends Service  implements IStatusListener, IButtonListener {
	public static final String TAG = "AnkiDroidZeemote";
	public static final int MSG_ZEEMOTE_BUTTON_BASE = 0x110;
	public static final int MSG_ZEEMOTE_STICK_BASE = 0x210;
	public static final int MSG_CNTRL_ACTION = 0;
	public static final int MSG_CNTRL_REGISTER_MANAGER = -1;
	public static final int MSG_CNTRL_WAIT_FOR_CONNECT = -2;
	public static final int MSG_CNTRL_SUPPORTED_ACTIONS = -4;
	public static final int MSG_CNTRL_START_LISTENING = -5;
	public static final int MSG_CNTRL_STOP_LISTENING = -6;
	public static final int MSG_CNTRL_DISCONNECT = -7;
	public static final int MSG_ARG_REQ_ACK = 0;
	public static final int MSG_ARG_ACK = 1;
    public static final int MSG_ARG_DONE = 2;
	
    protected Controller mController;
    protected NotificationManager mNm;
    protected Messenger mMessenger = new Messenger(new IncomingHandler(this));
    protected Messenger mManager;
    protected Bundle mActions;
    protected int mValue = 0;
	protected ControllerAndroidUi mControllerUi;
	protected JoystickToButtonAdapter mAdapter;
	protected boolean mIsListening;
	protected boolean mIsConnected;
	protected int mBattery = -1;
	
	@Override
	public void onCreate() {
		Log.d(TAG, "Controller service created");
		mNm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mController = new Controller(1, Controller.TYPE_GP1);
		mAdapter = new JoystickToButtonAdapter();
		mIsListening = false;
		mIsConnected = false;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "Controller service destroyed");
        // Tell the user we stopped.
		if (mIsConnected) {
			Toast.makeText(this, getResources().getString(R.string.toast_disconnected), Toast.LENGTH_SHORT).show();
			mIsConnected = false;
		}
        mIsListening = false;
        mAdapter = null;
        mController = null;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG, "Controller service bound");
		return mMessenger.getBinder();
	}

	@Override
	public void onRebind(Intent arg0) {
		Log.d(TAG, "Controller service re-bound");
	}

	@Override
	public boolean onUnbind(Intent arg0) {
		Log.d(TAG, "Controller service unbound");
		mIsListening = false;
		if (mNm != null) {
			mNm.cancel(R.string.stat_connected);
		}
		mController.removeStatusListener(this);
		mController.removeButtonListener(this);
		mController.removeJoystickListener(this.mAdapter);
		if (mController != null) {
			try {
				mController.disconnect();
			} catch (IOException e) {
				Log.e(TAG, "Error while disconnecting controller", e);
			}
		}
		return false;
	}
	
    static class IncomingHandler extends Handler {
    	private final WeakReference<ZeemoteService> mOwner;
    	IncomingHandler(ZeemoteService owner) {
    		mOwner = new WeakReference<ZeemoteService>(owner);
    	}
    	@Override
    	public void handleMessage(Message msg) {
    		ZeemoteService owner = mOwner.get();
    		if (owner != null) {
    			Message response;
    			switch (msg.what) {
    			case MSG_CNTRL_REGISTER_MANAGER:
    				owner.mManager = msg.replyTo;
    				BluetoothAdapter bla = BluetoothAdapter.getDefaultAdapter();
    				if (bla == null || !bla.isEnabled()) {
    					Toast.makeText(owner, owner.getResources().getString(R.string.toast_bluetooth_disabled),
    							Toast.LENGTH_SHORT).show();
    					response = Message.obtain(null, MSG_CNTRL_DISCONNECT);
	    				try {
							owner.mManager.send(response);
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
    				} else {
    					owner.mControllerUi = new ControllerAndroidUi(owner, owner.mController);
    					response = Message.obtain(null, MSG_CNTRL_WAIT_FOR_CONNECT);
    					response.arg1 = MSG_ARG_REQ_ACK;
    				}
    				try {
						owner.mManager.send(response);
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    				break;
    			case MSG_CNTRL_WAIT_FOR_CONNECT:
    				if (msg.arg1 == MSG_ARG_ACK) {
    					owner.mControllerUi.startConnectionProcess();
    				} else if (msg.arg1 == MSG_ARG_DONE) {
    					if (owner.mController.isConnected()) {
    						owner.mIsConnected = true;
    	    				owner.mIsListening = true;
    	    				owner.mController.addStatusListener(owner);
    	    				owner.mController.addButtonListener(owner);
    	    				owner.mController.addJoystickListener(owner.mAdapter);
    	    				owner.mAdapter.addButtonListener(owner);
    	    				owner.showNotification();
	    					Log.d(TAG, "Controller connected.");
    					} else {
    	    				response = Message.obtain(null, MSG_CNTRL_DISCONNECT);
    	    				try {
    							owner.mManager.send(response);
    						} catch (RemoteException e) {
    							// TODO Auto-generated catch block
    							e.printStackTrace();
    						}
    					}
    				}
    				break;
    			case MSG_CNTRL_START_LISTENING:
    				if (!owner.mIsListening) {
	    				owner.mIsListening = true;
    				}
    				break;
    			case MSG_CNTRL_STOP_LISTENING:
    				if (owner.mIsListening) {
    					owner.mIsListening = false;
    				}
    				break;
    			case MSG_CNTRL_SUPPORTED_ACTIONS:
    				owner.mActions = msg.getData();
    				for (String action: owner.mActions.keySet()) {
    					Log.d(TAG, "Action: " + action + ": " + owner.mActions.getInt(action));
    				}
    				break;
    			default:
    			}
    		}
        	super.handleMessage(msg);
    	}
    }

    private void showNotification() {
    	CharSequence title = getResources().getString(R.string.stat_connected, mController.getDeviceName());
    	CharSequence battery;
    	if (mBattery >= 0) {
    		battery = getResources().getString(R.string.stat_battery, mBattery);
    	} else {
    		battery = mController.getDeviceName();
    	}
        Notification notification = new Notification(R.drawable.ic_stat_joystick, title,
                System.currentTimeMillis());
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, Controller.class), 0);
        notification.setLatestEventInfo(this, title, battery, contentIntent);
        if (mNm != null) {
        	mNm.notify(R.string.stat_connected, notification);
        }
    }

	@Override
	public void buttonReleased(ButtonEvent arg0) {
		if (mIsListening) {
			Log.d(TAG, "button released " + arg0.getButtonID());
			Message msg = Message.obtain(null, MSG_CNTRL_ACTION);
			if (arg0.getButtonID() == -1) {
				msg.what = MSG_ZEEMOTE_STICK_BASE + arg0.getButtonGameAction();
			} else {
				msg.what = MSG_ZEEMOTE_BUTTON_BASE + arg0.getButtonID();
			}

			try {
				mManager.send(msg);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void batteryUpdate(BatteryEvent arg0) {
		mBattery = (100 * (arg0.getCurrentLevel() - arg0.getMinimumLevel()) / (arg0.getMaximumLevel() - arg0.getMinimumLevel()));
		showNotification();
	}

	@Override
	public void buttonPressed(ButtonEvent arg0) { }

	@Override
	public void connected(ControllerEvent arg0) { }

	@Override
	public void disconnected(DisconnectEvent arg0) { }
}
