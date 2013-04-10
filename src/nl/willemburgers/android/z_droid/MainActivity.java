package nl.willemburgers.android.z_droid;

import nl.willemburgers.android.z_android.R;
import android.hardware.usb.*;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {

	public final String TAG = "Z-Android";
	private ZWaveController zwc;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        try{
	        Intent intent = getIntent();
	        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            
            zwc = new ZWaveController(getApplicationContext(), device);
        }
        catch (Exception e){
        	Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    @Override
    public void onDestroy(){
    	super.onDestroy();
    	zwc.close();
    }
}
