package mbv.hmtproject

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.FragmentActivity
import android.support.v4.content.ContextCompat
import android.support.v4.util.ArrayMap
import android.widget.ListView

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson

import java.io.InputStream
import java.io.InputStreamReader
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import mbv.hmtproject.datatypes.Scoreboard
import mbv.hmtproject.datatypes.StopRoute

class MapsActivity : FragmentActivity(), OnMapReadyCallback, ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnMarkerClickListener {

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * [.onRequestPermissionsResult].
     */
    private var mPermissionDenied = false

    private var mMap: GoogleMap? = null

    private var counterVisibleMarkers = 0

    internal lateinit var scoreboard: Scoreboard

    internal lateinit var scoreboardAdapter: ScoreboardAdapter

    private var clientId = 0

    internal lateinit var socket: Socket

    internal lateinit var iconBus: Bitmap
    internal lateinit var iconBusR: Bitmap
    internal lateinit var iconTrolleybus: Bitmap
    internal lateinit var iconTrolleybusR: Bitmap
    internal lateinit var iconTram: Bitmap
    internal lateinit var iconTramR: Bitmap

    @Volatile private var markerMap = ArrayMap<Int, LocalMarker>()
    @Volatile private var markerMapToStop = ArrayMap<Marker, Int>()

    @Volatile private var vehicleMap = ArrayMap<Int, LocalVehicle>()
    @Volatile private var vehicleMapToStop = ArrayMap<Marker, Int>()

    @Volatile private var selectedMarker: Int? = -1

    inner class InitStruct {
        var ClientId: Int = 0
    }

    inner class RecordRaw {
        var Int: Int = 0
        var Type: String? = null
        var Number: String? = null
        var EndStop: String? = null
        var Nearest: String? = null
        var Next: String? = null
    }

    inner class ScoreboardRaw {
        var StopId: Int = 0
        var Time: Long = 0
        var Records: Array<RecordRaw>? = null
    }

    inner class VehiclesRaw {
        var Vehicles: Array<VehicleRaw>? = null
    }

    inner class VehicleRaw {
        var Id: Int = 0
        var IdEndStop: Int = 0
        var Latitude: Float = 0.toFloat()
        var Longitude: Float = 0.toFloat()
        var TripType: Int = 0
        var VehicleType: Int = 0
        var Title: String? = null
        var RouteId: Int = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        scoreboard = Scoreboard()
        scoreboard.Routes = ArrayList()


        val listView = findViewById(R.id.scoreboard_table) as ListView
        scoreboardAdapter = ScoreboardAdapter(this, scoreboard)
        listView.adapter = scoreboardAdapter

        val resources = applicationContext.resources

        iconBus = BitmapFactory.decodeResource(resources, R.drawable.bus)
        iconBusR = BitmapFactory.decodeResource(resources, R.drawable.bus_r)
        iconTrolleybus = BitmapFactory.decodeResource(resources, R.drawable.trolleybus)
        iconTrolleybusR = BitmapFactory.decodeResource(resources, R.drawable.trolleybus_r)
        iconTram = BitmapFactory.decodeResource(resources, R.drawable.tram)
        iconTramR = BitmapFactory.decodeResource(resources, R.drawable.tram_r)


        try {
            socket = IO.socket("https://hmt.mbv-soft.ru")

            val finalSocket = socket
            socket.on(Socket.EVENT_CONNECT) {
                finalSocket.emit("initClient", clientId)
                //finalSocket.disconnect();
            }.on("init") { args ->
                val json = args[0] as String
                val gson = Gson()

                val initStruct = gson.fromJson<InitStruct>(json, InitStruct::class.java!!)

                clientId = initStruct.ClientId
            }.on("send") { args ->
                val json = args[0] as String
                val gson = Gson()

                val scoreboardRaw = gson.fromJson<ScoreboardRaw>(json, ScoreboardRaw::class.java!!)

                scoreboard.StopId = scoreboardRaw.StopId
                scoreboard.Time = scoreboardRaw.Time

                scoreboard.Routes.clear()

                for (record in scoreboardRaw.Records!!) {
                    val tmp = StopRoute()

                    tmp.VehicleType = record.Type
                    tmp.Number = record.Number
                    tmp.EndStop = record.EndStop
                    tmp.Nearest = record.Nearest
                    tmp.Next = record.Next

                    scoreboard.Routes.add(tmp)
                }

                val df = java.util.Date(scoreboard.Time)
                val dateString = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(df)

                val marker = markerMap[scoreboard.StopId]!!
                if (marker.visible) {
                    Helper.runInUiLoop {
                        marker.marker!!.setSnippet(dateString)
                        marker.marker!!.hideInfoWindow()
                        marker.marker!!.showInfoWindow()

                        scoreboardAdapter.notifyDataSetChanged()
                    }
                }
            }.on("sendV") { args ->
                val json = args[0] as String
                val gson = Gson()

                val vehiclesRaw = gson.fromJson<VehiclesRaw>(json, VehiclesRaw::class.java!!)

                for (vehicleRaw in vehiclesRaw.Vehicles!!) {
                    if (vehicleMap.containsKey(vehicleRaw.Id)) {
                        val vehicle = vehicleMap[vehicleRaw.Id]!!
                        Helper.runInUiLoop { vehicle.marker!!.setPosition(LatLng(vehicleRaw.Latitude.toDouble(), vehicleRaw.Longitude.toDouble())) }
                    } else {

                        val vehicle = LocalVehicle()
                        vehicle.latLng = LatLng(vehicleRaw.Latitude.toDouble(), vehicleRaw.Longitude.toDouble())

                        vehicle.visible = true
                        vehicle.id = vehicleRaw.Id
                        vehicle.tripType = vehicleRaw.TripType
                        vehicle.vehicleType = vehicleRaw.VehicleType
                        vehicle.title = vehicleRaw.Title
                        vehicle.routeId = vehicleRaw.RouteId

                        val bitmap: Bitmap

                        when (vehicle.vehicleType) {
                            0 -> if (vehicle.tripType == 10) {
                                bitmap = iconBus
                            } else {
                                bitmap = iconBusR
                            }
                            1 -> if (vehicle.tripType == 10) {
                                bitmap = iconTrolleybus
                            } else {
                                bitmap = iconTrolleybusR
                            }
                            2 -> if (vehicle.tripType == 10) {
                                bitmap = iconTram
                            } else {
                                bitmap = iconTramR
                            }
                            else -> bitmap = iconBus
                        }

                        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(Helper.GetBitmapMarker(applicationContext, bitmap, vehicle.title!!))

                        Helper.runInUiLoop {
                            vehicle.marker = mMap!!.addMarker(MarkerOptions()
                                    .position(vehicle.latLng!!)
                                    .icon(bitmapDescriptor))

                            vehicleMap.put(vehicle.id, vehicle)
                            vehicleMapToStop.put(vehicle.marker, vehicle.id)
                        }
                    }
                }
            }.on(Socket.EVENT_DISCONNECT) { }
            socket.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }

    }


    private inner class Request {
        internal var type: String? = null
        internal var id: Int = 0
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if (markerMapToStop.containsKey(marker)) {
            val markerId = markerMapToStop[marker]
            if (selectedMarker != markerId) {
                var localMarker: LocalMarker

                if (selectedMarker != -1) {
                    localMarker = markerMap[selectedMarker]!!
                    if (localMarker.visible) {
                        var bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.stop)
                        if (localMarker.bearing == -1) {
                            bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.stop_start)
                        }
                        localMarker.marker!!.setIcon(bitmapDescriptor)
                    }
                }

                selectedMarker = markerId

                clearVehicles()
                Helper.runInUiLoop { scoreboardAdapter.notifyDataSetChanged() }

                localMarker = markerMap[markerId]!!

                val bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.stop_selected)

                localMarker.marker!!.setIcon(bitmapDescriptor)
                localMarker.marker!!.rotation = 0f

                val request = Request()
                request.type = "stop"
                request.id = localMarker.id

                val gson = Gson()


                socket.emit("get", gson.toJson(request))
            }
        }
        return false
    }

    fun clearVehicles() {
        for ((_, localVehicle) in vehicleMap) {
            localVehicle.marker!!.remove()
        }
        vehicleMapToStop.clear()
        vehicleMap.clear()
    }


    override fun onCameraIdle() {
        if (mMap!!.cameraPosition.zoom >= 14) {
            val bounds = mMap!!.projection.visibleRegion.latLngBounds

            for ((_, localMarker) in markerMap) {

                if (!bounds.contains(localMarker.latLng!!) || mMap!!.cameraPosition.zoom < 14) {
                    if (localMarker.visible) {
                        localMarker.visible = false
                        localMarker.marker!!.remove()
                        counterVisibleMarkers--
                    }
                } else {
                    if (!localMarker.visible) {
                        var bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.stop)
                        if (localMarker.bearing == -1) {
                            bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.stop_start)

                        }
                        localMarker.marker = mMap!!.addMarker(MarkerOptions()
                                .position(localMarker.latLng!!)
                                .title(localMarker.title)
                                .anchor(0.5.toFloat(), 0.5.toFloat())
                                .flat(true)
                                .icon(bitmapDescriptor))
                        if (localMarker.bearing != -1) {
                            localMarker.marker!!.rotation = localMarker.bearing.toFloat()
                        }
                        markerMapToStop.put(localMarker.marker, localMarker.id)
                        localMarker.visible = true
                        counterVisibleMarkers++
                    }
                }
            }
        } else if (counterVisibleMarkers > 0) {
            for ((_, localMarker) in markerMap) {
                if (localMarker.visible) {
                    localMarker.visible = false
                    localMarker.marker!!.remove()
                    counterVisibleMarkers--
                }
            }
        }
    }

    private inner class StopElement {
        internal var Id: Int = 0
        internal var Latitude: Float = 0.toFloat()
        internal var Longitude: Float = 0.toFloat()
        internal var Name: String? = null
        internal var Bearing: Int = 0
    }

    private inner class LocalMarker {
        internal var id: Int = 0
        internal var latLng: LatLng? = null
        internal var title: String? = null
        internal var marker: Marker? = null
        internal var visible: Boolean = false
        internal var bearing: Int = 0
    }

    private inner class LocalVehicle {
        internal var id: Int = 0
        internal var latLng: LatLng? = null
        internal var marker: Marker? = null
        internal var visible: Boolean = false
        var tripType: Int = 0
        var vehicleType: Int = 0
        var title: String? = null
        var routeId: Int = 0
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap!!.setOnCameraIdleListener(this)
        mMap!!.setOnMarkerClickListener(this)

        val Minsk = LatLng(53.93146, 27.48005)

        mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(Minsk, 10f))

        enableMyLocation()

        /*        Marker melbourne = mMap.addMarker(new MarkerOptions()
                .position(Minsk)
                .title("Melbourne")
                .snippet("Population: 4,137,400")
                .anchor((float) 0.5, (float) 0.5)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.stop_x32)));*/

        val `in` = resources.openRawResource(R.raw.stops)
        val gson = Gson()

        val result = gson.fromJson<Array<StopElement>>(InputStreamReader(`in`), Array<StopElement>::class.java!!)


        //LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

        for (element in result) {
            val localMarker = LocalMarker()
            localMarker.latLng = LatLng(element.Latitude.toDouble(), element.Longitude.toDouble())
            localMarker.title = element.Name
            localMarker.id = element.Id
            localMarker.bearing = element.Bearing


            localMarker.visible = false


            markerMap.put(localMarker.id, localMarker)
        }


        // Add a marker in Sydney and move the camera
        //LatLng sydney = new LatLng(-34, 151);
        //mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true)
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap!!.isMyLocationEnabled = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation()
        } else {
            // Display the missing permission error dialog when the fragments resume.
            mPermissionDenied = true
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (mPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError()
            mPermissionDenied = false
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private fun showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(supportFragmentManager, "dialog")
    }

    companion object {

        /**
         * Request code for location permission request.
         *
         * @see .onRequestPermissionsResult
         */
        private val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
