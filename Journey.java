package com.lonsdale.fuzzynavigationsystem;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.Toast;

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
import java.text.DateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Journey extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleApiClient mGoogleApiClient;
    public static final String TAG = Journey.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private LocationRequest mLocationRequest;
    Location mCurrentLocation;
    String API = "AIzaSyDTn1RCzQ9EnrZhtJFONmWrO0V1DeMTOso";
    static int LOCATION_REFRESH_TIME_SECONDS = 10;
    static int DESTINATION_CLOSE_METRES = 1000;

    final ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    final Handler h = new Handler();
    int delay = 10000; //milliseconds
    Runnable r;

    final static String LOCATION_KEY = "location-key";
    final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";

    TextView refreshTextView;
    TextView distanceTextView;
    TextView cLatTextView;
    TextView cLonTextView;
    TextView dAddTextView;
    TextView dLonTextView;
    TextView dLatTextView;
    TextView refreshTime;

    String cLatitudeLabel;
    String cLongitudeLabel;
    String dLatitudeLabel;
    String dLongitudeLabel;
    String dAddressLabel;
    String distanceLabel;
    String refreshLabel;

    String destLat;
    String destLon;
    int distToDest;
    int prevDist;
    String mLastUpdateTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        cLatTextView = (TextView) findViewById(R.id.cLatitude);
        cLonTextView = (TextView) findViewById(R.id.cLongitude);
        dLatTextView = (TextView) findViewById(R.id.dLatitude);
        dLonTextView = (TextView) findViewById(R.id.dLongitude);
        dAddTextView = (TextView) findViewById(R.id.dAddress);
        distanceTextView = (TextView) findViewById(R.id.distance);
        refreshTextView = (TextView) findViewById(R.id.refreshTextView);
        refreshTime = (TextView) findViewById(R.id.refreshTime);

        cLatitudeLabel = getResources().getString(R.string.current_latitude);
        cLongitudeLabel = getResources().getString(R.string.current_longitude);
        dLatitudeLabel = getResources().getString(R.string.destination_latitude);
        dLongitudeLabel = getResources().getString(R.string.destination_longitude);
        dAddressLabel = getResources().getString(R.string.destination_address);
        distanceLabel = getResources().getString(R.string.distance);
        refreshLabel = getResources().getString(R.string.refresh_time);

        //Pass in destination information
        Intent intent = getIntent();
        destLat = intent.getStringExtra(Destination.EXTRA_LAT);
        destLon = intent.getStringExtra(Destination.EXTRA_LON);
        String destAdd = intent.getStringExtra(Destination.EXTRA_ADD);

        dLatTextView.setText(String.format("%s %s", dLatitudeLabel, destLat));
        dLonTextView.setText(String.format("%s %s", dLongitudeLabel, destLon));
        dAddTextView.setText(String.format("%s %s", dAddressLabel, destAdd));

        mLastUpdateTime = "";

        //Get current location information
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_REFRESH_TIME_SECONDS * 1000)        // in milliseconds
                .setFastestInterval(LOCATION_REFRESH_TIME_SECONDS * 1000); // in milliseconds

        playAudio();
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

    private void startLocationUpdates() {
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
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
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    private void updateUI() {
        cLatTextView.setText(String.format("%s %f", cLatitudeLabel, mCurrentLocation.getLatitude()));
        cLonTextView.setText(String.format("%s %f", cLongitudeLabel, mCurrentLocation.getLongitude()));
        if (distToDest == 0){
            distanceTextView.setText(String.format("%s %s", distanceLabel, "Calculating distance"));
        } else {
            distanceTextView.setText(String.format("%s %s %s", distanceLabel, distToDest, "metres"));
        }
        refreshTextView.setText(String.format("%s %s", refreshLabel, mLastUpdateTime));
        refreshTime.setText(String.format("%s %s", "Refresh Time", LOCATION_REFRESH_TIME_SECONDS));
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mCurrentLocation == null) {
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
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            updateUI();
        }

        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
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

    private void handleNewLocation(Location location) throws IOException {

        Log.d(TAG, location.toString());
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        if (distToDest < DESTINATION_CLOSE_METRES && distToDest!=0){
            LOCATION_REFRESH_TIME_SECONDS = 5;
            locationClose();
            playAudio();
        } else {
            LOCATION_REFRESH_TIME_SECONDS = 10;
            delay = 10000;
            locationFar();
        }
        updateUI();

        new DistanceTask().execute();
    }

    private void locationClose() {

        if (distToDest > (DESTINATION_CLOSE_METRES*4)/5 ){
            delay = 5000;
        } else if (distToDest <= (DESTINATION_CLOSE_METRES*4)/5 && distToDest > (DESTINATION_CLOSE_METRES*3)/5){
            delay = 2500;
        } else if (distToDest <= (DESTINATION_CLOSE_METRES*3)/5 && distToDest > (DESTINATION_CLOSE_METRES*2)/5){
            delay = 1000;
        } else if (distToDest <= (DESTINATION_CLOSE_METRES*2)/5 && distToDest > (DESTINATION_CLOSE_METRES*1)/5){
            delay = 500;
        } else {
            delay = 250;
        }

    }

    private void locationFar() {
        if(prevDist > distToDest){
            playAudio();
        } else {
            stopAudio();
        }
    }

    private void playAudio(){

        r = new Runnable(){
            public void run(){
                toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                h.postDelayed(this, delay);
            }
        };

        h.postDelayed(r, delay);
    }

    private void stopAudio(){
        h.removeCallbacks(r);
        //toneGen1.stopTone();
    }

    @Override
    public void onLocationChanged(Location location) {
        try {
            handleNewLocation(location);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void goBack(View view) {
        stopAudio();
        Intent intent = new Intent(this, Destination.class);
        startActivity(intent);
    }

    private int getDistance() throws IOException {

        int dist = 0;

        //Make Google Distance Matrix API request
        StringBuilder urlString = new StringBuilder();
        urlString.append("https://maps.googleapis.com/maps/api/distancematrix/json?");
        urlString.append("origins=");//from
        urlString.append(mCurrentLocation.getLatitude());
        urlString.append(",");
        urlString.append(mCurrentLocation.getLongitude());
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

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return dist;
    }

    private class DistanceTask extends AsyncTask<Void,Void,Void> {
        protected Void doInBackground(Void... params) {
            try {
                prevDist = distToDest;
                distToDest = getDistance();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }
}
