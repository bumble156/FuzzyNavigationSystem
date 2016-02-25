package com.lonsdale.fuzzynavigationsystem;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;

public class Journey extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    //Backend stuff

    private GoogleApiClient mGoogleApiClient;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    String API = "AIzaSyDTn1RCzQ9EnrZhtJFONmWrO0V1DeMTOso";

    //Location stuff

    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;
    String destLat; //Latitude of destination
    String destLon; //Longitude of destination
    int distToDest; //Distance to destination (meters)
    int prevDist; //Distance to destination (meters) from previous location
    String mLastUpdateTime; //Last time that location was updated

    //Logging & intent stuff

    public static final String TAG = Journey.class.getSimpleName();
    public final static String EXTRA_NO_ROUTE = "com.lonsdale.fuzzynavigationsystem.EXTRA_NO_ROUTE";

    //Audio stuff

    final ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); //Audio data of beep
    final Handler h = new Handler();
    Runnable r;
    boolean isRunning; //Whether the audio track is currently playing or not

    //TextViews

    TextView cLatTextView;
    TextView cLonTextView;
    TextView dLatTextView;
    TextView dLonTextView;
    TextView dAddTextView;
    TextView distanceTextView;
    TextView refreshTextView;
    TextView refreshTime;

    //Labels

    String cLatitudeLabel;
    String cLongitudeLabel;
    String dLatitudeLabel;
    String dLongitudeLabel;
    String dAddressLabel;
    String distanceLabel;
    String refreshLabel;

    //Variables

    static int AUDIO_DELAY = 10000; //milliseconds
    static int DESTINATION_CLOSE_METRES = 300; //How close the destination should be to be considered 'close'
    static int LOCATION_REFRESH_TIME_SECONDS = 10; //How often location gets refreshed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Keeps screen on to avoid starting multiple audio threads when screen goes off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Find all text views in the activity
        cLatTextView = (TextView) findViewById(R.id.cLatitude);
        cLonTextView = (TextView) findViewById(R.id.cLongitude);
        dLatTextView = (TextView) findViewById(R.id.dLatitude);
        dLonTextView = (TextView) findViewById(R.id.dLongitude);
        dAddTextView = (TextView) findViewById(R.id.dAddress);
        distanceTextView = (TextView) findViewById(R.id.distance);
        refreshTextView = (TextView) findViewById(R.id.refreshTextView);
        refreshTime = (TextView) findViewById(R.id.refreshTime);

        //Set labels for all text views in the activity
        cLatitudeLabel = getResources().getString(R.string.current_latitude);
        cLongitudeLabel = getResources().getString(R.string.current_longitude);
        dLatitudeLabel = getResources().getString(R.string.destination_latitude);
        dLongitudeLabel = getResources().getString(R.string.destination_longitude);
        dAddressLabel = getResources().getString(R.string.destination_address);
        distanceLabel = getResources().getString(R.string.distance);
        refreshLabel = getResources().getString(R.string.refresh_time);

        //Pass in destination information from intent
        Intent intent = getIntent();
        destLat = intent.getStringExtra(Destination.EXTRA_LAT);
        destLon = intent.getStringExtra(Destination.EXTRA_LON);
        String destAdd = intent.getStringExtra(Destination.EXTRA_ADD);

        //Add destination information to activity once it has been found
        dLatTextView.setText(String.format("%s %s", dLatitudeLabel, destLat));
        dLonTextView.setText(String.format("%s %s", dLongitudeLabel, destLon));
        dAddTextView.setText(String.format("%s %s", dAddressLabel, destAdd));

        //Set initial variables
        mLastUpdateTime = "";
        isRunning = false;

        //Get current location information
        //Set up new API client
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        //Create new location request
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_REFRESH_TIME_SECONDS * 1000)        // in milliseconds
                .setFastestInterval(LOCATION_REFRESH_TIME_SECONDS * 1000); // in milliseconds
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
        //Checks all permissions are set
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //returns if no permissions
            return;
        }
        //Requests location updates from the API client
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    private void updateUI() {
        //Updates all text views in the UI
        cLatTextView.setText(String.format("%s %f", cLatitudeLabel, mCurrentLocation.getLatitude()));
        cLonTextView.setText(String.format("%s %f", cLongitudeLabel, mCurrentLocation.getLongitude()));

        //Displays distance to destination when calculated
        if (distToDest == 0){
            distanceTextView.setText(String.format("%s %s", distanceLabel, "Calculating distance"));
        } else {
            distanceTextView.setText(String.format("%s %s %s", distanceLabel, distToDest, "metres"));
        }

        refreshTextView.setText(String.format("%s %s", refreshLabel, mLastUpdateTime));
        refreshTime.setText(String.format("%s %s %s %s %s %s", "Refresh Time", LOCATION_REFRESH_TIME_SECONDS, "Delay", AUDIO_DELAY, "Progress Made", isRunning));
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
            //Checks for permissions
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //Gets initial location from phone
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
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
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

        //Called when the current phone location is updated

        Log.d(TAG, location.toString());

        //Setting current location
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        //Checking whether location is 'close' to destination or 'far'
        if (distToDest < DESTINATION_CLOSE_METRES && distToDest!=0){
            LOCATION_REFRESH_TIME_SECONDS = 5;
            locationClose();
        } else {
            if (distToDest != 0){
                LOCATION_REFRESH_TIME_SECONDS = 10;
                AUDIO_DELAY = 10000;
                locationFar();
            } else {
                // log 'lost connection'
            }
        }
        updateUI();

        //Starts another task to calculate new distance from destination using newly found location
        new DistanceTask().execute();
    }

    private void locationClose() {

        //Sets delay for audio to increase frequency of beep as destination nears

        if (distToDest > (DESTINATION_CLOSE_METRES*4)/5 ){
            AUDIO_DELAY = 5000;
        } else if (distToDest <= (DESTINATION_CLOSE_METRES*4)/5 && distToDest > (DESTINATION_CLOSE_METRES*3)/5){
            AUDIO_DELAY = 2500;
        } else if (distToDest <= (DESTINATION_CLOSE_METRES*3)/5 && distToDest > (DESTINATION_CLOSE_METRES*2)/5){
            AUDIO_DELAY = 1000;
        } else if (distToDest <= (DESTINATION_CLOSE_METRES*2)/5 && distToDest > (DESTINATION_CLOSE_METRES)/5){
            AUDIO_DELAY = 500;
        } else {
            AUDIO_DELAY = 250;
        }

        //Starts audio track if it is not already playing (if destination starts off 'close')
        if (!isRunning){
            playAudio();
        }
    }

    private void locationFar() {

        if(prevDist > distToDest && prevDist != 0){
            //if progress is being made on the journey keep playing audio
            if (!isRunning){
                playAudio();
            }
        } else {
            //no progress made, pause audio
            if (isRunning){
                stopAudio();
            }
        }
    }

    private void playAudio(){

        //Plays a single beeping sound
        r = new Runnable() {
            public void run() {
                isRunning = true;
                toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                h.postDelayed(this, AUDIO_DELAY);
            }
        };

        //Recursively repeats beeping sound after a delay
        h.postDelayed(r, AUDIO_DELAY);
    }

    private void stopAudio(){
        //Stops the audio from playing
        isRunning = false;
        h.removeCallbacks(r);
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
        //Handles the user pressing the back button
        stopAudio();
        Intent backToDest = new Intent(this, Destination.class);
        startActivity(backToDest);
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

        HttpURLConnection urlConnection;
        URL url;

        //Attempt to calculate the distance between the current location and the destination using the previous URL
        try {
            //Set up URL connection
            url = new URL(urlString.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.connect();

            //Set up readers for response
            InputStream inStream = urlConnection.getInputStream();
            BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));

            //Parse response from API as JSON
            String temp, response = "";
            while ((temp = bReader.readLine()) != null) {
                response += temp;
            }

            //Close reader, stream & connection
            bReader.close();
            inStream.close();
            urlConnection.disconnect();

            //Parse JSON to get distance of fastest route in meters
            JSONObject object = new JSONObject(response);
            JSONArray rows = object.getJSONArray("rows");
            JSONArray elements = rows.getJSONObject(0).getJSONArray("elements");
            JSONObject distance = elements.getJSONObject(0).getJSONObject("distance");
            dist = distance.getInt("value");

        } catch (JSONException e) {
            //If there is no route between points
            e.printStackTrace();
            //Send user back to Destination page and generate toast with information about problem
            Intent noRoute = new Intent(this, Destination.class);
            noRoute.putExtra(EXTRA_NO_ROUTE, getResources().getString(R.string.no_route_error));
            startActivity(noRoute);
        } catch (IOException e){
            e.printStackTrace();
        }
        return dist;
    }

    private class DistanceTask extends AsyncTask<Void,Void,Void> {
        protected Void doInBackground(Void... params) {
            try {
                //Set distance from previous location to assess progress
                if (distToDest != 0) {
                    prevDist = distToDest;
                }
                //Set new distance from API call
                distToDest = getDistance();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }
}
