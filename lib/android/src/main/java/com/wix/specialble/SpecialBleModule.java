package com.wix.specialble;


import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.wix.specialble.bt.BLEManager;
import com.wix.specialble.bt.Device;
import com.wix.specialble.bt.Scan;
import com.wix.specialble.config.Config;
import com.wix.specialble.db.DBClient;
import com.wix.specialble.kays.PublicKey;

import java.util.ArrayList;
import java.util.List;

public class SpecialBleModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private final BLEManager bleManager;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public SpecialBleModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        bleManager = BLEManager.getInstance(reactContext);
    }

    @Override
    public String getName() {
        return "SpecialBle";
    }


    @ReactMethod
    public void advertise(String serviceUUID, String publicKey) {
        bleManager.advertise(serviceUUID, publicKey);
    }

    @ReactMethod
    public void stopAdvertise() {
        bleManager.stopAdvertise();
    }

    @ReactMethod
    public void startBLEScan(String serviceUUID) {
        bleManager.startScan(serviceUUID);
    }

    @ReactMethod
    public void stopBLEScan() {
        bleManager.stopScan();
    }


    @ReactMethod
    private void startBLEService(String serviceUUID, String publicKey) {
        Intent sIntent = new Intent(this.reactContext, BLEForegroundService.class);
        sIntent.putExtra("serviceUUID", serviceUUID);
        sIntent.putExtra("publicKey", publicKey);
        this.reactContext.startService(sIntent);
    }

    @ReactMethod
    public void stopBLEService() {
        this.reactContext.stopService(new Intent(this.reactContext, BLEForegroundService.class));
    }

    @ReactMethod
    public void cleanDevicesDB() {
        DBClient.getInstance(reactContext).clearAllDevices();
    }

    @ReactMethod
    public void getAllDevices(Callback callback) {
        List<Device> devices = bleManager.getAllDevices();
        WritableArray retArray = new WritableNativeArray();
        for(Device device : devices){
            retArray.pushMap(device.toWritableMap());
        }
        callback.invoke(retArray);
    }

    @ReactMethod
    public void cleanScansDB() {
        DBClient.getInstance(reactContext).clearAllScans();
    }

    @ReactMethod
    public void getAllScans(Callback callback) {
        List<Scan> scans = bleManager.getAllScans();
        WritableArray retArray = new WritableNativeArray();
        for(Scan scan : scans){
            retArray.pushMap(scan.toWritableMap());
        }
        callback.invoke(retArray);
    }

    @ReactMethod
    public void setPublicKeys(ReadableArray pubKeys) {
        ArrayList<PublicKey> pkList = new ArrayList<>();
        for(int i=0; i<pubKeys.size(); i++){
            String pkString = pubKeys.getString(i);
            PublicKey pk = new PublicKey(i,pkString);
            pkList.add(pk);
        }
        DBClient.getInstance(reactContext).insertAllKeys(pkList);
    }


    @ReactMethod
    public void getConfig(Callback callback) {
        Config config = Config.getInstance(reactContext);
        WritableMap configMap = new WritableNativeMap();
        configMap.putString("serviceUUID", config.getServiceUUID());
        configMap.putDouble("scanDuration", config.getScanDuration());
        configMap.putDouble("scanInterval", config.getScanInterval());
        configMap.putInt("scanMode", config.getScanMode()); //
        configMap.putInt("scanMatchMode", config.getScanMatchMode());
        configMap.putDouble("advertiseDuration", config.getAdvertiseDuration());
        configMap.putDouble("advertiseInterval", config.getAdvertiseInterval());
        configMap.putInt("advertiseMode", config.getAdvertiseMode());
        configMap.putInt("advertiseTXPowerLevel", config.getAdvertiseTXPowerLevel());
        callback.invoke(configMap);
    }


    @ReactMethod
    public void SetConfig(ReadableMap configMap) {
        Config config = Config.getInstance(reactContext);
        config.setServiceUUID(configMap.getString("serviceUUID"));
        config.setScanDuration((long) configMap.getDouble("scanDuration"));
        config.setScanInterval((long) configMap.getDouble("scanInterval"));
        config.setScanMode(configMap.getInt("scanMode"));
        config.setAdvertiseInterval((long) configMap.getDouble("advertiseInterval"));
        config.setAdvertiseMode(configMap.getInt("advertiseMode"));
        config.setAdvertiseTXPowerLevel(configMap.getInt("advertiseTXPowerLevel"));
    }

}
