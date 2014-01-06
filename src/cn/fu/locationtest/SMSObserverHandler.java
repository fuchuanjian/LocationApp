package cn.fu.locationtest;

import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.fu.locationtest.SMSObserverHandler.SMSInfo;

import com.amap.api.services.core.LatLonPoint;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

public class SMSObserverHandler
{
	private Context mContext;
	private Handler mHandler;
	private SMSContentObserver msmsoContentObserver;
	private final Uri SMS_Uri = Uri.parse("content://sms");
//	private final Uri SMS_Uri = Uri.parse("content://sms/inbox");
	private static final String[] PROJECTION = new String[]
	{ "_id", "address", "person", "body", "date", "type" };
	public HashMap<String, SMSInfo> mSMSHashMap = new HashMap<String, SMSInfo>();
	private Pattern patternAll = Pattern.compile("\\(\\d+\\.\\d+,\\d+\\.\\d+\\)");
	private Pattern patternItem = Pattern.compile("\\d+\\.\\d+");
	private Pattern patternLocate = Pattern.compile("\".+\"");
	final Semaphore sp = new Semaphore(1);
	public LatLonPoint targetPoint = null;
	public SMSInfo targetSMSInfo;
	private long maxTime = 0;
	
	public SMSObserverHandler(Context context, Handler handler)
	{
		mContext = context;
		mHandler = handler;
		msmsoContentObserver = new SMSContentObserver(new Handler());
		querySMS();
	}

	// 查询短信
	private void querySMS()
	{
		new Thread(queryRunnable).start();
	}

	private Runnable queryRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			try
			{
				if (sp.availablePermits() == 0)
				{
					return;
				}
				sp.acquire();
				mSMSHashMap.clear();
				// "date > "+String.valueOf(System.currentTimeMillis()-24*60*60*1000)
				Cursor cursor = mContext.getContentResolver().query(SMS_Uri, PROJECTION, null, null, null);
				if (cursor != null)
				{
					while (cursor.moveToNext())
					{
						String body = cursor.getString(cursor.getColumnIndex("body"));
						Matcher matcher = patternAll.matcher(body);
						if (matcher.find())
						{
							String address = cursor.getString(cursor.getColumnIndex("address"));
							long date = cursor.getLong(cursor.getColumnIndex("date"));
							String name = getNameFromnumber(address);
							// 匹配出经纬度
							String line = matcher.group();
							Matcher m = patternItem.matcher(line);
							m.find();
							double lat = Double.valueOf(m.group());
							m.find();
							double lng = Double.valueOf(m.group());
							String locate = null;
							Matcher locateMatcher = patternLocate.matcher(body);
							
							
							if (locateMatcher.find())
							{
								locate = locateMatcher.group();
							}
							Log.i("fu", "地点 "+ locate +"  "+ body);
							// 存到map中
							if (mSMSHashMap.containsKey(name))
							{
								SMSInfo info = mSMSHashMap.get(name);
								if (info.date < date)
								{
									info.date = date;
								}
							} else
							{
								SMSInfo info = new SMSInfo(name, locate, lat, lng, date);
								mSMSHashMap.put(name, info);
							}
							if (date > maxTime)
							{
								maxTime = date;
								targetPoint = new LatLonPoint(lat, lng);
								targetSMSInfo = mSMSHashMap.get(name);
							}
						}
					}
					cursor.close();
					
					if (mHandler != null && mSMSHashMap!= null && mSMSHashMap.size() > 0)
					{
						mHandler.sendEmptyMessage(LocationMainActivity.SMS_NOTIFY);
					}
				}
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			sp.release();
		}
	};

	public void registerContentObserver()
	{
		// 监听通讯录
		try
		{
			mContext.getContentResolver().registerContentObserver(SMS_Uri, true, msmsoContentObserver);
			querySMS();
		} catch (Exception e)
		{
		}
	}

	public void unregisterContentObserver()
	{
		try
		{
			mContext.getContentResolver().unregisterContentObserver(msmsoContentObserver);
		} catch (Exception e)
		{
		}
	}

	private class SMSContentObserver extends ContentObserver
	{

		public SMSContentObserver(Handler handler)
		{
			super(handler);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void onChange(boolean selfChange)
		{
			super.onChange(selfChange);
			querySMS();
		}
	}

	/**
	 * 通过号码匹配名字 通用 有未接来电或者短信时通过号码查询联系人的名字，先到SIM卡中查，再到手机中查找
	 */
	public String getNameFromnumber(String number)
	{ // 从SIM卡中查找 是否有
		Uri simUri = Uri.parse("content://icc/adn");
		Uri phoneUri = Phone.CONTENT_URI;
		Cursor cursor;
		String name = null;
		try
		{
			// sim卡
			cursor = mContext.getContentResolver().query(simUri, null, null, null, null);
			while (cursor.moveToNext())
			{
				String tmp = cursor.getString(cursor.getColumnIndex("number"));
				if (PhoneNumberUtils.compare(number, tmp))
				{
					name = cursor.getString(cursor.getColumnIndex("name"));
				}
			}
			cursor.close();

			// 如果名字还是空，说明sim卡中没有，去手机中查找
			if (name == null || name.equals(""))
			{
				String projection[] = new String[]
				{ Phone.DISPLAY_NAME, Phone.NUMBER, Phone.CONTACT_ID };
				cursor = mContext.getContentResolver().query(phoneUri, projection, null, null, null);
				while (cursor.moveToNext())
				{
					String tmp = cursor.getString(1);
					if (PhoneNumberUtils.compare(number, tmp))
					{
						name = cursor.getString(0);
					}
				}
				cursor.close();
			}

		} catch (Exception e)
		{
			// TODO: handle exception
		}
		if (name == null || name.equals(""))
		{
			name = number;
		}
		return name;
	}

	public static class SMSInfo
	{
		public SMSInfo(String name, String locate, double lat, double lng, long date)
		{
			this.name = name;
			this.locate = locate;
			this.lat = lat;
			this.lng = lng;
			this.date = date;
		}
		String name;
		String locate;
		double lat;
		double lng;
		long date;
	}
}
