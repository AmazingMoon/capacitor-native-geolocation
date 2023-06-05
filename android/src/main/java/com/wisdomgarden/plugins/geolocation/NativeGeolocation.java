package com.wisdomgarden.plugins.geolocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PluginRequestCodes;

import java.util.HashMap;
import java.util.Map;

@NativePlugin(
        permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
        permissionRequestCode = PluginRequestCodes.GEOLOCATION_REQUEST_PERMISSIONS
)
public class NativeGeolocation extends Plugin {

    private Context context;
    private Map<String, PluginCall> watchingCalls = new HashMap<>();

    private LocationManager locationManager;

    private LocationListener listener;

    private String provider;


    /**
     * Load Method
     * Load the plugin
     */
    public void load() {
        // Get singleton instance of database
        this.context = getContext();
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    //
    private void getNativeBestProvider(boolean enableHighAccuracy) {
        // 创建 Criteria 对象
        Criteria criteria = new Criteria();
        // 判断是否需要使用高精度模式
        if (enableHighAccuracy) {
            // 如果需要使用高精度模式，设置精度要求为 ACCURACY_FINE
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
        } else {
            // 如果不需要使用高精度模式，设置精度要求为 ACCURACY_COARSE
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        }

        // 获取最佳的位置提供器
        if (this.checkControlCenterToggle()) {
            // 定位服务已开启
            provider = locationManager.getBestProvider(criteria, true);
        }
    }

    public Boolean isLocationServicesEnabled() {
        return locationManager.isProviderEnabled(provider);
    }

    public Boolean checkControlCenterToggle() {
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gpsEnabled || networkEnabled;
    }

    @SuppressWarnings("MissingPermission")
    public Location getLastLocation(boolean enableHighAccuracy) {
        Location lastLoc = null;
        getNativeBestProvider(enableHighAccuracy);
        Location tmpLoc = locationManager.getLastKnownLocation(provider);
        if (tmpLoc != null) {
            lastLoc = tmpLoc;
        } else {
            if (locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) != null) {
                provider = LocationManager.GPS_PROVIDER;
                lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } else if (locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) != null) {
                provider = LocationManager.NETWORK_PROVIDER;
                lastLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            } else {
                for (String currentProvider : locationManager.getAllProviders()) {
                    if (!currentProvider.equals(provider)) {
                        tmpLoc = locationManager.getLastKnownLocation(currentProvider);
                        if (tmpLoc != null) {
                            provider = currentProvider;
                            lastLoc = tmpLoc;
                            break;
                        }
                    }
                }

            }
        }
        return lastLoc;
    }

    @PluginMethod
    public void getCurrentPosition(PluginCall call) {
        if (!hasRequiredPermissions()) {
            saveCall(call);
            pluginRequestAllPermissions();
        } else {
            sendLocation(call);
        }
    }

    private void sendLocation(PluginCall call) {
        requestLocationUpdates(call);
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void watchPosition(PluginCall call) {
        call.save();
        if (!hasRequiredPermissions()) {
            saveCall(call);
            pluginRequestAllPermissions();
        } else {
            startWatch(call);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startWatch(PluginCall call) {
        requestLocationUpdates(call);
        watchingCalls.put(call.getCallbackId(), call);
    }

    @SuppressWarnings("MissingPermission")
    @PluginMethod
    public void clearWatch(PluginCall call) {
        String callbackId = call.getString("id");
        if (callbackId != null) {
            PluginCall removed = watchingCalls.remove(callbackId);
            if (removed != null) {
                removed.release(bridge);
            }
        }
        if (watchingCalls.size() == 0) {
            clearLocationUpdates();
        }
        call.success();
    }

    /**
     * Process a new location item and send it to any listening calls
     *
     * @param location
     */
    private void processLocation(Location location) {
        for (Map.Entry<String, PluginCall> watch : watchingCalls.entrySet()) {
            watch.getValue().success(getJSObjectForLocation(location));
        }
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);

        PluginCall savedCall = getSavedCall();
        if (savedCall == null) {
            return;
        }

        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                savedCall.error("User denied location permission", "1", new Exception("User denied location permission"));
                return;
            }
        }

        if (savedCall.getMethodName().equals("getCurrentPosition")) {
            sendLocation(savedCall);
        } else if (savedCall.getMethodName().equals("watchPosition")) {
            startWatch(savedCall);
        } else {
            savedCall.resolve();
            savedCall.release(bridge);
        }
    }

    private JSObject getJSObjectForLocation(Location location) {
        JSObject ret = new JSObject();
        JSObject coords = new JSObject();
        ret.put("coords", coords);
        ret.put("timestamp", location.getTime());
        coords.put("latitude", location.getLatitude());
        coords.put("longitude", location.getLongitude());
        coords.put("accuracy", location.getAccuracy());
        coords.put("altitude", location.getAltitude());
        if (Build.VERSION.SDK_INT >= 26) {
            coords.put("altitudeAccuracy", location.getVerticalAccuracyMeters());
        }
        coords.put("speed", location.getSpeed());
        coords.put("heading", location.getBearing());
        return ret;
    }

    @SuppressWarnings("MissingPermission")
    private void requestLocationUpdates(final PluginCall call) {
        clearLocationUpdates();
        final boolean enableHighAccuracy = call.getBoolean("enableHighAccuracy", false);
        getNativeBestProvider(enableHighAccuracy);
        if (this.checkControlCenterToggle()) {
            if (this.isLocationServicesEnabled()) {
                if (call.getMethodName().equals("getCurrentPosition")) {
                    Location lastLocation = getLastLocation(enableHighAccuracy);
                    if (lastLocation == null) {
                        call.error("location services not restored", "4", new Exception("location services not restored"));
                        clearLocationUpdates();
                    } else {
                        call.success(getJSObjectForLocation(lastLocation));
                    }
                }

                listener =
                        new LocationListener() {
                            @Override
                            public void onLocationChanged(Location location) {
                                if (location == null) {
                                    call.error("location unavailable", "2", new Exception("location unavailable"));
                                } else {
                                    call.success(getJSObjectForLocation(location));
                                }
                            }

                            @Override
                            public void onStatusChanged(String provider, int status, Bundle extras) {
                            }

                            @Override
                            public void onProviderEnabled(String provider) {
                                locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
                            }

                            @Override
                            public void onProviderDisabled(String provider) {
                                clearLocationUpdates();

                            }
                        };
                locationManager.requestLocationUpdates(provider, 0, 0, listener);
            } else {
                call.error("location unavailable", "2", new Exception("location unavailable"));
            }

        } else {
            call.error("open location service", "1", new Exception("open location service"));
        }
    }

    private void clearLocationUpdates() {
        if (listener != null && locationManager != null) {
            locationManager.removeUpdates(listener);
            listener = null;
        }
    }
}