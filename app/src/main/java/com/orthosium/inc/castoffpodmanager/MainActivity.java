package com.orthosium.inc.castoffpodmanager;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSIONS_REQUESTS = 3;
    private static final long SCAN_PERIOD = 10000;
    private static final long READ_WRITE_DELAY = 2000;

    public static final int REQUEST_REVISE_DATA = 20;
    public static final int RESPONSE_REVISE_THRESHOLDS = 30;
    public static final int RESPONSE_REVISE_ENTRY = 32;
    public static final int RESPONSE_REVISE_DATA_SENT = 35;

    public static final int REQUEST_UPDATE_DATA = 33;
    public static final int RESPONSE_UPDATE_DATA = 34;

    public static final int REQUEST_ENTRY_DATA = 21;
    public static final int RESPONSE_ENTRY_DATA = 31;

    public static final int REQUEST_CALIBRATE_DATA = 40;
    public static final int RESPONSE_CALIBRATE_DATA = 41;

    public static final int REQUEST_SEND_EMAIL = 50;
    public static final int RESPONSE_SEND_EMAIL_OK = 51;

    public final static String WEIGHT_UNITS =
            "orthosium.inc.castoffpodmanager.WEIGHT_UNITS";
    public static boolean weightUnitsIn_kg;

    public final static String INTENT_RESPONSE_REVISE_DATA =
            "orthosium.inc.castoffpodmanager.INTENT_RESPONSE_REVISE_DATA";
    public final static String INTENT_RESPONSE_ENTRY_DATA =
            "orthosium.inc.castoffpodmanager.INTENT_RESPONSE_ENTRY_DATA";
    public final static String INTENT_REVISE_DATA_ACTIVITY =
            "orthosium.inc.castoffpodmanager.INTENT_REVISE_DATA_ACTIVITY";
    public final static String INTENT_RESPONSE_CALIBRATE_DATA =
            "orthosium.inc.castoffpodmanager.INTENT_RESPONSE_CALIBRATE_DATA";
    public final static String INTENT_CLIBRATE_POD_ACTIVITY =
            "orthosium.inc.castoffpodmanager.INTENT_CLIBRATE_POD_ACTIVITY";
    public final static String INTENT_UPDATE_ENTRY_ACTIVITY =
            "orthosium.inc.castoffpodmanager.INTENT_UPDATE_ENTRY_ACTIVITY";
    public final static String INTENT_UPDATE_ENTRY_DATA =
            "orthosium.inc.castoffpodmanager.INTENT_UPDATE_ENTRY_DATA";
    public static String deviceAddress = "";
    public BluetoothAdapter mBluetoothAdapter;
    private PodBleService mSoleSensorBleService;
    private String deviceName = "podSensor";
    private int txPower;
    private BluetoothLeScanner mBluetoothScanner;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;
    private TextView connectionStatus;
    private TextView verString;
    private Button connectToPod;
    private enum NextAction {READ_VERSION,
                             SYNC_DATA,
                             SYNC_DOWN,
                             READ_DATA,
                             WRITE_DATA,
                             ENTER_DATA,
                             REVISE_DATA,
                             SEND_EMAIL,
                             CALIBRATE_POD,
                             READ_POD_SENSORS}
    private NextAction nextAction;
    private byte[] podData;
//  thresholds array
//  [0]-backPercent;
//  [1]-frontPercent;
//  [2]-bodyWeight;
//  [3]-injuryType;
    private short[] thresholds;

    /* START: Initialization */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize controls
        connectionStatus = findViewById(R.id.text_ConnectionStatus);
        verString = findViewById(R.id.info_VersionNo);
        verString.setText("Ver. 4.0 Build on Aug 8, 2021");
        connectToPod = findViewById(R.id.button_ConnectToPod);
        nextAction = NextAction.SYNC_DATA;
        thresholds = new short[4];
        // First check for permissions for BLE and Write to File
        boolean permissionGranted = true;
        final List<String> permissionsList = new ArrayList<String>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissionGranted = false;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionGranted = false;
        }
        if (permissionsList.size() > 0) {
            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(this,
                    permissionsList.toArray(new String[permissionsList.size()]),
                    PERMISSIONS_REQUESTS);
        }
        if(permissionGranted) {
            initializeBLE();
        }

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        weightUnitsIn_kg = sharedPref.getBoolean(WEIGHT_UNITS, false);
    }
    @Override
    protected void onDestroy() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor .putBoolean(WEIGHT_UNITS, weightUnitsIn_kg);
        editor.apply();
        super.onDestroy();
        if(mSoleSensorBleService != null)
            unregisterReceiver(mGattUpdateReceiver);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        boolean permissionBle = false;
        boolean permissionWrite = false;
        if (requestCode == PERMISSIONS_REQUESTS) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i]))
                        permissionWrite = true;
                    if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i]))
                        permissionBle = true;
                }
            }
        }
        if (permissionBle && permissionWrite) {
            initializeBLE();
        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Terminated");
            alertDialog.setMessage("To enable communication with POD, please, Allow an access to Location and Storage");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(1);
                        }
                    });
            alertDialog.show();
        }
    }
    private void initializeBLE() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }
    // Option Menu GUI implementation
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_activity_menu,menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.pod_version_menu:
                startScan(NextAction.READ_VERSION);
                break;
            case R.id.test_pod_sensors_menu:
                startScan(NextAction.READ_POD_SENSORS);
                break;
        }
        return super.onOptionsItemSelected(item);
    }
/* START: Scanning PODs */
    public void onClickConnectToPodButton( View view) {
        startScan(NextAction.SYNC_DATA);
    }
    public void startScan(NextAction action) {
        connectToPod.setEnabled(false);
        connectionStatus.setText(R.string.status_ScanningPodDevices);
        nextAction = action;
        txPower = -200;
        deviceAddress = "";
        if (Build.VERSION.SDK_INT >= 21) {
            mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
            mBluetoothScanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(final int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (Objects.equals(result.getDevice().getName(), deviceName)) {
                        if (result.getRssi() > txPower) {
                            txPower = result.getRssi();
                            deviceAddress = result.getDevice().getAddress();
                        }
                    }
                }
            });
        }
        else {
            mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (Objects.equals(device.getName(), deviceName)) {
                                if (rssi > txPower) {
                                    txPower = rssi;
                                    deviceAddress = device.getAddress();
                                }
                            }
                        }
                    });
                }
            };
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= 21) {
                    mBluetoothScanner.stopScan(new ScanCallback() {
                        @Override
                        public void onScanFailed(int errorCode) {
                            super.onScanFailed(errorCode);
                        }
                    });
                }
                else {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
                if (deviceAddress.isEmpty()) {
                    connectionStatus.setText(R.string.status_PodNotFound);
                    connectToPod.setEnabled(true);
                }
                else {
                    connectionStatus.setText(String.format("%s  %s",deviceAddress, getString(R.string.status_PodFound)));
                    initConnection();
                    connectToPod.setEnabled(false);
                }
            }
        },SCAN_PERIOD );
    }
/* START: Connecting/Disconnecting to POD BLE Service */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mSoleSensorBleService = ((PodBleService.LocalBinder) service).getService();
            if (!mSoleSensorBleService.initialize()) {
                showPrompt(R.string.prompt_BleServiceUnavailable);
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mSoleSensorBleService.connect(deviceAddress);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSoleSensorBleService = null;
        }
    };
    private void initConnection() {
        if (mSoleSensorBleService == null) {
            Intent gattServiceIntent = new Intent(this, PodBleService.class);
            bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        } else {
            mSoleSensorBleService.connect(deviceAddress);
        }
    }
    private void closeConnection() {
        if (mSoleSensorBleService != null) {
            mSoleSensorBleService.disconnect();
        }
    }
/* START: Receiving POD data */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(PodBleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(PodBleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(PodBleService.ACTION_GATT_DISCOVERED);
        intentFilter.addAction(PodBleService.DATA_PODID_AVAILABLE);
        intentFilter.addAction(PodBleService.DATA_127HRS_AVAILABLE);
        intentFilter.addAction(PodBleService.ACTION_WRITE_COMPLETE);
        return intentFilter;
    }
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (PodBleService.ACTION_GATT_CONNECTED.equals(action)) {
            connectionStatus.setText(R.string.status_DiscoveringPodServices);
        }
        else if (PodBleService.ACTION_GATT_DISCONNECTED.equals(action)) {
            if(nextAction == NextAction.READ_VERSION)
                nextAction = NextAction.SYNC_DOWN;
            else
                connectionStatus.setText("");
            if     (nextAction == NextAction.REVISE_DATA) {
                Intent nextIntent = new Intent(getApplicationContext(), ReviseDataActivity.class);
                nextIntent.putExtra(INTENT_REVISE_DATA_ACTIVITY, podData);
                startActivityForResult(nextIntent,REQUEST_REVISE_DATA);
            }
            else if(nextAction == NextAction.ENTER_DATA ) {
                Intent nextIntent = new Intent(getApplicationContext(), EntryDataActivity.class);
                startActivityForResult(nextIntent,REQUEST_ENTRY_DATA);
            }
            else if(nextAction == NextAction.SEND_EMAIL ) {
                displayCompleteMessage();
            }
            else if(nextAction == NextAction.SYNC_DOWN ) {
                connectToPod.setEnabled(true);
            }
        }
        else if (PodBleService.ACTION_GATT_DISCOVERED.equals(action)) {
            connectionStatus.setText(R.string.status_ConnectedToPod);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if     (nextAction == NextAction.READ_VERSION      ) {
                        if(mSoleSensorBleService.dataPODID != null)
                            mSoleSensorBleService.readCharacteristic(mSoleSensorBleService.dataPODID);
                        else {
                            StringBuilder msg = new StringBuilder();
                            msg.append("\nPOD ID: ");
                            msg.append(deviceAddress);
                            connectionStatus.setText(msg);
                            closeConnection();
                        }
                    }
                    else if (nextAction == NextAction.SYNC_DATA        ) {
                        initOpData((byte)1, (byte)0);
                        mSoleSensorBleService.dataOpMode.setValue(podData);
                        mSoleSensorBleService.writeCharacteristic(mSoleSensorBleService.dataOpMode);
                        connectionStatus.setText(R.string.status_WritingToPod);
                    }
                    else if(nextAction == NextAction.WRITE_DATA       ) {
                        syncOpData();
                        mSoleSensorBleService.dataOpMode.setValue(podData);
                        mSoleSensorBleService.writeCharacteristic(mSoleSensorBleService.dataOpMode);
                        connectionStatus.setText(R.string.status_WritingToPod);
                    }
                    else if(nextAction == NextAction.CALIBRATE_POD    ) {
                        initOpData((byte)8, (byte)0);
                        mSoleSensorBleService.dataOpMode.setValue(podData);
                        mSoleSensorBleService.writeCharacteristic(mSoleSensorBleService.dataOpMode);
                        connectionStatus.setText(R.string.status_WritingToPod);
                    }
                    else if(nextAction == NextAction.READ_POD_SENSORS ) {
                        initOpData((byte)8, (byte)0);
                        mSoleSensorBleService.dataOpMode.setValue(podData);
                        mSoleSensorBleService.writeCharacteristic(mSoleSensorBleService.dataOpMode);
                        connectionStatus.setText(R.string.status_WritingToPod);
                    }
                }
            },READ_WRITE_DELAY );
        }
        else if (PodBleService.DATA_PODID_AVAILABLE.equals(action)) {
            podData = intent.getByteArrayExtra(PodBleService.DATA_ARRAY_PODID);
            if (podData != null && podData.length == 25) {
                StringBuilder msg = new StringBuilder();
                msg.append("POD Firmware Version: ");
                msg.append(new String(podData, 0, 6));
                msg.append("\nPOD ID: ");
                msg.append(new String(podData, 7,17));
                connectionStatus.setText(msg);
           }
            closeConnection();
        }
        else if (PodBleService.DATA_127HRS_AVAILABLE.equals(action)) {
            podData = intent.getByteArrayExtra(PodBleService.DATA_ARRAY_127HRS);
            if (podData != null && podData.length == (121 * 4 + 12)) {
                PatientData patientData =  new PatientData();
                patientData.setDataArrays(podData);
                if(patientData.getNoOfDays() != 0)
                {
                    thresholds[0] = (short)patientData.backPercent;
                    thresholds[1] = (short)patientData.frontPercent;
                    thresholds[2] = (short)patientData.bodyWeight;
                    thresholds[3] = (short)patientData.injuryType;
                    // Request Switch to Revise Data Activity
                    nextAction = NextAction.REVISE_DATA;
                    closeConnection();
                }
                else
                {
                    // Request Switch to Entry New Data Activity
                    nextAction = NextAction.ENTER_DATA;
                    closeConnection();
                }
            }
            else {
                nextAction = NextAction.SYNC_DOWN;
                closeConnection();
                connectToPod.setEnabled(true);
                showPrompt(R.string.prompt_incompatible_data);
            }

        }
        else if (PodBleService.ACTION_WRITE_COMPLETE.equals(action)) {
            byte mode = intent.getByteExtra(PodBleService.DATA_OPMODE, (byte) 16);
            if      ( mode == 0 ) {
                connectionStatus.setText(R.string.status_WritingToPodComplete);
                nextAction = NextAction.SEND_EMAIL;
                closeConnection();
                showPrompt(R.string.status_InfoSaved);
            }
            else if ( mode == 1 ) {
                nextAction = NextAction.READ_DATA;
                mSoleSensorBleService.readCharacteristic(mSoleSensorBleService.data127hrs);
                connectionStatus.setText(R.string.status_ReceivingData);
            }
            else if ( mode == 8 ) {
                connectionStatus.setText("");
                if (nextAction == NextAction.CALIBRATE_POD) {
                    Intent nextIntent = new Intent(getApplicationContext(), CalibratePodActivity.class);
                    nextIntent.putExtra(INTENT_CLIBRATE_POD_ACTIVITY, thresholds);
                    startActivityForResult(nextIntent, REQUEST_CALIBRATE_DATA);
                } else if (nextAction == NextAction.READ_POD_SENSORS) {
                    Intent nextIntent = new Intent(getApplicationContext(), TestPodActivity.class);
                    nextIntent.putExtra(INTENT_CLIBRATE_POD_ACTIVITY, (byte) 0);
                    startActivityForResult(nextIntent, REQUEST_CALIBRATE_DATA);
                }
            }
            else if (mode == 9) {
                nextAction = NextAction.SYNC_DOWN;
                closeConnection();
            }
            else if ( mode == 10) {
                nextAction = NextAction.SYNC_DOWN;
                closeConnection();
                showPrompt(R.string.status_InfoSaved);
            }
        }
    }
};
/* START: Popup Dialog Implementation */
    private View popupInputDialogView = null;
    private EditText emailAddress = null;
    private Button saveUserDataButton = null;
    private Button cancelUserDataButton = null;
    private void initPopupViewControls() {
        // Get layout inflater object.
        LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
        // Inflate the popup dialog from a layout xml file.
        popupInputDialogView = layoutInflater.inflate(R.layout.popup_sendmail_req, null);
        // Get user input edittext and button ui controls in the popup dialog.
        emailAddress =  popupInputDialogView.findViewById(R.id.data_emailAddress);
        saveUserDataButton = popupInputDialogView.findViewById(R.id.button_save_user_data);
        cancelUserDataButton = popupInputDialogView.findViewById(R.id.button_cancel_user_data);
    }
    public void displayCompleteMessage() {
        // Create a AlertDialog Builder.
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        // Set title, icon, can not cancel properties.
        alertDialogBuilder.setTitle("Enter patient's email address");
        alertDialogBuilder.setIcon(R.drawable.ic_launcher_background);
        alertDialogBuilder.setCancelable(false);
        // Init popup dialog view and it's ui controls.
        initPopupViewControls();
        // Set the inflated layout view object to the AlertDialog builder.
        alertDialogBuilder.setView(popupInputDialogView);
        // Create AlertDialog and show.
        final AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        // When user click the save user data button in the popup dialog.
        saveUserDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get user data from popup dialog editeext.
                String email = emailAddress.getText().toString();
                alertDialog.dismiss();
                onSendData(email);
            }
        });
        cancelUserDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                nextAction = NextAction.CALIBRATE_POD;
                alertDialog.dismiss();
                connectionStatus.setText(R.string.status_Connecting);
                initConnection();
                connectToPod.setEnabled(false);
            }
        });
    }
    private void onSendData(String emailAddr) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, PatientInfo.patientFileName);
        if (file.exists()) {
            Intent email = new Intent(Intent.ACTION_SEND);
            email.setType("text/plain");
            email.putExtra(Intent.EXTRA_EMAIL, new String[] {emailAddr});
            email.putExtra(Intent.EXTRA_SUBJECT, "CastOff PodTracker application");
            email.putExtra(Intent.EXTRA_CC, "www.orthosium.com/support");
            StringBuilder message = new StringBuilder();
            message.append ("Please, download the attached PodProfile.dat file\n")
                    .append("Click on a link below to install the PodTracker App\n")
                    .append("www.orthosium.com/support\n");
            email.putExtra(Intent.EXTRA_TEXT,message.toString());
            Uri uri = FileProvider.getUriForFile(this, getString(R.string.file_provider_authority), file);
            email.putExtra(Intent.EXTRA_STREAM, uri);
            email.setType("message/rfc822");
            startActivityForResult(email, REQUEST_SEND_EMAIL);
        }
        else {
            nextAction = NextAction.CALIBRATE_POD;
            connectionStatus.setText(R.string.status_Connecting);
            initConnection();
            connectToPod.setEnabled(false);
        }
    }
/* START: Return from other Activity */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if      (requestCode == REQUEST_REVISE_DATA && resultCode == RESPONSE_REVISE_THRESHOLDS) {
            nextAction = NextAction.ENTER_DATA;
            Intent nextIntent = new Intent(getApplicationContext(), UpdateEntryDataActivity.class);
            nextIntent.putExtra(INTENT_UPDATE_ENTRY_ACTIVITY,thresholds);
            startActivityForResult(nextIntent,REQUEST_UPDATE_DATA);
        }
        else if (requestCode == REQUEST_REVISE_DATA && resultCode == RESPONSE_REVISE_ENTRY) {
            nextAction = NextAction.ENTER_DATA;
            Intent nextIntent = new Intent(getApplicationContext(), EntryDataActivity.class);
            startActivityForResult(nextIntent,REQUEST_ENTRY_DATA);
        }
        else if (requestCode == REQUEST_REVISE_DATA && resultCode == RESPONSE_REVISE_DATA_SENT) {
            nextAction = NextAction.SYNC_DOWN;
            connectToPod.setEnabled(true);
        }
        else if (requestCode == REQUEST_UPDATE_DATA && resultCode == RESPONSE_UPDATE_DATA) {
            Bundle extraData = intent.getExtras();
            thresholds = extraData.getShortArray(MainActivity.INTENT_UPDATE_ENTRY_DATA);
            nextAction = NextAction.CALIBRATE_POD;
            connectionStatus.setText(R.string.status_Connecting);
            initConnection();
            connectToPod.setEnabled(false);
        }
        else if (requestCode == REQUEST_ENTRY_DATA && resultCode == RESPONSE_ENTRY_DATA) {
            Bundle extraData = intent.getExtras();
            podData = extraData.getByteArray(MainActivity.INTENT_RESPONSE_ENTRY_DATA);
            thresholds[0] = (short)podData[6];        // backPercent
            thresholds[1] = (short)podData[5];        // frontPercent
            thresholds[2] = (short)(podData[8] < 0 ?  // bodyWeight
                                    podData[8] + 256:
                                    podData[8]);
            thresholds[3] = (short)podData[7];        // injuryType
            nextAction = NextAction.WRITE_DATA;
            connectionStatus.setText(R.string.status_Connecting);
            initConnection();
            connectToPod.setEnabled(false);
        }
        else if (requestCode == REQUEST_CALIBRATE_DATA && resultCode == RESPONSE_CALIBRATE_DATA) {
            Bundle extraData = intent.getExtras();
            podData = extraData.getByteArray(MainActivity.INTENT_RESPONSE_CALIBRATE_DATA);
            mSoleSensorBleService.dataOpMode.setValue(podData);
            mSoleSensorBleService.writeCharacteristic(mSoleSensorBleService.dataOpMode);
        }
        else if (requestCode == REQUEST_SEND_EMAIL ) {
            nextAction = NextAction.CALIBRATE_POD;
            connectionStatus.setText(R.string.status_Connecting);
            initConnection();
            connectToPod.setEnabled(false);
        }
        else {
            nextAction = NextAction.SYNC_DOWN;
            connectToPod.setEnabled(true);
        }
    }
/* Utility functions */
    private void showPrompt(final int msgId) {
        Toast.makeText(getApplicationContext(), getString(msgId), Toast.LENGTH_SHORT).show();;
    }
    public void initOpData(byte mode, byte injuryType) {
        podData = new byte[20];
        ByteBuffer bb = ByteBuffer.wrap(podData);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(mode);
        bb.put((byte) 0);  //weightAdjustment
        bb.put((byte) 0);  //backThreshold
        bb.put((byte) 0);  //frontThreshold
        bb.put((byte) 0);  //totalThreshold
        bb.put((byte) 0);  //frontPercent
        bb.put((byte) 0);  //backPercent
        bb.put(injuryType);//injuryType
        bb.put((byte) 0);  //bodyWeight
        Calendar datetime = new GregorianCalendar();
        datetime.setTimeInMillis(System.currentTimeMillis());
        bb.put((byte) datetime.get(Calendar.MINUTE));
        bb.put((byte) datetime.get(Calendar.SECOND));
        bb.put((byte) datetime.get(Calendar.HOUR_OF_DAY));
        bb.putShort((short) datetime.get(Calendar.MILLISECOND));
        bb.putShort((short) datetime.get(Calendar.DAY_OF_YEAR));
        bb.putShort((short) datetime.get(Calendar.YEAR));
    }
    private void syncOpData() {
        ByteBuffer bb = ByteBuffer.wrap(podData);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.position(9);
        Calendar datetime = new GregorianCalendar();
        datetime.setTimeInMillis(System.currentTimeMillis());
        bb.put((byte) datetime.get(Calendar.MINUTE));
        bb.put((byte) datetime.get(Calendar.SECOND));
        bb.put((byte) datetime.get(Calendar.HOUR_OF_DAY));
        bb.putShort((short) datetime.get(Calendar.MILLISECOND));
        bb.putShort((short) datetime.get(Calendar.DAY_OF_YEAR));
        bb.putShort((short) datetime.get(Calendar.YEAR));
    }
}


