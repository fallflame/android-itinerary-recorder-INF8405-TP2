package com.example.fallflame.itineraryrecorder;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.telephony.CellIdentityCdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ItineraryRecordingActivity extends FragmentActivity {

    // some constant use by android system
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;

    // Default values, for test
    final static private double DEFAULT_lNG = -73.597929;
    final static private double DEFAULT_LAT = 45.508536;
    final static private int DEFAULT_ZOOM_LEVEL = 12;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private String mode = "GPS"; //"GPS" or "Cellular", emulator can only use GPS, for test
    private int markInterval = 600; // a default interval, for test

    // used for store the
    private Location currentLocation;
    private double currentLocationTimestamp;
    final static private int validityOfCurrentLocation = 30000; //30s

    private ArrayList<ItineraryMark> itineraryMarks = new ArrayList<>();
    private ArrayList<BaseStationMark> baseStationMarks = new ArrayList<>();

    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_itinerary_recording);
        setUpMapIfNeeded();

        resetCountDownTimer();
        registerLocationListener();

    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(DEFAULT_LAT, DEFAULT_lNG), DEFAULT_ZOOM_LEVEL));
        mMap.setMyLocationEnabled(true);
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {

                ItineraryMark mark = itineraryMarks.get(Integer.parseInt(marker.getTitle()));

                View v = getLayoutInflater().inflate(R.layout.info_window_layout, null);


                TextView tvTitle = (TextView) v.findViewById(R.id.tv_title);
                TextView tvLng = (TextView) v.findViewById(R.id.tv_info);
                ImageView photoView = (ImageView) v.findViewById(R.id.photoView);


                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(mark.getImageURI()));
                    photoView.setImageBitmap(bitmap);
                } catch (IOException | NullPointerException e) {
                    // photo not exist
                }

                tvTitle.setText("Marker No.: " + marker.getTitle());
                tvLng.setText(mark.getInfoString());

                return v;
            }
        });
    }

    public void makeMark(){

        if (currentLocation == null
                || System.currentTimeMillis() - currentLocationTimestamp > validityOfCurrentLocation) {

            CharSequence text = "Cannot get the current position.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(getApplicationContext(), text, duration);
            toast.show();

        } else {

            ItineraryMark mark = new ItineraryMark();

            mark.setPosition(currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    currentLocation.getAltitude());
            mark.setBatteryLevel(getCurrentBatteryPercentage());
            mark.setMode(mode);
            if(!itineraryMarks.isEmpty())
                mark.setPreviousMark(itineraryMarks.get(itineraryMarks.size() - 1));
            itineraryMarks.add(mark);
            takePhoto(); // take photo will always add the Uri to the last element in itineraryMarks array.
            addMarkerToMap(mark); // add a marker
            if (mode == "GPS")
                addWifiInfo(itineraryMarks.indexOf(mark));

            if (mode == "Cellular")
                addBaseStationInfo();
        }

        resetCountDownTimer();
    }

    private void addBaseStationInfo(){
        //BaseStationMark bsm = new BaseStationMark();
        TelephonyManager mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        for (CellInfo cellInfo : mTelephonyManager.getAllCellInfo()) {
            BaseStationMark bsm = new BaseStationMark();
            bsm.setMcc(mTelephonyManager.getNetworkOperator().substring(0, 3));
            bsm.setMnc(mTelephonyManager.getNetworkOperator().substring(3,5));
            bsm.setMnc_name(mTelephonyManager.getNetworkOperatorName());
            switch (mTelephonyManager.getNetworkType()){
                case 7:
                    bsm.setType_r("1xRTT");
                    break;
                case 4:
                    bsm.setType_r("CDMA");
                    break;
                case 2:
                    bsm.setType_r("EDGE");
                    break;
                case 14:
                    bsm.setType_r("eHRPD");
                    break;
                case 5:
                    bsm.setType_r("EVDO rev. 0");
                    break;
                case 6:
                    bsm.setType_r("EVDO rev. A");
                    break;
                case 12:
                    bsm.setType_r("EVDO rev. B");
                    break;
                case 1:
                    bsm.setType_r("GPRS");
                    break;
                case 8:
                    bsm.setType_r("HSDPA");
                    break;
                case 10:
                    bsm.setType_r("HSPA");
                    break;
                case 15:
                    bsm.setType_r("HSPA+");
                    break;
                case 9:
                    bsm.setType_r("HSUPA");
                    break;
                case 11:
                    bsm.setType_r("iDen");
                    break;
                case 13:
                    bsm.setType_r("LTE");
                    break;
                case 3:
                    bsm.setType_r("UMTS");
                    break;
                case 0:
                    bsm.setType_r("Unknown");
                    break;
            }

            // cell_id, lac, lat, lng, niv_sig_sb
            if (cellInfo instanceof CellInfoCdma){
                CellInfoCdma cellInfoCdma = (CellInfoCdma) cellInfo;
                bsm.setNiv_sig_sb(cellInfoCdma.getCellSignalStrength().getDbm() + "dbm");
                bsm.setCell_id(cellInfoCdma.getCellIdentity().getBasestationId());
                bsm.setLac(cellInfoCdma.getCellIdentity().getNetworkId());
                if (cellInfoCdma.getCellIdentity().getLatitude() != Integer.MAX_VALUE) //means no value
                    bsm.setLat_sb(cellInfoCdma.getCellIdentity().getLatitude()/14400); // It is represented in units of 0.25 seconds
                if (cellInfoCdma.getCellIdentity().getLongitude() != Integer.MAX_VALUE) //means no value
                    bsm.setLong_sb(cellInfoCdma.getCellIdentity().getLongitude() / 14400); // It is represented in units of 0.25 seconds

            } else if (cellInfo instanceof CellInfoGsm){
                CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                bsm.setNiv_sig_sb(cellInfoGsm.getCellSignalStrength().getDbm() + "dbm");
                bsm.setCell_id(cellInfoGsm.getCellIdentity().getCid());
                bsm.setLac(cellInfoGsm.getCellIdentity().getLac());

            } else if (cellInfo instanceof CellInfoLte){
                CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                bsm.setNiv_sig_sb(cellInfoLte.getCellSignalStrength().getDbm() + "dbm");
                bsm.setCell_id(cellInfoLte.getCellIdentity().getCi());
                // LTE cannot get a location

            } else if (cellInfo instanceof CellInfoWcdma){
                CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfo;
                bsm.setNiv_sig_sb(cellInfoWcdma.getCellSignalStrength().getDbm() + "dbm");
                bsm.setLac(cellInfoWcdma.getCellIdentity().getLac());
                bsm.setCell_id(cellInfoWcdma.getCellIdentity().getCid());
            }

            for (BaseStationMark b : baseStationMarks){
                if (b.getCell_id() == bsm.getCell_id())
                    return; // base station already added
            }

            if ( cellInfo instanceof CellInfoGsm || cellInfo instanceof  CellInfoWcdma &&
                   bsm.getCell_id() != 0 && bsm.getCell_id() != -1 && bsm.getCell_id() != Integer.MAX_VALUE
                && bsm.getLac() !=0 && bsm.getLac()!=-1 && bsm.getCell_id() != Integer.MAX_VALUE    ){

                double[] latlng = getBaseStation(bsm.getCell_id(),
                                                 bsm.getLac(),
                                                 Integer.valueOf(bsm.getMcc()),
                                                 Integer.valueOf(bsm.getMnc()));
                bsm.setLat_sb(latlng[0]);
                bsm.setLong_sb(latlng[1]);
            }

            baseStationMarks.add(bsm);

        }
    }

    public double[] getBaseStation(int cid, int lac, int mcc, int mnc) {
        double[] latlng = new double[]{0.0, 0.0};

        try {

            JSONObject holder = new JSONObject();

            JSONArray array = new JSONArray();
            JSONObject data = new JSONObject();
            data.put("cell_id", cid);
            data.put("locationAreaCode", lac);
            data.put("mobileCountryCode", mcc);
            data.put("mobileNetworkCode", mnc);
            array.put(data);
            holder.put("cell_towers", array);

            DefaultHttpClient client = new DefaultHttpClient();

            HttpPost post = new HttpPost("https://www.googleapis.com/geolocation/v1/geolocate?key=API_KEY");

            StringEntity se = new StringEntity(holder.toString());

            post.setEntity(se);
            HttpResponse resp = client.execute(post);

            HttpEntity entity = resp.getEntity();

            BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
            StringBuffer sb = new StringBuffer();
            String result = br.readLine();

            Log.e("GetBaseStation", result);

            while (result != null) {

                sb.append(result);
                result = br.readLine();
            }
            JSONObject jsonObject = new JSONObject(sb.toString());

            JSONObject jsonObject1 = new JSONObject(jsonObject.getString("location"));

            latlng[0] = Double.parseDouble(jsonObject1.getString("lat"));
            latlng[1] = Double.parseDouble(jsonObject1.getString("lng"));

        } catch (Exception e) {
        }

        return latlng;
    }



    private void addWifiInfo(final int markIndex){
        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if(!wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);

        //register to receive the call back when wifi scan results are ready
        IntentFilter i = new IntentFilter();
        i.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(new BroadcastReceiver(){
            public void onReceive(Context c, Intent i){
                List<ScanResult> scanResultList = wifiManager.getScanResults(); // Returns a <list> of scanResults
                if (scanResultList.size() != 0) {
                    int highestLevelIndex = 0;
                    int highestLevel = 0;
                    for (ScanResult scanResult : scanResultList) {
                        if (scanResult.level > highestLevel) {
                            highestLevelIndex = scanResultList.indexOf(scanResult);
                        }
                    }

                    ScanResult hr = scanResultList.get(highestLevelIndex);
                    String wifiInfo = "PA_Wifi(" + hr.SSID + ", " + hr.level + "dBm, " + hr.BSSID +")";

                    itineraryMarks.get(markIndex).setWifiInfo(wifiInfo);

                    new AlertDialog.Builder(getApplicationContext()).setTitle("Wifi points found.")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                }


            }
        }, i );

        wifiManager.startScan();
    }

    public void makeMark(View view) {
        makeMark();
    }

    private void addMarkerToMap(ItineraryMark mark){
        if (mMap != null){
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(mark.getPosition()[0], mark.getPosition()[1]))
                    .title(itineraryMarks.size() - 1 + ""));
            if(itineraryMarks.size() >=2) {
                ItineraryMark p1 = itineraryMarks.get(itineraryMarks.size()-2);
                ItineraryMark p2 = itineraryMarks.get(itineraryMarks.size()-1);
                double lat1 = p1.getPosition()[0];
                double lng1 = p1.getPosition()[1];
                double lat2 = p2.getPosition()[0];
                double lng2 = p2.getPosition()[1];
                PolylineOptions lineOptions = new PolylineOptions()
                        .add(new LatLng(lat1, lng1))
                        .add(new LatLng(lat2, lng2));
                Polyline polyline = mMap.addPolyline(lineOptions);
            }
        }
    }

    private void registerLocationListener(){
        LocationManager locationManager = (LocationManager) this.getSystemService(getApplicationContext().LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                updateLocation(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

    // Register the listener with the Location Manager to receive location updates

        String locationProvider = "";
        if (mode == "GPS"){
            locationProvider = LocationManager.GPS_PROVIDER;

        } else if (mode == "Cellular"){
            locationProvider = LocationManager.NETWORK_PROVIDER;
        }
        locationManager.requestLocationUpdates(locationProvider, 0, 0, locationListener);
    }

    private float getCurrentBatteryPercentage(){
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus =  getApplicationContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level / (float)scale;
    }

    private void updateLocation(Location location){
        currentLocation = location;
        currentLocationTimestamp = System.currentTimeMillis();
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), DEFAULT_ZOOM_LEVEL));
    }

    private void takePhoto(){
        new AlertDialog.Builder(this).setTitle("Take a photo?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeToImageCaptureActivity();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .show();
    }

    private void changeToImageCaptureActivity(){

        // create Intent to take a picture and return control to the calling application
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        Uri fileUri = Uri.fromFile(getOutputMediaFile(MEDIA_TYPE_IMAGE)); // create a file to save the image
        itineraryMarks.get(itineraryMarks.size() - 1).setImageURI(fileUri.toString());
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri); // set the image file name

        // start the image capture Intent
        startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){

        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        if ( ! Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ){
            // TODO
            return null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else {
            return null;
        }

        return mediaFile;
    }

    private void resetCountDownTimer(){

        if (countDownTimer != null)
            countDownTimer.cancel();

        countDownTimer = new CountDownTimer(markInterval * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                TextView mCountDownTimerView = (TextView)findViewById(R.id.mCountDownTimerView);
                int minute = (int) millisUntilFinished / 1000 / 60;
                int second = (int) millisUntilFinished / 1000 % 60;

                mCountDownTimerView.setText("Next Mark in: " + minute + " minutes " + second + " seconds.");
            }

            @Override
            public void onFinish() {
                makeMark();
            }
        }.start();
    }

    public void finishRecorder(View view){

        if(itineraryMarks.size() != 0) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(itineraryMarks);
                oos.flush();
                oos.close();
                bos.close();

                byte[] data = bos.toByteArray();

                SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);

                db.execSQL("CREATE TABLE IF NOT EXISTS itineraries (_id INTEGER PRIMARY KEY AUTOINCREMENT, itineraryDate INTEGER, itineraryMarks BLOB)");
                db.execSQL("INSERT INTO itineraries VALUES (NULL, ?, ?)", new Object[]{System.currentTimeMillis(), data});


            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Intent intent = new Intent(this, ItineraryRecordedActivity.class);
        startActivity(intent);
    }
}
