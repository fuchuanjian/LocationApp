package cn.fu.locationtest;

import java.lang.ref.WeakReference;

import android.os.Handler;
import android.os.Message;

public class WeakHandler extends Handler
{
	private WeakReference<IHandler> mWeaActivity;

	public WeakHandler(IHandler activity)
	{
		mWeaActivity = new WeakReference<WeakHandler.IHandler>(activity);
	}
	
	@Override
	public void handleMessage(Message msg)
	{
		if (mWeaActivity == null)
		{
			return;
		}
		
		IHandler handler = mWeaActivity.get();
		if (handler != null)
		{
			handler.handleMessage(msg);
		}
	}
	
	public static interface IHandler
	{
		 public void handleMessage(Message msg);
	}
}
