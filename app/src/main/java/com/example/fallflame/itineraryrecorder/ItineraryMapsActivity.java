package com.example.fallflame.itineraryrecorder;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Chronometer;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ItineraryMapsActivity extends FragmentActivity {

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
        setContentView(R.layout.activity_itinerary_maps);
        setUpMapIfNeeded();
        resetCountDownTimer();
        registerLocationListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
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

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
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

                // Getting view from the layout file info_window_layout
                View v = getLayoutInflater().inflate(R.layout.info_window_layout, null);

                // Getting the position from the marker
                LatLng latLng = marker.getPosition();

                // Getting reference to the TextView to set latitude
                TextView tvLat = (TextView) v.findViewById(R.id.tv_lat);

                // Getting reference to the TextView to set longitude
                TextView tvLng = (TextView) v.findViewById(R.id.tv_lng);

                // Setting the latitude
                tvLat.setText("Latitude:" + latLng.latitude);

                // Setting the longitude
                tvLng.setText("Longitude:"+ latLng.longitude);

                // Returning the view containing InfoWindow contents
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
                                .title("I-" + itineraryMarks.size()));
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
}
