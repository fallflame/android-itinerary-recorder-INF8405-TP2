package com.example.fallflame.itineraryrecorder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;


public class HistoriesActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItinerariesFromDatabase();
    }

    public void cleanHistory(View view){
        final HistoriesActivity self = this;
        new AlertDialog.Builder(this).setTitle("Clean History?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteAllItineraries();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .show();

    }

    // Manipulate the database, but we don't think need to use thread
    private void loadItinerariesFromDatabase(){

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);
        linearLayout.removeAllViewsInLayout();

        SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);
        Cursor c = db.rawQuery("SELECT _id, itineraryDate FROM itineraries ORDER BY itineraryDate DESC", null);


        while (c.moveToNext()) {
            final int id = c.getInt(c.getColumnIndex("_id"));
            String itineraryString = "Itinerary-" + id;
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            itineraryString += " : " + df.format(c.getLong(c.getColumnIndex("itineraryDate")));

            TextView textView = new TextView(getBaseContext());
            textView.setTextSize(16);
            textView.setPadding(0, 10, 0, 10);
            textView.setTextColor(0xff2ab1ff);
            textView.setText(itineraryString);
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Intent intent = new Intent(HistoriesActivity.this, ItineraryReviewActivity.class);
                    intent.putExtra("recordId", id);
                    startActivity(intent);
                }
            });

            linearLayout.addView(textView);
        }
        db.close();

        /* For this size of application, thread is not needed

        final Handler loadItinerariesFromDatabaseHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Map<Integer, String> data = (Map) msg.obj;
                LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);
                linearLayout.removeAllViewsInLayout();

                for (Map.Entry<Integer, String> entry : data.entrySet()) {
                    final int id = entry.getKey();
                    String itineraryString = entry.getValue();

                    TextView textView = new TextView(getBaseContext());
                    textView.setTextSize(16);
                    textView.setPadding(0, 10, 0, 10);
                    textView.setTextColor(0xff2ab1ff);
                    textView.setText(itineraryString);
                    textView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            Intent intent = new Intent(HistoriesActivity.this, ItineraryReviewActivity.class);
                            intent.putExtra("recordId", id);
                            startActivity(intent);
                        }
                    });

                    linearLayout.addView(textView);

                }
            }
        };

        new Thread(new Runnable() {
            @Override
            public void run() {
                Message msg = new Message();
                SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);
                Cursor c = db.rawQuery("SELECT _id, itineraryDate FROM itineraries ORDER BY itineraryDate DESC", null);

                Map<Integer, String> data = new HashMap<>();

                while (c.moveToNext()) {
                    int id = c.getInt(c.getColumnIndex("_id"));
                    String itineraryString = "Itinerary-" + id;
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    itineraryString += " : " + df.format(c.getLong(c.getColumnIndex("itineraryDate")));
                    data.put(id, itineraryString);
                }
                msg.obj = data;
                loadItinerariesFromDatabaseHandler.sendMessage(msg);
                db.close();
            }
        }).start();

        */
    }

    private void deleteAllItineraries(){
        SQLiteDatabase db = openOrCreateDatabase("itinerary.db", Context.MODE_PRIVATE, null);
        db.execSQL("DELETE FROM itineraries");
        LinearLayout linearLayout = (LinearLayout)findViewById(R.id.linearLayout);
        linearLayout.removeAllViews();
        db.close();
    }

    @Override
    // need to go back to the menu
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Intent intent = new Intent(this, EntryActivity.class);
        startActivity(intent);

        return true;
    }
}
