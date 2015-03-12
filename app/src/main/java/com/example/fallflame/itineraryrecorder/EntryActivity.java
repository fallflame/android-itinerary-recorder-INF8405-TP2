package com.example.fallflame.itineraryrecorder;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;


public class EntryActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);

        // Create the databases needed for this application
        // must be done before the program start
        createDatabaseIfNeeded();
    }

    private void createDatabaseIfNeeded(){
        SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS itineraries (_id INTEGER PRIMARY KEY AUTOINCREMENT, itineraryDate INTEGER, itineraryMarks BLOB)");
        db.close();
    }

    public void beginNewItinerary(View view) {
        Intent intent = new Intent(this, ItineraryConfigurationActivity.class);
        startActivity(intent);
    }

    public void consultHistoryItineraries(View view){
        Intent intent = new Intent(this, HistoriesActivity.class);
        startActivity(intent);
    }

    // double touch back button to quit the application
    private long exitTime = 0;
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN){
            if((System.currentTimeMillis()-exitTime) > 2000){
                Toast.makeText(getApplicationContext(), "Touch back again to Quit.", Toast.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
