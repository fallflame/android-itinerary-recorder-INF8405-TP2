package com.example.fallflame.itineraryrecorder;

import android.app.AlertDialog;
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
import android.os.BatteryManager;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

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

        }

        resetCountDownTimer();
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

        Intent intent = new Intent(this, ItineraryRecordedActivity.class);
        startActivity(intent);
    }
}
