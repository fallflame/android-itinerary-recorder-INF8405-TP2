package com.example.fallflame.itineraryrecorder;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ItineraryRecordingActivity extends FragmentActivity {

    // constance for image capture
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private String mode = "Cellular"; //"GPS" or "Cellular", emulator can only use GPS, for test
    private int markInterval; // interval of making mark
    private int zoomLevel;

    // store the location get from listener
    private Location currentLocation;
    // store the time when location get updated
    private double currentLocationTimestamp;
    final static private int validityOfCurrentLocation = 20000; //20s

    // store the points during this itinerary
    private ArrayList<ItineraryPointMark> itineraryPointMarks = new ArrayList<>();

    // a count down timer to indicate when next capture will come
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_itinerary_recording);

        // get data from configuration activity
        Intent intent = getIntent();
        this.mode = intent.getStringExtra("mode");
        markInterval = intent.getIntExtra("interval", 600);
        zoomLevel = intent.getIntExtra("zoomLevel", 10);

        setUpMapIfNeeded();

        // Put two marker for initial and final positions in maps if possible
        setPositionOnMap("initial", intent.getStringExtra("initialPosition"));
        setPositionOnMap("final", intent.getStringExtra("finalPosition"));

        // reset the count down timer
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
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), zoomLevel));
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.setMyLocationEnabled(true);
        //customize the modal window
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {

                try {
                    ItineraryPointMark mark = itineraryPointMarks.get(Integer.parseInt(marker.getTitle()));

                    LinearLayout v = (LinearLayout) getLayoutInflater().inflate(R.layout.info_window_layout, null);

                    TextView tvTitle = (TextView) v.findViewById(R.id.tv_title);
                    TextView tvLng = (TextView) v.findViewById(R.id.tv_info);
                    ImageView photoView = (ImageView) v.findViewById(R.id.photoView);

                    try {

                        Bitmap bitmap = ImageResizer.decodeSampledBitmapFromFile(mark.getImageURI(), 120, 120);
                        photoView.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        v.removeView(photoView);
                    }

                    tvTitle.setText("Marker No.: " + marker.getTitle());
                    tvLng.setText(mark.getInfoString());

                    return v;
                } catch (Exception e){
                    return null;
                }
            }
        });
    }



    // if the user input the initial and/or final address, need to put a marker on map
    private void setPositionOnMap(String title, String location){

        if (location.length() == 0)
            return;

        Geocoder geocoder = new Geocoder(getBaseContext());
        List<Address> addresses;
        try {
            addresses = geocoder.getFromLocationName(location, 1);
            if(addresses.size() > 0){
                if(mMap != null) {
                    mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(addresses.get(0).getLatitude(), addresses.get(0).getLongitude()))
                            .title(title));
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(addresses.get(0).getLatitude(), addresses.get(0).getLongitude())));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private void makeMark(){

        if (currentLocation == null
                || System.currentTimeMillis() - currentLocationTimestamp > validityOfCurrentLocation) {

            //if the location is no longer valid, inform the user
            CharSequence text = "Cannot get the current position.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(getApplicationContext(), text, duration);
            toast.show();

        } else {

            ItineraryPointMark mark = new ItineraryPointMark();

            mark.setPosition(currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    currentLocation.getAltitude());
            mark.setBatteryLevel(getCurrentBatteryPercentage());
            mark.setMode(mode);
            if(!itineraryPointMarks.isEmpty())
                mark.setPreviousMark(itineraryPointMarks.get(itineraryPointMarks.size() - 1));
            itineraryPointMarks.add(mark);

            addWifiInfo(itineraryPointMarks.indexOf(mark));
            takePhoto(itineraryPointMarks.indexOf(mark));
            addMarkerToMap(mark); // add a marker
        }

        resetCountDownTimer();
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

                    itineraryPointMarks.get(markIndex).setWifiInfo(wifiInfo);

                    CharSequence text = "Wifi points found.";
                    int duration = Toast.LENGTH_LONG;
                    Toast toast = Toast.makeText(getApplicationContext(), text, duration);
                    toast.show();

                }

                unregisterReceiver(this);
            }
        }, i );

        wifiManager.startScan();

    }

    // This method will let the user take a mark immediately. It will reset the count down at the same time
    public void makeMark(View view) {
        makeMark();
    }

    private void addMarkerToMap(ItineraryPointMark mark){
        if (mMap != null){
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(mark.getPosition()[0], mark.getPosition()[1]))
                    .title(itineraryPointMarks.size() - 1 + ""));
            if(itineraryPointMarks.size() >=2) {
                ItineraryPointMark p1 = itineraryPointMarks.get(itineraryPointMarks.size()-2);
                ItineraryPointMark p2 = itineraryPointMarks.get(itineraryPointMarks.size()-1);
                double lat1 = p1.getPosition()[0];
                double lng1 = p1.getPosition()[1];
                double lat2 = p2.getPosition()[0];
                double lng2 = p2.getPosition()[1];
                PolylineOptions lineOptions = new PolylineOptions()
                        .add(new LatLng(lat1, lng1))
                        .add(new LatLng(lat2, lng2));
                mMap.addPolyline(lineOptions);
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
        // GPS provider is for test
        String locationProvider = LocationManager.GPS_PROVIDER;
        locationManager.requestLocationUpdates(locationProvider, 0, 0, locationListener);
        locationProvider = LocationManager.NETWORK_PROVIDER;
        locationManager.requestLocationUpdates(locationProvider, 0, 0, locationListener);

    }

    private float getCurrentBatteryPercentage(){
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus =  getApplicationContext().registerReceiver(null, intentFilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level / (float)scale;
    }

    private void updateLocation(Location location){
        currentLocation = location;
        currentLocationTimestamp = System.currentTimeMillis();
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
    }

    private void takePhoto(final int index){
        new AlertDialog.Builder(this).setTitle("Take a photo?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeToImageCaptureActivity(index);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .show();
    }

    private void changeToImageCaptureActivity(int index){

        // create Intent to take a picture and return control to the calling application
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        Uri fileUri = Uri.fromFile(getOutputMediaFile(MEDIA_TYPE_IMAGE)); // create a file to save the image
        itineraryPointMarks.get(index).setImageURI(fileUri.getPath());
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

    // This method will finish this recording, store the results in database.
    public void finishRecorder(View view){
        if(itineraryPointMarks.size() != 0) try {
            int id;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(itineraryPointMarks);
            oos.flush();
            oos.close();
            bos.close();

            byte[] data = bos.toByteArray();

            SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);
            ContentValues values = new ContentValues();
            values.put("itineraryDate", System.currentTimeMillis());
            values.put("itineraryMarks", data);
            id = (int) db.insert("itineraries", null, values);

            Intent intent = new Intent(this, ItineraryReviewActivity.class);
            intent.putExtra("recordId", id);
            startActivity(intent);

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}
