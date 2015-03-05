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
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ItineraryRecordedActivity extends FragmentActivity {

    // some constant use by android system
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;

    // Default values, for test
    final static private int DEFAULT_NO_ITINERARY = 1;
    final static private int DEFAULT_ZOOM_LEVEL = 12;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private ArrayList<ItineraryMark> itineraryMarks = new ArrayList<>();
    private ArrayList<BaseStationMark> baseStationMarks = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_itinerary_recorded);

        loadItinerary();
        setItineraryInfo();

        setUpMapIfNeeded();

    }

    private void loadItinerary(){
        SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);
        Cursor c = db.rawQuery("SELECT itineraryDate, itineraryMarks FROM itineraries WHERE _id = " + DEFAULT_NO_ITINERARY, null);

        while (c.moveToNext()){
            try {
                byte[] data = c.getBlob(c.getColumnIndex("itineraryMarks"));

                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bis);
                itineraryMarks = (ArrayList<ItineraryMark>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void setItineraryInfo(){
        LinearLayout linearLayout = (LinearLayout)findViewById(R.id.itineraryInfo);

        ArrayList<String> infos = new ArrayList<>();
        ItineraryMark firstMark = itineraryMarks.get(0);
        ItineraryMark lastMark = itineraryMarks.get(itineraryMarks.size()-1);

        infos.add("Mode: " + firstMark.getMode());
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        infos.add("Started Time: " + df.format(itineraryMarks.get(0).getCurrentTime()));
        int durationInSecond = (int)(lastMark.getCurrentTime() - firstMark.getCurrentTime()) / 1000;
        infos.add("Duration: " + (int)durationInSecond / 60 + "minutes, " + durationInSecond % 60 + "seconds.");
        double totalDistance = 0;
        for(ItineraryMark m : itineraryMarks){
            totalDistance += m.getDistanceFromPreviousMark();
        }
        infos.add("Total Distance: " + totalDistance + "meters.");
        infos.add("Battery Consumed: " + (int) (firstMark.getBatteryLevel() - lastMark.getBatteryLevel()) * 100 + "%.");

        for(String info : infos){
            TextView textView = new TextView(getBaseContext());
            textView.setTextColor(0xFF000000);
            textView.setText(info);
            linearLayout.addView(textView);
        }
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

        LatLng center = new LatLng(itineraryMarks.get(0).getPosition()[0], itineraryMarks.get(0).getPosition()[1]);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, DEFAULT_ZOOM_LEVEL));
        for(ItineraryMark mark : itineraryMarks){
            addMarkerToMap(mark);
        }

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

    private void addMarkerToMap(ItineraryMark mark){
        if (mMap != null){
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(mark.getPosition()[0], mark.getPosition()[1]))
                    .title(itineraryMarks.indexOf(mark) + ""));
            if(itineraryMarks.size() >=2 && itineraryMarks.indexOf(mark) != 0) {

                ItineraryMark previousMark = itineraryMarks.get(itineraryMarks.indexOf(mark) - 1);

                ItineraryMark p1 = previousMark;
                ItineraryMark p2 = mark;
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

}
