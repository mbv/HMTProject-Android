package mbv.hmtproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.widget.ListView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import mbv.hmtproject.datatypes.Scoreboard;
import mbv.hmtproject.datatypes.StopRoute;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnMarkerClickListener {

    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private boolean mPermissionDenied = false;

    private GoogleMap mMap;

    private int counterVisibleMarkers = 0;

    Scoreboard scoreboard;

    ScoreboardAdapter scoreboardAdapter;

    private int clientId = 0;

    public class InitStruct {
        public int ClientId;
    }

    public class RecordRaw {
        public int Int;
        public String Type;
        public String Number;
        public String EndStop;
        public String Nearest;
        public String Next;
    }

    public class ScoreboardRaw {
        public int StopId;
        public long Time;
        public RecordRaw[] Records;
    }

    Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        final SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        scoreboard = new Scoreboard();
        scoreboard.Routes = new ArrayList<>();




        ListView listView = (ListView) findViewById(R.id.scoreboard_table);
        scoreboardAdapter = new ScoreboardAdapter(this, scoreboard);
        listView.setAdapter(scoreboardAdapter);

       /* StopRoute stopRoute = new StopRoute();

        stopRoute.VehicleType = "T";
        stopRoute.Number = "2";
        stopRoute.Nearest = "<1";
        stopRoute.Next = "3";

        adapter.notifyDataSetChanged();*/

        try {
            socket = IO.socket("https://hmt.mbv-soft.ru");

            final Socket finalSocket = socket;
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    finalSocket.emit("initClient", clientId);
                    //finalSocket.disconnect();
                }

            }).on("init", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    String json = (String) args[0];
                    Gson gson = new Gson();

                    InitStruct initStruct = gson.fromJson(json, InitStruct.class);

                    clientId = initStruct.ClientId;
                }

            }).on("send", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    String json = (String) args[0];
                    Gson gson = new Gson();

                    ScoreboardRaw scoreboardRaw = gson.fromJson(json, ScoreboardRaw.class);

                    scoreboard.StopId = scoreboardRaw.StopId;
                    scoreboard.Time = scoreboardRaw.Time;

                    scoreboard.Routes.clear();

                    for (RecordRaw record: scoreboardRaw.Records) {
                        StopRoute tmp = new StopRoute();

                        tmp.VehicleType = record.Type;
                        tmp.Number = record.Number;
                        tmp.EndStop = record.EndStop;
                        tmp.Nearest = record.Nearest;
                        tmp.Next = record.Next;

                        scoreboard.Routes.add(tmp);
                    }

                    Date df = new java.util.Date(scoreboard.Time);
                    final String dateString = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(df);

                    final LocalMarker marker = markerMap.get(scoreboard.StopId);
                    if (marker.visible) {
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable(){
                            public void run() {
                                marker.marker.setSnippet(dateString);
                                marker.marker.hideInfoWindow();
                                marker.marker.showInfoWindow();

                                scoreboardAdapter.notifyDataSetChanged();
                            }
                        });
                    }

                   // scoreboardAdapter.notifyDataSetChanged();

                }

            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {}

            });
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

    }


    private class Request {
        String type;
        int id;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        LocalMarker localMarker = markerMap.get(markerMapToStop.get(marker));

        Request request = new Request();
        request.type = "stop";
        request.id = localMarker.id;

        Gson gson = new Gson();


        socket.emit("get", gson.toJson(request));
        return false;
    }

    @Override
    public void onCameraIdle() {
        if (mMap.getCameraPosition().zoom >= 14) {
            LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

            for (Map.Entry<Integer, LocalMarker> entry : markerMap.entrySet()) {

                LocalMarker localMarker = entry.getValue();

                if (!bounds.contains(localMarker.latLng) || mMap.getCameraPosition().zoom < 14) {
                    if (localMarker.visible) {
                        localMarker.visible = false;
                        localMarker.marker.remove();
                        counterVisibleMarkers--;
                    }
                } else {
                    if (!localMarker.visible) {
                        localMarker.marker = mMap.addMarker(new MarkerOptions()
                                .position(localMarker.latLng)
                                .title(localMarker.title)
                                .anchor((float) 0.5, (float) 0.5)
                                .flat(true)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.stop)));
                        markerMapToStop.put(localMarker.marker, localMarker.id);
                        localMarker.visible = true;
                        counterVisibleMarkers++;
                    }
                }
            }
        } else if (counterVisibleMarkers > 0) {
            for (Map.Entry<Integer, LocalMarker> entry : markerMap.entrySet()) {
                LocalMarker localMarker = entry.getValue();
                if (localMarker.visible) {
                    localMarker.visible = false;
                    localMarker.marker.remove();
                    counterVisibleMarkers--;
                }
            }
        }
    }

    private class StopElement {
        int Id;
        float Latitude;
        float Longitude;
        String Name;
    }

    private class LocalMarker {
        int id;
        LatLng latLng;
        String title;
        Marker marker;
        boolean visible;
    }

    private Map<Integer, LocalMarker> markerMap = new ArrayMap<>();
    private Map<Marker, Integer> markerMapToStop = new ArrayMap<>();


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnCameraIdleListener(this);
        mMap.setOnMarkerClickListener(this);

        LatLng Minsk = new LatLng(53.93146, 27.48005);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Minsk, 10));

        enableMyLocation();

/*        Marker melbourne = mMap.addMarker(new MarkerOptions()
                .position(Minsk)
                .title("Melbourne")
                .snippet("Population: 4,137,400")
                .anchor((float) 0.5, (float) 0.5)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.stop_x32)));*/

        InputStream in = getResources().openRawResource(R.raw.stops);
        Gson gson = new Gson();

        StopElement[] result = gson.fromJson(new InputStreamReader(in), StopElement[].class);


        //LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

        for (StopElement element : result) {
            LocalMarker localMarker = new LocalMarker();
            localMarker.latLng = new LatLng(element.Latitude, element.Longitude);
            localMarker.title = element.Name;
            localMarker.id = element.Id;


            localMarker.visible = false;


            markerMap.put(localMarker.id, localMarker);
        }


        // Add a marker in Sydney and move the camera
        //LatLng sydney = new LatLng(-34, 151);
        //mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Display the missing permission error dialog when the fragments resume.
            mPermissionDenied = true;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            mPermissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }
}
