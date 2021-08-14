package com.orthosium.inc.castoffpodmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class TestPodActivity extends AppCompatActivity {

    private static final long CALIBRATE_DELAY = 1000;

    short backVolt;
    short front1STVolt;
    short front5THVolt;
    short backWeight;
    short frontWeight;
    short backWeightCalibrated;
    short frontWeightCalibrated;
    short backWeightEstimated;
    short frontWeightEstimated;

    short backVoltMin     = Short.MAX_VALUE;
    short front1STVoltMin = Short.MAX_VALUE;
    short front5THVoltMin = Short.MAX_VALUE;

    short backVoltAve     = 0;
    short front1STVoltAve = 0;
    short front5THVoltAve = 0;

    short backVoltMax = Short.MIN_VALUE;
    short front1STVoltMax = Short.MIN_VALUE;
    short front5THVoltMax = Short.MIN_VALUE;

    byte[]    podData = new byte[20];
    short[][] recData = new short[0x800][3];
    int recTotal = 0;
    int recIndex = 0;

    private static final int aveSize = 8;
    short[][]  aveData = new short[aveSize][3];
    int aveIndex = 0;

    private TextView viewBackVolt;
    private TextView viewFront1STVolt;
    private TextView viewFront5THVolt;
    private TextView viewBackVoltMin;
    private TextView viewFront1STVoltMin;
    private TextView viewFront5THVoltMin;
    private TextView viewBackVoltMax;
    private TextView viewFront1STVoltMax;
    private TextView viewFront5THVoltMax;
    private TextView viewBackWeight;
    private TextView viewFrontWeight;
    private TextView viewBackPressure;
    private TextView viewFrontPressure;

    boolean saveDataRequest = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_pod);

        Bundle extraData = getIntent().getExtras();
        byte calibrate = extraData.getByte(MainActivity.INTENT_CLIBRATE_POD_ACTIVITY);

        viewBackVolt = findViewById(R.id.data_backVolt);
        viewFront1STVolt = findViewById(R.id.data_front1STVolt);
        viewFront5THVolt = findViewById(R.id.data_front5THVolt);

        viewBackVoltMin = findViewById(R.id.data_backVolt_min);
        viewFront1STVoltMin = findViewById(R.id.data_front1STVolt_min);
        viewFront5THVoltMin = findViewById(R.id.data_front5THVolt_min);

        viewBackVoltMax = findViewById(R.id.data_backVolt_max);
        viewFront1STVoltMax = findViewById(R.id.data_front1STVolt_max);
        viewFront5THVoltMax = findViewById(R.id.data_front5THVolt_max);

        viewBackWeight = findViewById(R.id.data_back_weight_calculated);
        viewFrontWeight = findViewById(R.id.data_Front_Weight_calculated);
        viewBackPressure = findViewById(R.id.data_backWeight);
        viewFrontPressure = findViewById(R.id.data_frontWeight);

        Arrays.fill(podData, (byte) 0);
        registerReceiver(mBleServiceReceiver, makeGattUpdateIntentFilter());
    }
    @Override
    public void onBackPressed() {
        exitActivity();
        super.onBackPressed();
    }
    @Override
    protected void onStop(){
        super.onStop();
        if(mBleServiceReceiver != null)
            unregisterReceiver(mBleServiceReceiver);
    }
/* START: Receiving POD data */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(PodBleService.CALIBRATE_DATA_NOTIFY);

        return intentFilter;
    }
    private BroadcastReceiver mBleServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (PodBleService.CALIBRATE_DATA_NOTIFY.equals(action)) {
                byte[] ar = intent.getByteArrayExtra(PodBleService.CALIBRATE_DATA_AVAILABLE);
                if (ar[0] == 0x43) {
                    ByteBuffer bb = ByteBuffer.wrap(ar);
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    bb.position(1);
                    // Retrieve data
                    backVolt = bb.getShort();
                    front1STVolt = bb.getShort();
                    front5THVolt = bb.getShort();
                    backWeight = bb.getShort();
                    frontWeight = bb.getShort();
                    backWeightCalibrated = bb.getShort();
                    frontWeightCalibrated = bb.getShort();
                    // update view
                    updateMessage(viewBackVolt, backVolt);
                    updateMessage(viewFront1STVolt, front1STVolt);
                    updateMessage(viewFront5THVolt, front5THVolt);
                    updateMessage(viewBackPressure, backWeight);
                    updateMessage(viewFrontPressure, frontWeight);
                    updateMessage(viewBackWeight, backWeightCalibrated);
                    updateMessage(viewFrontWeight, frontWeightCalibrated);

                    // update statistics
//                    if (backVolt < backVoltMin) {
//                        backVoltMin = backVolt;
//                        updateMessage(viewBackVoltMin, backVoltMin);
//                    }
//                    if (front1STVolt < front1STVoltMin) {
//                        front1STVoltMin = front1STVolt;
//                        updateMessage(viewFront1STVoltMin, front1STVoltMin);
//                    }
//                    if (front5THVolt < front5THVoltMin) {
//                        front5THVoltMin = front5THVolt;
//                        updateMessage(viewFront5THVoltMin, front5THVoltMin);
//                    }
                    if (backVolt > backVoltMax) {
                        backVoltMax = backVolt;
                        updateMessage(viewBackVoltMax, backVoltMax);
                    }
                    if (front1STVolt > front1STVoltMax) {
                        front1STVoltMax = front1STVolt;
                        updateMessage(viewFront1STVoltMax, front1STVoltMax);
                    }
                    if (front5THVolt > front5THVoltMax) {
                        front5THVoltMax = front5THVolt;
                        updateMessage(viewFront5THVoltMax, front5THVoltMax);
                    }
                    // Collect recorded Data
                    recData[recIndex][0] = backVolt;
                    recData[recIndex][1] = front1STVolt;
                    recData[recIndex][2] = front5THVolt;
                    recIndex = (recIndex + 1) % 0x800;
                    if (recTotal < 0x800)
                        recTotal++;
                    if(saveDataRequest)
                    {
                        saveData();
                        saveDataRequest = false;
                    }
                    aveData[aveIndex][0] = backVolt;
                    aveData[aveIndex][1] = front1STVolt;
                    aveData[aveIndex][2] = front5THVolt;
                    aveIndex = (aveIndex+1) % aveSize;
                    backVoltAve     = GetAverage(aveData, 0);
                    front1STVoltAve = GetAverage(aveData, 1);
                    front5THVoltAve = GetAverage(aveData, 2);
                    updateMessage(viewBackVoltMin, backVoltAve);
                    updateMessage(viewFront1STVoltMin, front1STVoltAve);
                    updateMessage(viewFront5THVoltMin, front5THVoltAve);
                }
            }
        }
    };

    private short GetAverage(short[][] Data, int index) {
        int Sum = 0;
        for(int i=0; i < aveSize; i++)
            Sum += Data[i][index];
        return (short)(Sum/aveSize);
    }

    private void updateMessage(final TextView view, final short data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewBackVolt.setText(String.valueOf(backVolt));
                view.setText(String.valueOf(data));
            }
        });
    }
    private void exitActivity() {
        unregisterReceiver(mBleServiceReceiver);
        mBleServiceReceiver = null;
        podData[0] = (byte) 9;
        Intent returnIntent = new Intent();
        returnIntent.putExtra(MainActivity.INTENT_RESPONSE_CALIBRATE_DATA, podData);
        setResult(MainActivity.RESPONSE_CALIBRATE_DATA, returnIntent);
        finish();
    }
    private void saveData() {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        try {

            String patientDataFile = "PodData_" + hour + "_" + minute + ".csv";
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, patientDataFile);
            FileOutputStream output = new FileOutputStream(file);

            String record = "backVolt, front1STVolt, front5THVolt";
            output.write(record.getBytes());

            int start = (recTotal == 0x800)? recIndex: 0;
            do {
                record = String.format("%d,%d,%d\n",recData[start][0],recData[start][1],recData[start][2]);
                output.write(record.getBytes());
                start = (start+1)%0x400;
                recTotal--;
            } while (recTotal > 0);

            output.flush();
            output.close();
            Toast.makeText(getApplicationContext(), "Data Saved to: " + patientDataFile, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.i("Serialize Exception", "TotalMonitorArray: ");
        }


    }
    private void resetStat() {
        backVoltMin = Short.MAX_VALUE;
        front1STVoltMin = Short.MAX_VALUE;
        front5THVoltMin = Short.MAX_VALUE;

        backVoltMax = Short.MIN_VALUE;
        front1STVoltMax = Short.MIN_VALUE;
        front5THVoltMax = Short.MIN_VALUE;
    }
    public void onClickResetButton( View view) {
        resetStat();
    }
    public void onClickOkButton(View view) {
        saveDataRequest = true;
    }
    public void onClickStopTesting(View view) {
        exitActivity();
    }
}