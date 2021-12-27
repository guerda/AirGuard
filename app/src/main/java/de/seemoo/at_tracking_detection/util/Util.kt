package de.seemoo.at_tracking_detection.util

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber

object Util {

    const val MAX_ZOOM_LEVEL = 19.5

    fun checkAndRequestPermission(permission: String): Boolean {
        val context = ATTrackingDetectionApplication.getCurrentActivity()
        when {
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                return true
            }
            shouldShowRequestPermissionRationale(context, permission) -> {
                val bundle = Bundle().apply { putString("permission", permission) }
                val intent = Intent(context, OnboardingActivity::class.java).apply {
                    putExtras(bundle)
                }
                context.startActivity(intent)
                return false
            }
            else -> {
                requestPermissions(
                    context,
                    arrayOf(permission),
                    0
                )
                return true
            }
        }
    }

    fun setGeoPointsFromList(
        beaconList: List<Beacon>,
        view: View,
        connectWithPolyline: Boolean = false,
        onMarkerWindowClick: ((beacon: Beacon) -> Unit)? = null
    ): Boolean {

        val map: MapView = view.findViewById(R.id.map)
        val context = ATTrackingDetectionApplication.getCurrentActivity()
        val copyrightOverlay = CopyrightOverlay(context)
        map.getOverlays().add(copyrightOverlay)
        val locationOverlay = MyLocationNewOverlay(map)
        val options = BitmapFactory.Options()

        val bitmapPerson =
            BitmapFactory.decodeResource(view.resources, R.drawable.mylocation, options)
        locationOverlay.setPersonIcon(bitmapPerson)
        locationOverlay.setPersonHotspot((26.0 * 1.6).toFloat(), (26.0 * 1.6).toFloat())
        val mapController = map.controller
        val geoPointList = ArrayList<GeoPoint>()

        locationOverlay.enableMyLocation()
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setUseDataConnection(true)
        map.setMultiTouchControls(true)
        map.overlays.add(locationOverlay)

        beaconList
            .filter { it.latitude != null && it.longitude != null }
            .map { beacon ->
                val marker = Marker(map)
                val geoPoint = GeoPoint(beacon.longitude!!, beacon.latitude!!)
                marker.infoWindow = DeviceMarkerInfo(
                    R.layout.include_device_marker_window, map, beacon, onMarkerWindowClick
                )
                marker.position = geoPoint
                marker.icon = ResourcesCompat.getDrawable(
                    view.resources,
                    R.drawable.ic_baseline_location_on_45_black,
                    null
                )
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                geoPointList.add(geoPoint)
                map.overlays.add(marker)

                marker.setOnMarkerClickListener { clickedMarker, _ ->
                    if (clickedMarker.isInfoWindowShown) {
                        clickedMarker.closeInfoWindow()
                    } else {
                        clickedMarker.showInfoWindow()
                    }
                    true
                }
            }

        Timber.d("Added ${geoPointList.size} markers to the map!")

        if (connectWithPolyline) {
            val line = Polyline(map)
            line.setPoints(geoPointList)
            line.infoWindow = null
            map.overlays.add(line)
        }
        if (geoPointList.isEmpty()) {
            locationOverlay.enableFollowLocation()
            mapController.setZoom(MAX_ZOOM_LEVEL)
            return false
        }
        val boundingBox = BoundingBox.fromGeoPointsSafe(geoPointList)

        map.post {
            try {
                Timber.d("Zoom in to bounds -> $boundingBox")
                map.zoomToBoundingBox(boundingBox, true, 100, MAX_ZOOM_LEVEL, 1)

            } catch (e: IllegalArgumentException) {
                mapController.setCenter(boundingBox.centerWithDateLine)
                mapController.setZoom(10.0)
                Timber.e("Failed to zoom to bounding box! ${e.message}")
            }
        }

//            map.zoomToBoundingBox(boundingBox, true)

        return true
    }

    fun buildScanSettings(scanMode: Int): ScanSettings =
        ScanSettings.Builder().setScanMode(scanMode).build()

    val bleScanFilter: MutableList<ScanFilter> = mutableListOf(
        ScanFilter.Builder()
            .setManufacturerData(0x4C, byteArrayOf((0x12).toByte(), (0x19).toByte()))
            .build()
    )

    val gattIntentFilter: IntentFilter = IntentFilter().apply {
        addAction(BluetoothConstants.ACTION_GATT_CONNECTED)
        addAction(BluetoothConstants.ACTION_GATT_DISCONNECTED)
        addAction(BluetoothConstants.ACTION_EVENT_COMPLETED)
        addAction(BluetoothConstants.ACTION_EVENT_FAILED)
    }
}
