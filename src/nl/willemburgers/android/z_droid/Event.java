package nl.willemburgers.android.z_droid;

import android.util.Log;

public final class Event {
	private final String TAG = "Z-Droid";
	private boolean isSet;
	
	public synchronized void Set(){
		//Log.d(TAG, "set");
		isSet = true;
		notify();
	}
	
	public void Reset(){
		//Log.d(TAG, "unset");
		isSet = false;
	}
	
	public synchronized boolean Wait(long milis){
		if(isSet)
		{
			return isSet;
		}
		//Log.d(TAG, "wait");
		if(milis < 0){
			try {
				wait();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		else{
			try {
				wait(milis);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return this.isSet;
	}
}
