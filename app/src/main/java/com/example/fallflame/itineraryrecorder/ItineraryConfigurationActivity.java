package com.example.fallflame.itineraryrecorder;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;


public class ItineraryConfigurationActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuration);
    }

    public void beginItinerary(View view) {

        EditText initialPosition = (EditText) findViewById(R.id.initialPosition);
        EditText finalPosition = (EditText) findViewById(R.id.finalPosition);
        //RadioButton modeGPS = (RadioButton) findViewById(R.id.GPSRadioButton);
        RadioButton modeCellular = (RadioButton) findViewById(R.id.CellularRadioButton);
        EditText intervalMinET = (EditText) findViewById(R.id.intervalMin);
        EditText zoomLevelET = (EditText) findViewById(R.id.zoomLevel);

        int interval;
        int zoomLevel;


        // return when the input is not valid
        try{
            interval = Integer.parseInt(intervalMinET.getText().toString()) * 60;
            zoomLevel = Integer.parseInt(zoomLevelET.getText().toString());
        } catch (Exception e) {
            // return when the input is not valid
            return;
        }

        // return when the input is not valid
        if (zoomLevel < 1 || zoomLevel > 20 )
            return;

        Intent intent = new Intent(this, ItineraryRecordingActivity.class);

        if(modeCellular.isChecked())
            intent.putExtra("mode", "Cellular");

        intent.putExtra("initialPosition", initialPosition.getText().toString());
        intent.putExtra("finalPosition", finalPosition.getText().toString());
        intent.putExtra("interval", interval);
        intent.putExtra("zoomLevel", zoomLevel);

        finish();

        startActivity(intent);

    }
}
