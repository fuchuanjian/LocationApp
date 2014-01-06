package cn.fu.locationtest;

import android.util.Log;
import android.widget.Toast;

public class ToastUtil
{
	private static Toast toast;
	
    public synchronized static void showToast(String str) {
        try {
            if (toast == null) {
                toast = Toast.makeText(MyApplication.getContext(), str, Toast.LENGTH_SHORT);
            }
            toast.setText(str);
            toast.setDuration(0);
            toast.show();
        } catch (Exception e) {
            // TODO: handle exception
        	Log.i("fu", "出错了 "+e);
        }
    }
    
    public static void release()
    {
    	toast = null;
    }
}
