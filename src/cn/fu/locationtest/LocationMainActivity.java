package cn.fu.locationtest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import cn.fu.locationtest.RouteSearchPoiDialog.OnListItemClick;
import cn.fu.locationtest.SMSObserverHandler.SMSInfo;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.LocationManagerProxy;
import com.amap.api.location.LocationProviderProxy;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMap.InfoWindowAdapter;
import com.amap.api.maps.AMap.OnInfoWindowClickListener;
import com.amap.api.maps.AMap.OnMarkerClickListener;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.overlay.BusRouteOverlay;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiItemDetail;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.services.poisearch.PoiSearch.OnPoiSearchListener;
import com.amap.api.services.route.BusPath;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.RouteSearch.BusRouteQuery;
import com.amap.api.services.route.RouteSearch.DriveRouteQuery;
import com.amap.api.services.route.RouteSearch.OnRouteSearchListener;
import com.amap.api.services.route.RouteSearch.WalkRouteQuery;
import com.amap.api.services.route.WalkRouteResult;

public class LocationMainActivity extends Activity implements LocationSource, AMapLocationListener, OnMarkerClickListener, InfoWindowAdapter, OnRouteSearchListener, OnInfoWindowClickListener, OnPoiSearchListener, WeakHandler.IHandler, OnClickListener
{

	private TextView mTextView;
	private MapView mMapView;
	private AMap mMap;
	private AMapLocation mMapLocation;

	private LocationManagerProxy mLocationManager;
	private OnLocationChangedListener mListener;

	private ProgressDialog progDialog = null;// 搜索时进度条

	private BusRouteResult busRouteResult;// 公交模式查询结果
	private DriveRouteResult driveRouteResult;// 驾车模式查询结果
	private WalkRouteResult walkRouteResult;// 步行模式查询结果

	private int busMode = RouteSearch.BusDefault;// 公交默认模式
	private int drivingMode = RouteSearch.DrivingDefault;// 驾车默认模式
	private int walkMode = RouteSearch.WalkDefault;// 步行默认模式

	private LatLonPoint startPoint = new LatLonPoint(39.983, 116.490793);// 360地址
	private LatLonPoint endPoint = new LatLonPoint(39.989614, 116.481763);// 方恒国际中心经纬度;
	private PoiSearch.Query startSearchQuery;
	private PoiSearch.Query endSearchQuery;
	private RouteSearch routeSearch;
	private WeakHandler mHandler = new WeakHandler(this);
	// handleMessage 中的消息
	private int tryCnt = 0;
	public static final int LOCATION_CHECK = 1;
	public static final int LOCATION_CANCEL = 2;
	public static final int SMS_NOTIFY = 3;

	//路径搜索临时变量
	private String routeStartStr;
	private String routeEndStr;	
	private LatLonPoint routeStartPoint;
	private LatLonPoint routeEndPoint;
	
	// panel button
	private ImageView mLocateIV;
	private ImageView mGotoIV;
	private ImageView mSMSIV;
	private ImageView mSearchIV;

	private SMSObserverHandler mSmsObserverHandler;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mTextView = (TextView) findViewById(R.id.text_view);
		mMapView = (MapView) findViewById(R.id.map);
		mMapView.onCreate(savedInstanceState);

		mLocateIV = (ImageView) findViewById(R.id.location_img);
		mGotoIV = (ImageView) findViewById(R.id.goto_img);
		mSMSIV = (ImageView) findViewById(R.id.sms_img);
		mSearchIV = (ImageView) findViewById(R.id.search_img);
		initOnClickListener(mLocateIV, mGotoIV, mSMSIV, mSearchIV);

		routeSearch = new RouteSearch(this);
		routeSearch.setRouteSearchListener(this);

		init();
	}

	private void init()
	{
		if (mMap == null)
		{
			mMap = mMapView.getMap();
			MyLocationStyle myLocationStyle = new MyLocationStyle();
			myLocationStyle.myLocationIcon(BitmapDescriptorFactory.fromResource(R.drawable.location_marker));// 设置小蓝点的图标
			myLocationStyle.strokeColor(Color.BLACK);// 设置圆形的边框颜色
			// myLocationStyle.radiusFillColor(color)//设置圆形的填充颜色
			// myLocationStyle.anchor(int,int)//设置小蓝点的锚点
			myLocationStyle.strokeWidth(0.1f);// 设置圆形的边框粗细
			// ui层 指南针 单位等
			UiSettings uiSettings = mMap.getUiSettings();
			uiSettings.setCompassEnabled(true); // 指南针
			uiSettings.setMyLocationButtonEnabled(true);// 设置默认定位按钮是否显示
			uiSettings.setScaleControlsEnabled(true); // 比例尺

			mMap.setMyLocationStyle(myLocationStyle);
			mMap.setMyLocationRotateAngle(180);
			mMap.setLocationSource(this);// 设置定位监听
			mMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
			mMap.setOnMarkerClickListener(this);
			mMap.setInfoWindowAdapter(this);// 设置自定义InfoWindow样式
			mMap.setOnInfoWindowClickListener(this);
		}

	}

	private void refreshMarker()
	{

//		HashMap<String, SMSInfo> smsMap = mSmsObserverHandler.mSMSHashMap;
//		Iterator<Entry<String, SMSInfo>> iter = smsMap.entrySet().iterator();
//		while (iter.hasNext())
//		{
//			Map.Entry entry = (Map.Entry) iter.next();
//			SMSInfo info = (SMSInfo) entry.getValue();
//
//			MarkerOptions markerOption = new MarkerOptions();
//			markerOption.position(new LatLng(info.lat, info.lng));
//			markerOption.title(info.name).snippet(info.locate);
//			markerOption.perspective(true);
//			markerOption.draggable(true);
//			markerOption.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
//			mMap.addMarker(markerOption);
//		}
		SMSInfo info = mSmsObserverHandler.targetSMSInfo;
		if (info != null)
		{			
			MarkerOptions markerOption = new MarkerOptions();
			markerOption.position(new LatLng(info.lat, info.lng));
			markerOption.title(info.name).snippet(info.locate);
			markerOption.perspective(true);
			markerOption.draggable(true);
			markerOption.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
			mMap.addMarker(markerOption);
		}
		

	}

	@Override
	public void handleMessage(Message msg)
	{
		switch (msg.what)
		{
		case LOCATION_CHECK:
			if (mMapLocation == null)
			{
				if (tryCnt < 5)
				{
					startLocateRequest();
				} else
				{
					deactivate();
				}
			}else {
				deactivate();
			}

			break;

		case LOCATION_CANCEL:
			deactivate();
			break;

		case SMS_NOTIFY:
			refreshMarker();
			break;
		default:
			break;
		}

	}

	/** start 必须重写的方法 */
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		mMapView.onSaveInstanceState(outState);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (mSmsObserverHandler == null)
		{
			mSmsObserverHandler = new SMSObserverHandler(this, mHandler);
		}
		mSmsObserverHandler.registerContentObserver();
		mMapView.onResume();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (mSmsObserverHandler != null)
		{
			mSmsObserverHandler.unregisterContentObserver();
		}
		mMapView.onPause();
		deactivate();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mMapView.onDestroy();
		deactivate();
	}

	/** end 必须重写的方法 */

	@Override
	public void onLocationChanged(Location arg0)
	{
	}

	@Override
	public void onProviderDisabled(String arg0)
	{
	}

	@Override
	public void onProviderEnabled(String arg0)
	{
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2)
	{
	}

	/**
	 * 定位成功后回调函数
	 */
	@Override
	public void onLocationChanged(AMapLocation location)
	{
		if (mListener != null && location != null)
		{
			mMapLocation = location;
			Double geoLat = location.getLatitude();
			Double geoLng = location.getLongitude();
			startPoint = new LatLonPoint(geoLat, geoLng);
			String desc = "";
			Bundle locBundle = location.getExtras();
			if (locBundle != null)
			{
				desc = locBundle.getString("desc");
			}

			mTextView.setText(desc);

			mListener.onLocationChanged(location);// 显示系统小蓝点
			float bearing = mMap.getCameraPosition().bearing;
			mMap.setMyLocationRotateAngle(bearing);// 设置小蓝点旋转角度
		}

	}

	/** 激活定位时 */
	@Override
	public void activate(OnLocationChangedListener listener)
	{
		Log.i("fu", "激活定位");
		mListener = listener;
		if (mLocationManager == null)
		{
			startLocateRequest();
		}

	}

	private void startLocateRequest()
	{
		tryCnt++;
		Log.i("fu", "开始定位");
		mLocationManager = LocationManagerProxy.getInstance(this);
		/*
		 * mAMapLocManager.setGpsEnable(false);
		 * 1.0.2版本新增方法，设置true表示混合定位中包含gps定位，false表示纯网络定位，默认是true Location
		 * API定位采用GPS和网络混合定位方式
		 * ，第一个参数是定位provider，第二个参数时间最短是5000毫秒，第三个参数距离间隔单位是米，第四个参数是定位监听者
		 */
		mLocationManager.setGpsEnable(false);
		mLocationManager.requestLocationUpdates(LocationProviderProxy.AMapNetwork, 5000, 10, this);
//		mHandler.sendEmptyMessageDelayed(LOCATION_CHECK, 12000);
	}

	/** 停止定位时 */
	@Override
	public void deactivate()
	{
		mListener = null;
		if (mLocationManager != null)
		{
			mLocationManager.removeUpdates(this);
			mLocationManager.destory();
		}
		mLocationManager = null;
	}

	@Override
	public boolean onMarkerClick(Marker marker)
	{
		// if (marker.getPosition().latitude ==
		// mMap.getMyLocation().getLatitude() && marker.getPosition().longitude
		// == mMap.getMyLocation().getLongitude())
		// {
		// Log.i("fu", "我的位置");
		// String desc = "我的位置";
		// Bundle bundle = mMapLocation.getExtras();
		// if (bundle != null)
		// {
		// desc = bundle.getString("desc");
		// }
		// marker.setTitle(desc);
		// }
		if (marker.isInfoWindowShown())
		{
			marker.hideInfoWindow();
		} else
		{
			marker.showInfoWindow();
		}
		return false;
	}

	@Override
	public View getInfoContents(Marker marker)
	{
		return null;
	}

	@Override
	public View getInfoWindow(Marker marker)
	{
		return getView(marker.getTitle(), marker.getSnippet());
	}

	/**
	 * 把一个xml布局文件转化成view
	 */
	public View getView(String title, String text)
	{
		View view = getLayoutInflater().inflate(R.layout.marker, null);
		TextView text_title = (TextView) view.findViewById(R.id.marker_title);
		TextView text_text = (TextView) view.findViewById(R.id.marker_text);
		text_title.setText(title);
		if (text == null || text.equals(""))
		{
			text_text.setVisibility(View.GONE);
		} else
		{
			text_text.setText(text);
		}
		return view;
	}

	/**
	 * 显示进度框
	 */
	private void showProgressDialog()
	{
		if (progDialog == null)
			progDialog = new ProgressDialog(this);
		progDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progDialog.setIndeterminate(false);
		progDialog.setCancelable(true);
		progDialog.setMessage("正在搜索");
		progDialog.show();
	}

	/**
	 * 隐藏进度框
	 */
	private void dissmissProgressDialog()
	{
		if (progDialog != null)
		{
			progDialog.dismiss();
		}
	}

	/**
	 * 公交路线查询回调
	 */
	@Override
	public void onBusRouteSearched(BusRouteResult result, int rCode)
	{
		dissmissProgressDialog();
		if (rCode == 0)
		{
			if (result != null && result.getPaths() != null && result.getPaths().size() > 0)
			{
				busRouteResult = result;
				BusPath busPath = busRouteResult.getPaths().get(0);
				mMap.clear();// 清理地图上的所有覆盖物
				BusRouteOverlay routeOverlay = new BusRouteOverlay(this, mMap, busPath, busRouteResult.getStartPos(), busRouteResult.getTargetPos());
				routeOverlay.removeFromMap();
				routeOverlay.addToMap();
				routeOverlay.zoomToSpan();
			} else
			{
				ToastUtil.showToast("没有结果");
			}
		} else if (rCode == 27)
		{
			ToastUtil.showToast("无网络");
		} else if (rCode == 32)
		{
			ToastUtil.showToast("鉴权出现错误");
		} else
		{
			ToastUtil.showToast("错误");
		}

	}

	/**
	 * 自驾路线查询回调
	 */
	@Override
	public void onDriveRouteSearched(DriveRouteResult result, int rCode)
	{
		// TODO Auto-generated method stub

	}

	/**
	 * 步行查询回调
	 */
	@Override
	public void onWalkRouteSearched(WalkRouteResult result, int rCode)
	{
		// TODO Auto-generated method stub

	}

	/**
	 * 开始搜索路径规划方案
	 */
	public void searchRouteResult(LatLonPoint startPoint, LatLonPoint endPoint)
	{
		showProgressDialog();
		final RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(startPoint, endPoint);
		int routeType = 1;
		if (routeType == 1)
		{// 公交路径规划
			BusRouteQuery query = new BusRouteQuery(fromAndTo, busMode, "北京", 0);// 第一个参数表示路径规划的起点和终点，第二个参数表示公交查询模式，第三个参数表示公交查询城市区号，第四个参数表示是否计算夜班车，0表示不计算
			routeSearch.calculateBusRouteAsyn(query);// 异步路径规划公交模式查询
		} else if (routeType == 2)
		{// 驾车路径规划
			DriveRouteQuery query = new DriveRouteQuery(fromAndTo, drivingMode, null, null, "");// 第一个参数表示路径规划的起点和终点，第二个参数表示驾车模式，第三个参数表示途经点，第四个参数表示避让区域，第五个参数表示避让道路
			routeSearch.calculateDriveRouteAsyn(query);// 异步路径规划驾车模式查询
		} else if (routeType == 3)
		{// 步行路径规划
			WalkRouteQuery query = new WalkRouteQuery(fromAndTo, walkMode);
			routeSearch.calculateWalkRouteAsyn(query);// 异步路径规划步行模式查询
		}
	}

	@Override
	public void onInfoWindowClick(Marker marker)
	{
	}

	@Override
	public void onClick(View v)
	{
		switch (v.getId())
		{
		case R.id.location_img:
			startLocateRequest();
			break;
		case R.id.goto_img:
			if (startPoint != null && mSmsObserverHandler.targetPoint != null)
			{
				if (startPoint.equals(mSmsObserverHandler.targetPoint))
				{
					ToastUtil.showToast("距离太近了");
				}else {
					searchRouteResult(startPoint, mSmsObserverHandler.targetPoint);
				}
			} else
			{
				ToastUtil.showToast("终点 起点信息不完整");
			}
			break;
		case R.id.sms_img:
			if (mMapLocation == null)
			{
				ToastUtil.showToast("请先定位");
			} else
			{
				sendSMS();
			}
			break;
		case R.id.search_img:
			showSearchDialog();
			break;

		default:
			break;
		}

	}

	private void initOnClickListener(View... views)
	{
		for (View view : views)
		{
			view.setOnClickListener(this);
		}
	}

	private long lastPressTime = 0;

	private void sendSMS()
	{
		if (lastPressTime + 1000 > System.currentTimeMillis())
			return;
		lastPressTime = System.currentTimeMillis();
		Intent sharedIntent = new Intent(Intent.ACTION_SEND);

		Double geoLat = mMapLocation.getLatitude();
		Double geoLng = mMapLocation.getLongitude();

		String desc = "";
		Bundle locBundle = mMapLocation.getExtras();
		if (locBundle != null)
		{
			desc = "\"" + locBundle.getString("desc") + "\"";
		}

		StringBuffer sb = new StringBuffer();
		sb.append("我在 ").append(desc).append(" 经纬度(").append(geoLat).append(",").append(geoLng).append(")");
		sharedIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		sharedIntent.setType("text/plain");
		sharedIntent.putExtra("sms_body", sb.toString());
		sharedIntent.putExtra(Intent.EXTRA_SUBJECT, "发送我的位置");
		sharedIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
		startActivity(Intent.createChooser(sharedIntent, "发送我的位置"));
	}

	// 搜索对话框
	private Dialog mSearchDialog = null;

	private void showSearchDialog()
	{
		if (mSearchDialog != null && mSearchDialog.isShowing())
		{
			return;
		}
		
		if (mSearchDialog == null)
		{
			 View view = LayoutInflater.from(this).inflate(R.layout.search_layout, null, false);
			 mSearchDialog = new Dialog(this, R.style.shareDialog);
			 mSearchDialog.setContentView(view);
			 mSearchDialog.setCanceledOnTouchOutside(true);
			 WindowManager.LayoutParams lp = mSearchDialog.getWindow().getAttributes();
			 lp.width = MyApplication.getContext().getResources().getDisplayMetrics().widthPixels;
			 mSearchDialog.getWindow().setAttributes(lp);
			 
			 
			 final EditText startET = (EditText) view.findViewById(R.id.start_text);
			 final EditText endET = (EditText) view.findViewById(R.id.end_text);
			 Button button = (Button) view.findViewById(R.id.btn);
			 
			 button.setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					String start = startET.getText().toString().trim();
					String end = endET.getText().toString().trim();
					if (start == null || start.length() == 0)
					{
						ToastUtil.showToast("请输入起点");
						return;
					}
					if (end == null || end.length() == 0)
					{
						ToastUtil.showToast("请输入终点");
					}
					if (start.equals(end))
					{
						ToastUtil.showToast("同一地点");
					}
					routeStartStr = start;
					routeEndStr = end;
					queryStartPointInfo();
					mSearchDialog.dismiss();
				}
			});
			 
		}
		mSearchDialog.show();
		
	}

	/**
	 * 查询路径规划起点
	 */
	public void queryStartPointInfo()
	{
		showProgressDialog();
		startSearchQuery = new PoiSearch.Query(routeStartStr, "", "010"); //
		// 第一个参数表示查询关键字，第二参数表示poi搜索类型，第三个参数表示城市区号或者城市名
		startSearchQuery.setPageNum(0);// 设置查询第几页，第一页从0开始
		startSearchQuery.setPageSize(20);// 设置每页返回多少条数据
		PoiSearch poiSearch = new PoiSearch(this, startSearchQuery);
		poiSearch.setOnPoiSearchListener(this);
		poiSearch.searchPOIAsyn();// 异步poi查询
	}

	/**
	 * 查询路径规划终点
	 */
	public void queryEndPointInfo()
	{
		if (routeEndPoint != null)
		{
			searchRouteResult(routeStartPoint, routeEndPoint);
		} else
		{
			showProgressDialog();
			endSearchQuery = new PoiSearch.Query(routeEndStr, "", "010"); //
			// 第一个参数表示查询关键字，第二参数表示poi搜索类型，第三个参数表示城市区号或者城市名
			endSearchQuery.setPageNum(0);// 设置查询第几页，第一页从0开始
			endSearchQuery.setPageSize(20);// 设置每页返回多少条数据

			PoiSearch poiSearch = new PoiSearch(this, endSearchQuery);
			poiSearch.setOnPoiSearchListener(this);
			poiSearch.searchPOIAsyn(); // 异步poi查询
		}
	}

	/**
	 * POI搜索结果回调
	 */
	@Override
	public void onPoiSearched(PoiResult result, int rCode)
	{
		dissmissProgressDialog();
		if (rCode == 0)
		{// 返回成功
			if (result != null && result.getQuery() != null && result.getPois() != null && result.getPois().size() > 0)
			{// 搜索poi的结果
				if (result.getQuery().equals(startSearchQuery))
				{
					List<PoiItem> poiItems = result.getPois();// 取得poiitem数据
					RouteSearchPoiDialog dialog = new RouteSearchPoiDialog(this, poiItems);
					dialog.setTitle("您要找的起点是:");
					dialog.show();
					dialog.setOnListClickListener(new OnListItemClick()
					{
						@Override
						public void onListItemClick(RouteSearchPoiDialog dialog, PoiItem startpoiItem)
						{
							routeStartPoint = startpoiItem.getLatLonPoint();
							queryEndPointInfo();// 开始搜终点
						}

					});
				} else if (result.getQuery().equals(endSearchQuery))
				{
					List<PoiItem> poiItems = result.getPois();// 取得poiitem数据
					RouteSearchPoiDialog dialog = new RouteSearchPoiDialog(this, poiItems);
					dialog.setTitle("您要找的终点是:");
					dialog.show();
					dialog.setOnListClickListener(new OnListItemClick()
					{
						@Override
						public void onListItemClick(RouteSearchPoiDialog dialog, PoiItem endpoiItem)
						{
							routeEndPoint = endpoiItem.getLatLonPoint();
							searchRouteResult(routeStartPoint, routeEndPoint);// 进行路径规划搜索
						}

					});
				}
			} else
			{
				Toast.makeText(this, "没有结果", 0).show();
			}
		} else if (rCode == 27)
		{
			Toast.makeText(this, "没有网络", 0).show();
		} else if (rCode == 32)
		{
			Toast.makeText(this, "鉴权错误", 0).show();
		} else
		{
			Toast.makeText(this, "错误", 0).show();
		}

	}

	@Override
	public void onPoiItemDetailSearched(PoiItemDetail arg0, int arg1)
	{
		// TODO Auto-generated method stub
		
	}
}
