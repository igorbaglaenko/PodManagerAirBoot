package com.orthosium.inc.castoffpodmanager;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class PodBleService extends Service {
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    public String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final Semaphore syncAccess = new Semaphore(1, true);

    // BROADCAST RECEIVER ACTIONS
    public final static String ACTION_GATT_CONNECTED =
            "orthosium.inc.castoffpodmanager.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "orthosium.inc.castoffpodmanager.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_DISCOVERED =
            "orthosium.inc.castoffpodmanager.ACTION_GATT_DISCOVERED";
    public final static String DATA_PODID_AVAILABLE =
            "orthosium.inc.castoffpodmanager.DATA_PODID_AVAILABLE";
    public final static String DATA_24HRS_AVAILABLE =
            "orthosium.inc.castoffpodmanager.DATA_24HRS_AVAILABLE";
    public final static String DATA_127HRS_AVAILABLE =
            "orthosium.inc.castoffpodmanager.DATA_127HRS_AVAILABLE";
    public final static String ACTION_WRITE_COMPLETE =
            "orthosium.inc.castoffpodmanager.ACTION_WRITE_COMPLETE";

    public final static String DATA_OPMODE =
            "orthosium.inc.castoffpodmanager.DATA_OPMODE";
    public final static String DATA_ARRAY_PODID =
            "orthosium.inc.castoffpodmanager.DATA_ARRAY_PODID";
    public final static String DATA_ARRAY_24HRS =
            "orthosium.inc.castoffpodmanager.DATA_ARRAY_24HRS";
    public final static String DATA_ARRAY_127HRS =
            "orthosium.inc.castoffpodmanager.DATA_ARRAY_127HRS";
    public final static String DATA_ALARM_NOTIFY =
            "orthosium.inc.castoffpodmanager.DATA_ALARM_NOTIFY";
    public final static String CALIBRATE_DATA_NOTIFY =
            "orthosium.inc.castoffpodmanager.CALIBRATE_DATA_NOTIFY";
    public final static String CALIBRATE_DATA_AVAILABLE =
            "orthosium.inc.castoffpodmanager.CALIBRATE_DATA_AVAILABLE";
    public final static String TEST_DATA_NOTIFY =
            "orthosium.inc.castoffpodmanager.TEST_DATA_NOTIFY";
    public final static String TEST_DATA_AVAILABLE =
            "orthosium.inc.castoffpodmanager.TEST_DATA_AVAILABLE";

    public static final String INSOLE_MONITORING_SERVICE     = "820FC9D1-0C34-4BAF-87FC-758571831943";
    public static final String DATA_POD_ID_CHARACTERISTIC    = "FDFBB797-6831-4E77-A383-67B5C30CF759";
    public static final String DATA_24HRS_CHARACTERISTIC     = "D823A49A-E194-46F0-85DD-6C043A1CB67B";
    public static final String DATA_127HRS_CHARACTERISTIC    = "846C755F-BB17-4BDF-B8D5-A778A2C203CB";
    public static final String OPERATION_MODE_CHARACTERISTIC = "7C61A878-DEA9-421A-AC8D-1BB3D418CBB2";
    public static final String NOTIFY_CHARACTERISTIC         = "E7EFD0A2-524B-463C-8F1C-01521B43C349";
    public static final String CLIENT_CHARACTERISTIC_CONFIG  = "00002902-0000-1000-8000-00805F9B34FB";

    public BluetoothGattCharacteristic dataPODID;
    public BluetoothGattCharacteristic data24hrs;
    public BluetoothGattCharacteristic data127hrs;
    public BluetoothGattCharacteristic dataOpMode;
    public BluetoothGattCharacteristic dataNotify;

    // Implements callback methods for GATT events
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                sendBroadcast(new Intent(ACTION_GATT_CONNECTED));
                // Attempts to discover services after successful connection.
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                sendBroadcast(new Intent(ACTION_GATT_DISCONNECTED));
                syncAccess.release();
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> gattServices = gatt.getServices();
                if(gattServices != null) {
                    for (BluetoothGattService gattService : gattServices) {
                        if (gattService.getUuid().toString().equalsIgnoreCase(INSOLE_MONITORING_SERVICE)) {
                            dataNotify = gattService.getCharacteristic(UUID.fromString(NOTIFY_CHARACTERISTIC));
                            mBluetoothGatt.setCharacteristicNotification(dataNotify, true);
                            BluetoothGattDescriptor descriptor = dataNotify.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                mBluetoothGatt.writeDescriptor(descriptor);
                            }
                            dataPODID = gattService.getCharacteristic(UUID.fromString(DATA_POD_ID_CHARACTERISTIC));
                            data24hrs = gattService.getCharacteristic(UUID.fromString(DATA_24HRS_CHARACTERISTIC));
                            data127hrs = gattService.getCharacteristic(UUID.fromString(DATA_127HRS_CHARACTERISTIC));
                            dataOpMode = gattService.getCharacteristic(UUID.fromString(OPERATION_MODE_CHARACTERISTIC));
                            dataOpMode.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                            sendBroadcast(new Intent(ACTION_GATT_DISCOVERED));
                        }
                    }
                }
            }
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,int status) {
            syncAccess.release();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                    if(characteristic.getUuid().toString().equalsIgnoreCase(DATA_POD_ID_CHARACTERISTIC)) {
                        final byte[] data = characteristic.getValue();
                        if (data != null && data.length > 0) {
                            final Intent intent = new Intent(DATA_PODID_AVAILABLE);
                            intent.putExtra(DATA_ARRAY_PODID, data);
                            sendBroadcast(intent);
                        }
                    }
                    else if (characteristic.getUuid().toString().equalsIgnoreCase(DATA_127HRS_CHARACTERISTIC)) {
                    final byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0) {
                        final Intent intent = new Intent(DATA_127HRS_AVAILABLE);
                        intent.putExtra(DATA_ARRAY_127HRS, data);
                        sendBroadcast(intent);
                    }
                }
            }
        }
        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            syncAccess.release();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().toString().equalsIgnoreCase(OPERATION_MODE_CHARACTERISTIC)) {
                    final byte[] data = characteristic.getValue();
                    final Intent intent = new Intent(ACTION_WRITE_COMPLETE);
                    intent.putExtra(DATA_OPMODE, data[0]);
                    sendBroadcast(intent);
                }
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic) {
            if(characteristic.getUuid().toString().equalsIgnoreCase(NOTIFY_CHARACTERISTIC)) {
                final byte[] ar = characteristic.getValue();
                if(ar != null && ar.length > 0) {
                    if (ar[0] == 0x43 ) {
                        final Intent intent = new Intent(CALIBRATE_DATA_NOTIFY);
                        intent.putExtra(CALIBRATE_DATA_AVAILABLE, ar);
                        sendBroadcast(intent);
                    }
                    if (ar[0] == 0x54 ) {
                        final Intent intent = new Intent(TEST_DATA_NOTIFY);
                        intent.putExtra(TEST_DATA_AVAILABLE, ar);
                        sendBroadcast(intent);
                    }
                }
            }
        }
    };
    public class LocalBinder extends Binder {
        PodBleService getService() {
            return PodBleService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        // Make sure that BluetoothGatt.close() is called
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        return super.onUnbind(intent);
    }

    // Initializes BluetoothLE service.
    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }
        return true;
    }
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }
        // Previously connected device.  Try to reconnect.
        if (address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else
                return false;
        }
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }
        // autoConnect parameter to true: connect to device as soon as become available.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }
    //Disconnects an existing connection or cancel a pending connection. The disconnection result
    public void    disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        syncAccess.release();
        mBluetoothGatt.disconnect();
    }
    //Request read characteristic
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mConnectionState == STATE_CONNECTED && mBluetoothAdapter != null && mBluetoothGatt != null) {
            try {
                syncAccess.acquire();
                mBluetoothGatt.readCharacteristic(characteristic);
            } catch (InterruptedException e){

            }
        }
    }
    //Request write characteristic
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mConnectionState == STATE_CONNECTED && mBluetoothAdapter != null && mBluetoothGatt != null) {
            try {
                syncAccess.acquire();
                mBluetoothGatt.writeCharacteristic(characteristic);
            } catch (InterruptedException e){

            }
        }
    }



}
