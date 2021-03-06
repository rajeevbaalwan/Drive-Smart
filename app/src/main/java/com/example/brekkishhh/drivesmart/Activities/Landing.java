package com.example.brekkishhh.drivesmart.Activities;

import android.Manifest;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.brekkishhh.drivesmart.Db.DbHelper;
import com.example.brekkishhh.drivesmart.R;
import com.example.brekkishhh.drivesmart.Services.MessageService;
import com.example.brekkishhh.drivesmart.Utils.Constants;
import com.example.brekkishhh.drivesmart.Utils.Tracking;
import com.example.brekkishhh.drivesmart.Utils.UtilClass;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Landing extends AppCompatActivity implements OnMapReadyCallback,GoogleMap.OnMarkerClickListener,SensorEventListener {

    private MapFragment mainMap;
    private Tracking tracking;
    private static final String TAG = "Landing Activity";
    private GoogleMap googleMap;
    private int radiusOfSearch = 20;     //this radius is in kms.
    private LatLng userLocation;
    private SensorManager sensorManager;
    private float acceleration;
    private float accelerationCurrent;
    private float accelerationLast;
    private final static int RC_SEND_MESSAGE = 574;
    public final static int RC_GET_PERMISSION = 10005;
    private DbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);

        mainMap = MapFragment.newInstance();         //getting instance of the map object
        mainMap.getMapAsync(this);         //registering onMapReadyCallback
        this.addMapToLayout();                   //this method adds the map to the layout
        tracking = new Tracking(Landing.this);
        dbHelper = new DbHelper(Landing.this);

        if (ContextCompat.checkSelfPermission(Landing.this, Manifest.permission.SEND_SMS ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(Landing.this, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(Landing.this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED){
            Log.e(TAG,"Permission Not Granted For Sending Sms");

            ActivityCompat.requestPermissions(Landing.this,new String[]{Manifest.permission.SEND_SMS,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},RC_GET_PERMISSION);
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_NORMAL);
        acceleration= 0.00f;
        accelerationCurrent = SensorManager.GRAVITY_EARTH;
        accelerationLast = SensorManager.GRAVITY_EARTH;
    }

    private void addMapToLayout() {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.add(R.id.mapContainer, mainMap).commit();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        this.googleMap = googleMap;
        googleMap.setOnMarkerClickListener(this);
        userLocation = tracking.fetchUserLocation();

        if (userLocation == null) {
            return;
        }
        googleMap.addMarker(new MarkerOptions()
                .position(userLocation)
                .title("I am Stuck Here")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        zoomToMarker(userLocation);

        getNearbyCarServices(userLocation);
    }

    public void getNearbyCarServices(LatLng userLatLang) {

        String placeSearchApiUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" + userLatLang.latitude + "," + userLatLang.longitude + "&radius=" + radiusOfSearch + "000&type=car_repair&key=" + getString(R.string.place_search_api_key);

        OkHttpClient httpClient = new OkHttpClient();
        final List<LatLng> responses = new ArrayList<>();

        httpClient.newCall(new Request.Builder()
                .url(placeSearchApiUrl)
                .build()).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UtilClass.toastL(Landing.this, "Unable to get nearby car repair services");
                    }
                });

            }

            @Override
            public void onResponse(Response response) throws IOException {
                JsonArray locationArray = null;
                JsonParser jsonParser = new JsonParser();
                JsonObject jsonObject = jsonParser.parse(response.body().charStream()).getAsJsonObject();

                locationArray = jsonObject.getAsJsonArray("results");
                for (int objects = 0; objects < locationArray.size(); objects++) {
                    JsonObject geometry = locationArray.get(objects).getAsJsonObject().getAsJsonObject("geometry");
                    JsonObject location = geometry.getAsJsonObject("location");
                    double latitude = location.get("lat").getAsDouble();
                    double longitude = location.get("lng").getAsDouble();
                    responses.add(new LatLng(latitude, longitude));
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateMapWithNewCentres(responses);
                    }
                });

            }
        });
    }

    private void updateMapWithNewCentres(List<LatLng> carServices) {

        //Calculating Bounds For Making Camera TO Fit for all markers
        LatLngBounds.Builder boundBuilder = new LatLngBounds.Builder();

        for (LatLng pos : carServices) {
            boundBuilder.include(pos);
            googleMap.addMarker(new MarkerOptions()
                    .title("I am here to help you")
                    .position(pos)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }

        if (carServices.size() != 0) {
            LatLngBounds bounds = boundBuilder.build();
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100, 100, 5);
            googleMap.animateCamera(cameraUpdate);
        }
    }

    private void zoomToMarker(LatLng marker) {

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(marker)
                .zoom(10)
                .build();

        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_landing, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.changeRadius) {

            final EditText editText = new EditText(Landing.this);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);

            AlertDialog dialog = new AlertDialog.Builder(Landing.this)
                    .setMessage("Input Radius For The Search in kms.")
                    .setPositiveButton("Search", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (editText.getText().toString().length() == 0 || editText.getText().toString().equals("")) {
                                return;
                            }

                            radiusOfSearch = Integer.parseInt(editText.getText().toString());
                            getNearbyCarServices(userLocation);
                        }
                    }).create();

            dialog.setView(editText);

            dialog.show();

            return true;
        }

        else if (item.getItemId() == R.id.settings){

            AlertDialog dialog = new AlertDialog.Builder(Landing.this)
                    .setCancelable(false)
                    .create();

            final LinearLayout layout = getUserInputLayout();

           // dialog.setMessage(getString(R.string.add_numbers));
            dialog.setView(layout,30,20,30,0);

            dialog.setButton(Dialog.BUTTON_POSITIVE, "ADD CONTACT", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    EditText phone = (EditText) layout.getChildAt(2);
                    EditText contact = (EditText) layout.getChildAt(1);
                    if (contact.getText().toString().length()>0 && phone.getText().toString().length() == Constants.MAX_LENGTH_PHONE){
                        dbHelper.addEntryToDb(contact.getText().toString(),phone.getText().toString());
                        dialog.cancel();
                        return;
                    }

                    UtilClass.toastS(Landing.this,"Enter Valid Details");
                }
            });


            dialog.show();

            return true;
        }

        else if (item.getItemId()==R.id.showEmergencyNumbers){

            AlertDialog.Builder dialog = new AlertDialog.Builder(Landing.this);


            dialog.setCancelable(false);

            dialog.setAdapter(new ArrayAdapter<String>(Landing.this,
                    android.R.layout.simple_list_item_1, dbHelper.retrieveInfoFromDb()), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    UtilClass.toastS(Landing.this,"");
                }
            });

            TextView title = new TextView(Landing.this);
            title.setText(getString(R.string.all_emergency_numbers));
            title.setTextColor(Color.BLACK);
            title.setPadding(0,30,0,0);
            title.setGravity(Gravity.CENTER);
            title.setTextSize(20);
            dialog.setCustomTitle(title);
            dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });


            dialog.create().show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {

        LatLng markerLocation = marker.getPosition();

        String extra = markerLocation.latitude+","+markerLocation.longitude;
        Intent showDescription = new Intent(Landing.this,CarRepairDescription.class);
        showDescription.putExtra("latlng",extra);
        startActivity(showDescription);
        return true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        accelerationLast = accelerationCurrent;
        accelerationCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
        float delta = accelerationCurrent - accelerationLast;
        acceleration = acceleration * 0.9f + delta; // perform low-cut filter

        if (acceleration > 8) {

            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);

            try{
                sendMessageToServers("I am lost here Please Help me : " + userLocation.latitude + " " + userLocation.longitude);
            }catch (NullPointerException ex){
                Log.e(TAG,ex.getMessage());
            }

        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }
    private void sendMessageToServers(String userAddress){

        List<DbHelper.User> numberList = dbHelper.retrieveListFromDb();

        for (DbHelper.User user : numberList){
            Intent intent = new Intent(this, MessageService.class);
            intent.putExtra("contactName",user.getName());

            PendingIntent smsPendingIntent = PendingIntent.getActivity(Landing.this,RC_SEND_MESSAGE,intent,0);
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage("+91"+user.getPhone(),"",userAddress,smsPendingIntent,null);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);


    }

    private LinearLayout getUserInputLayout(){

        LinearLayout layout = new LinearLayout(Landing.this);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText contact = new EditText(Landing.this);
        EditText phone = new EditText(Landing.this);
        TextView title = new TextView(Landing.this);

        title.setText(getString(R.string.add_numbers));
        title.setTextColor(Color.BLACK);
        title.setPadding(0,20,0,20);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(18);


        contact.setHint("Enter Contact Name");
        phone.setHint("Enter Contact Number");


        contact.setInputType(InputType.TYPE_CLASS_TEXT);
        phone.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(title,0,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(contact , 1,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(phone,2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return layout;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RC_GET_PERMISSION && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
            UtilClass.toastS(Landing.this,"Permission Granted");
        }

        else {
            UtilClass.toastL(Landing.this,"We need permissions to do the further task");
            this.finish();
        }
    }
}
