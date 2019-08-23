package com.bus.tracker;

import android.Manifest;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Whereto extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    public GoogleMap mMap;
    LinearLayout locatemelayout;
    LocationManager locationManager;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    SupportMapFragment mapFragment;
    TextView find_bus;
    LatLng userlatlnglocation,pickuplocation,destinationlatlng;
    Geocoder geocoder;
    Boolean requestbol = false;
    Marker pickupmarker;
    ImageView addplus;
    String destination;
    LinearLayout mdriverinfo;
    ImageView driverprofileimage,back;
    TextView drivername,drivernumber,drivercar,for_me;
    RatingBar mRatingbar;
    List<Address> address;
    private String TAG;


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
//        if (googleServicesAvailable()) {
//            Toast.makeText(Whereto.this, "Perfect", Toast.LENGTH_LONG).show();
        setContentView(R.layout.activity_whereto);
//            initMap();
//            locatemelayout = (LinearLayout) findViewById(R.id.locatme);
//            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        }

        //trying to change the status bar background color
        Window window = getWindow();
        // clear FLAG_TRANSLUCENT_STATUS flag:
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        // add FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag to the window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // finally change the color
        window.setStatusBarColor(ContextCompat.getColor(Whereto.this, R.color.grey));

        geocoder = new Geocoder(Whereto.this, Locale.getDefault());
        destinationlatlng = new LatLng(0.0,0.0);
        find_bus = (TextView)findViewById(R.id.done_button);

        mdriverinfo = (LinearLayout)findViewById(R.id.driverInfo);
        drivername = (TextView) findViewById(R.id.drivername);
        drivernumber = (TextView) findViewById(R.id.drivernumber);
        drivercar = (TextView) findViewById(R.id.drivercar);


        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(Whereto.this,new String[] {Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
        }else{
            mapFragment.getMapAsync(this);
        }


        find_bus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(requestbol){
                    Toast.makeText(Whereto.this,"no bus at the moment",Toast.LENGTH_LONG).show();
                }else{
                    requestbol = true;
                    String userid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("userlocation");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userid, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String s, DatabaseError databaseError) {

                        }
                    });

                    pickuplocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    Toast.makeText(Whereto.this,pickuplocation+"",Toast.LENGTH_LONG).show();
                    pickupmarker = mMap.addMarker(new MarkerOptions().position(pickuplocation).title("Pickup Here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pin_)));
                    find_bus.setText("Waiting for Bus...");
                    getClosestDriver();
                }
            }
        });
    }
    int radius = 1;
    Boolean driverFound = false;
    String driverFoundID;
    GeoQuery geoQuery;
    private void getClosestDriver() {
        DatabaseReference driverlocation = FirebaseDatabase.getInstance().getReference().child("bus");
        GeoFire geoFire = new GeoFire(driverlocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickuplocation.latitude,pickuplocation.longitude),radius);
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String s, GeoLocation geoLocation) {
                if(!driverFound && requestbol){
                    driverFound = true;
                    driverFoundID = s;
                }
                DatabaseReference driverref = FirebaseDatabase.getInstance().getReference().child("request");
                String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                HashMap map = new HashMap();
                map.put("customerRideId",customerId);
                map.put("destination",destination);
                map.put("destinationLat",destinationlatlng.latitude);
                map.put("destinationLng",destinationlatlng.longitude);
                driverref.updateChildren(map);

                getDriverLocation();
//                getDriverInfo();
                find_bus.setText("Looking for driver location...");
            }

            @Override
            public void onKeyExited(String s) {

            }

            @Override
            public void onKeyMoved(String s, GeoLocation geoLocation) {

            }

            @Override
            public void onGeoQueryReady() {
                if(!driverFound){
                    radius++;
                    getClosestDriver();
                }
            }
            @Override
            public void onGeoQueryError(DatabaseError databaseError) {

            }
        });
    }

    private void getDriverInfo() {
            mdriverinfo.setVisibility(View.VISIBLE);
            DatabaseReference mCustomerdatabase = FirebaseDatabase.getInstance().getReference().child("bus").child("Drivers").child(driverFoundID);
            mCustomerdatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
//                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                        java.util.Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                        if(map.get("name") != null){
                            drivername.setText(map.get("name").toString());
                        }
                        if(map.get("phone") != null){
                            drivernumber.setText(map.get("phone").toString());
                        }
                        if(map.get("car") != null){
                            drivercar.setText(map.get("car").toString());
                        }

                    }else{

                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
        }


    Marker mDriverMarker;
    DatabaseReference driverLocationref;
    ValueEventListener driverLocationrefListener;
    private void getDriverLocation() {
        driverLocationref = FirebaseDatabase.getInstance().getReference().child("bus").child(driverFoundID).child("l");
        driverLocationrefListener = driverLocationref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && requestbol){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationlat = 0;
                    double locationlong = 0;
                    find_bus.setText("Driver Found");
                    if(map.get(0) != null){
                        locationlat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        locationlong = Double.parseDouble(map.get(1).toString());
                    }
                        LatLng driverlatlng = new LatLng(locationlat,locationlong);
                    if(mDriverMarker != null){
                        mDriverMarker.remove();
                    }
                    Location loc1  = new Location("");
                    loc1.setLatitude(pickuplocation.latitude);
                    loc1.setLongitude(pickuplocation.longitude);


                    Location loc2  = new Location("");
                    loc2.setLatitude(driverlatlng.latitude);
                    loc2.setLongitude(driverlatlng.longitude);

                    float distance = loc1.distanceTo(loc2);
                    find_bus.setText("Bus Distance: " + String.valueOf(distance) + "m");
                    if(distance<100){
                        find_bus.setText("Bus Has Arrived");

                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(Whereto.this)
                                .setSmallIcon(R.drawable.pickbot_logo)
                                .setContentTitle("Bus Arrived")
                                .setContentText("School Bus Has Arrived.")
                                .setStyle(new NotificationCompat.BigTextStyle()
                                        .bigText("School Bus Has Arrived."))
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                          // notificationID allows you to update the notification later on.
                        mNotificationManager.notify(1, mBuilder.build());

                    }else{
                        find_bus.setText("Bus Distance: " + String.valueOf(distance));
                    }

                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverlatlng).title("school bus").icon(BitmapDescriptorFactory.fromResource(R.mipmap.driver_)));
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API
        ).build();
        mGoogleApiClient.connect();
    }

    public boolean googleServicesAvailable() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int isavailable = api.isGooglePlayServicesAvailable(this);
        if (isavailable == ConnectionResult.SUCCESS) {
            return true;
        } else if (api.isUserResolvableError(isavailable)) {
            Dialog dialog = api.getErrorDialog(this, isavailable, 0);
            dialog.show();
        } else {
            Toast.makeText(Whereto.this, "Cannot Connect To Google Play Services", Toast.LENGTH_LONG).show();
        }


        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
//        try {
//            address = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(),1);
//            userlocation.setText(address.get(0).getAddressLine(0));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(Whereto.this,new String[] {Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    final int LOCATION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case LOCATION_REQUEST_CODE:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                }
                else{
                    Toast.makeText(getApplicationContext(),"Turn Location On",Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(FirebaseAuth.getInstance().getCurrentUser() != null){

        }else{
            startActivity(new Intent(Whereto.this,Login.class));
        }
    }
}
