package com.example.fallflame.itineraryrecorder;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class ItineraryReviewActivity extends FragmentActivity {

    // Default values, for test
    final static private int DEFAULT_ZOOM_LEVEL = 18;
    private int id; //id of the record

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private ArrayList<ItineraryPointMark> itineraryPointMarks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_itinerary_recorded);

        id = getIntent().getIntExtra("recordId", -1);

        loadItinerary();
        setItineraryInfo();

        setUpMapIfNeeded();

    }

    private void loadItinerary(){
        SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);
        Cursor c = db.rawQuery("SELECT itineraryDate, itineraryMarks FROM itineraries WHERE _id = " + id, null);

        while (c.moveToNext()){
            try {
                byte[] data = c.getBlob(c.getColumnIndex("itineraryMarks"));

                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bis);
                itineraryPointMarks = (ArrayList<ItineraryPointMark>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void setItineraryInfo(){
        LinearLayout linearLayout = (LinearLayout)findViewById(R.id.itineraryInfo);

        ArrayList<String> infos = new ArrayList<>();
        ItineraryPointMark firstMark = itineraryPointMarks.get(0);
        ItineraryPointMark lastMark = itineraryPointMarks.get(itineraryPointMarks.size()-1);

        infos.add("Mode: " + firstMark.getMode());
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        infos.add("Started Time: " + df.format(itineraryPointMarks.get(0).getCurrentTime()));
        int durationInSecond = (int)(lastMark.getCurrentTime() - firstMark.getCurrentTime()) / 1000;
        infos.add("Duration: " + (int)durationInSecond / 60 + " minutes, " + durationInSecond % 60 + " seconds.");
        double totalDistance = 0;
        for(ItineraryPointMark m : itineraryPointMarks){
            totalDistance += m.getDistanceFromPreviousMark();
        }
        infos.add("Total Distance: " + (int)totalDistance + "meters.");
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

        LatLng center = new LatLng(itineraryPointMarks.get(0).getPosition()[0], itineraryPointMarks.get(0).getPosition()[1]);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, DEFAULT_ZOOM_LEVEL));
        for(ItineraryPointMark mark : itineraryPointMarks){
            addMarkerToMap(mark);
        }

        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {

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
            }
        });
    }

    private void addMarkerToMap(ItineraryPointMark mark){
        if (mMap != null){
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(mark.getPosition()[0], mark.getPosition()[1]))
                    .title(itineraryPointMarks.indexOf(mark) + ""));
            if(itineraryPointMarks.size() >=2 && itineraryPointMarks.indexOf(mark) != 0) {

                ItineraryPointMark previousMark = itineraryPointMarks.get(itineraryPointMarks.indexOf(mark) - 1);

                ItineraryPointMark p1 = previousMark;
                ItineraryPointMark p2 = mark;
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

    @Override
    // need to go back to the menu
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Intent intent = new Intent(this, HistoriesActivity.class);
        startActivity(intent);

        return true;
    }

}
