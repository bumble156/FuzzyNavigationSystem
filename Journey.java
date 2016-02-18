package com.lonsdale.fuzzynavigationsystem;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class Journey extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleApiClient mGoogleApiClient;
    public static final String TAG = Journey.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private LocationRequest mLocationRequest;
    String API = "AIzaSyDTn1RCzQ9EnrZhtJFONmWrO0V1DeMTOso";

    String currentLat;
    String currentLon;
    String destLat;
    String destLon;
    int distToDest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Pass in destination information
        Intent intent = getIntent();
        destLat = intent.getStringExtra(Destination.EXTRA_LAT);
        destLon = intent.getStringExtra(Destination.EXTRA_LON);
        String destAdd = intent.getStringExtra(Destination.EXTRA_ADD);

        TextView dLatTextView = (TextView) findViewById(R.id.dLatTextView);
        dLatTextView.append(" " + destLat);
        TextView dLonTextView = (TextView) findViewById(R.id.dLonTextView);
        dLonTextView.append(" " + destLon);
        TextView dAddTextView = (TextView) findViewById(R.id.dAddTextView);
        dAddTextView.append(" " + destAdd);

        //Get current location information
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(10 * 1000); // 10 second, in milliseconds

        mGoogleApiClient.connect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_journey, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Location services connected.");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (location == null) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
        else {
            handleNewLocation(location);
        };
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services suspended. Please reconnect.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    private void handleNewLocation(Location location) {

        Log.d(TAG, location.toString());
        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();

        currentLat = String.valueOf(currentLatitude);
        currentLon = String.valueOf(currentLongitude);

        TextView cLatTextView = (TextView) findViewById(R.id.cLatTextView);
        cLatTextView.append(" " + currentLat);
        TextView cLonTextView = (TextView) findViewById(R.id.cLonTextView);
        cLonTextView.append(" " + currentLon);

        new DistanceTask().execute();

        TextView distanceTextView = (TextView) findViewById(R.id.distanceTextView);
        distanceTextView.append(" " + distToDest);
    }

    @Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }

    public void goBack(View view) {
        Intent intent = new Intent(this, Destination.class);
        startActivity(intent);
    }

    private int getDistance() throws IOException {

        int dist = 0;

        //Make Google Distance Matrix API request
        StringBuilder urlString = new StringBuilder();
        urlString.append("https://maps.googleapis.com/maps/api/distancematrix/json?");
        urlString.append("origins=");//from
        urlString.append(currentLat);
        urlString.append(",");
        urlString.append(currentLon);
        urlString.append("&destinations=");//to
        urlString.append(destLat);
        urlString.append(",");
        urlString.append(destLon);
        urlString.append("&key=");
        urlString.append(API);

        HttpURLConnection urlConnection= null;
        URL url = null;

        try {
            url = new URL(urlString.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.connect();

            InputStream inStream = urlConnection.getInputStream();
            BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));

            String temp, response = "";
            while ((temp = bReader.readLine()) != null) {
                //Parse data
                response += temp;
            }
            //Close the reader, stream & connection
            bReader.close();
            inStream.close();
            urlConnection.disconnect();

            JSONObject object = new JSONObject(response);
            JSONArray rows = object.getJSONArray("rows");
            JSONArray elements = rows.getJSONObject(0).getJSONArray("elements");
            JSONObject distance = elements.getJSONObject(0).getJSONObject("distance");
            dist = distance.getInt("value");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println("Dist: " + dist);
        return dist;
    }

    private class DistanceTask extends AsyncTask<Void,Void,Void> {
        protected Void doInBackground(Void... params) {
            try {
                distToDest = getDistance();
                System.out.println("DistToDest: " + distToDest);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }
}
