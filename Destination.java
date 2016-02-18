package com.lonsdale.fuzzynavigationsystem;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.EditText;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class Destination extends AppCompatActivity {

    public final static String EXTRA_LAT = "com.lonsdale.fuzzynavigationsystem.LAT";
    public final static String EXTRA_LON = "com.lonsdale.fuzzynavigationsystem.LON";
    public final static String EXTRA_ADD = "com.lonsdale.fuzzynavigationsystem.ADD";

    Geocoder geocoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_destination);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        geocoder = new Geocoder(this, Locale.getDefault());
    }

    public void sendDestination(View view) throws IOException {
        Intent intent = new Intent(this, Journey.class);
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String message = editText.getText().toString();

        if (Geocoder.isPresent()) {
            List<Address> addresses = geocoder.getFromLocationName(message, 1);
            Address address = addresses.get(0);
            double latitude = address.getLatitude();
            double longitude = address.getLongitude();

            String add = "";
            for (int i=0;i<=address.getMaxAddressLineIndex();i++){
                if (i != 0){
                    add += ", ";
                }
                add += address.getAddressLine(i);
            }

            String lat = String.valueOf(latitude);
            String lon = String.valueOf(longitude);
            intent.putExtra(EXTRA_ADD, add);
            intent.putExtra(EXTRA_LAT, lat);
            intent.putExtra(EXTRA_LON, lon);
        } else {
            System.out.println("No geocoder");
        }

        startActivity(intent);
    }

}
