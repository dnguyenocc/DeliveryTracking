package com.leximit.deliverytracking;

import android.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.callbacks.PNCallback;
import com.pubnub.api.models.consumer.PNPublishResult;
import com.pubnub.api.models.consumer.PNStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{

    public static final String PUBLISH_KEY = "pub-c-e6c73b7b-37a9-4d60-835e-83c4e4b9a830";
    public static final String SUBSCRIBE_KEY = "sub-c-1d440f7c-0b02-11e6-a9bb-02ee2ddab7fe";
    public static final String CHANNEL_NAME = "leximit123";
    private static final String TAG = "tag";

    private static final int FINE_LOCATION_REQUEST_CODE = 100;

    private PubNub mPubNub;

    private GoogleApiClient mGoogleApiClient;
    // Member variable to store location request (how often to update)
    private LocationRequest mLocationRequest;
    // Member variable to store our location
    private Location myLocation;

    private EditText truckNameEditText;
    private String checkedString;
    private boolean broadcastStatus = false;
    private Button broadcastButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            mGoogleApiClient.connect();

        }
        // Define the interval for update:
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000)
                .setFastestInterval(1000);

        initPubNub();
        truckNameEditText = (EditText) findViewById(R.id.truckNameEditText);
        broadcastButton = (Button) findViewById(R.id.broadcastButton);
        checkedString = "";
    }

    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this).addApi(LocationServices.API)
                .build();
    }

    private final void initPubNub() {
        PNConfiguration config = new PNConfiguration();

        config.setPublishKey(PUBLISH_KEY);
        config.setSubscribeKey(SUBSCRIBE_KEY);
        config.setSecure(true);

        this.mPubNub = new PubNub(config);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},FINE_LOCATION_REQUEST_CODE);

        }
        myLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest,this);
        if (broadcastStatus) try {
            broadcastLocation(myLocation);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection to Google API suspended");
    }

    private LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(500);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }


    @Override
    public void onLocationChanged(Location location) {
        Log.d("Location Update", "Latitude: " + location.getLatitude() +
                " Longitude: " + location.getLongitude());
        if (broadcastStatus) try {
            broadcastLocation(location);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void broadcastLocation(Location location) throws JSONException {
        JSONObject message = new JSONObject();
        try {
            message.put("lat", location.getLatitude());
            message.put("lng", location.getLongitude());
            message.put("checkedString", checkedString.toLowerCase());
            message.put("carname",truckNameEditText.getText().toString());
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        mPubNub.publish()
                .message(toMap(message))
                .channel(CHANNEL_NAME)
                .async(new PNCallback<PNPublishResult>() {
                    @Override
                    public void onResponse(PNPublishResult result, PNStatus status) {
                        // handle publish result, status always present, result if successful
                        // status.isError() to see if error happened
                        if (!status.isError()) {
                            System.out.println("pub timetoken: " + result.getTimetoken());
                        }
                        System.out.println("pub status code: " + status.getStatusCode());
                    }
                });
    }

    public static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<>();

        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }
    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radioButton1:
                if (checked)
                    checkedString = "California";
                    break;
            case R.id.radioButton2:
                if (checked)
                    checkedString = "Texas";
                    break;
            case R.id.radioButton3:
                if (checked)
                    checkedString = "Georgia";
                    break;
            case R.id.radioButton4:
                if (checked)
                    checkedString = "Virginia";
                    break;
        }
    }

    public void broadcastOnClick (View view)
    {
        if (broadcastButton.getText().toString().equals("Start")) {
            if (!checkedString.isEmpty() && !truckNameEditText.getText().toString().isEmpty()) {
                broadcastStatus = true;
                broadcastButton.setText("Stop");
            } else {
                Toast.makeText(this, "Missing information", Toast.LENGTH_SHORT).show();
            }
        }
        else {
            broadcastStatus = false;
            broadcastButton.setText("Start");
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e("TAG", "Failed connection from google Play Service.");
    }
}