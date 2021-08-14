package com.orthosium.inc.castoffpodmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class CalibratePodActivity extends AppCompatActivity {

    short backWeight;
    short frontWeight;

    short thresholdFrontReading;
    short thresholdBackReading;
    short liftedFrontReading;
    short liftedBackReading;
    short sittingFrontReading;
    short sittingBackReading;
    short sittingFrontWeight;
    short sittingBackWeight;
    Integer sittingWeight;

    byte[] podData = new byte[20];

    short[] recBackWeight = new short[128];
    short[] recFrontWeight = new short[128];
    int recIndex = 0;
    int recTotal = 0;

    short[] patientData;
    static final int BackPercent  = 0;
    static final int FrontPercent = 1;
    static final int BodyWeight   = 2;
//    static final int InjuryType   = 3;
    private TextView viewTitle;
    private TextView viewPromptMessage;
    private TextView viewPromptAction;
    private EditText dataSittingWeight;
    private TextView weightUnits;
    private short bodyWeight;
    private Button buttonAction;

    private boolean stopRequest = false;
    private int measureArraySize = 6;
    private enum CalibrateState {IDLE, CALIBRATE_LIFTTED, CALIBRATE_SITTING, CALIBRATE_FRONT, CALIBRATE_BACK,STOP_TEST}
    CalibrateState currentState = CalibrateState.IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate_pod);

        Bundle extraData = getIntent().getExtras();
        patientData = extraData.getShortArray(MainActivity.INTENT_CLIBRATE_POD_ACTIVITY);

        viewTitle = findViewById(R.id.view_title);
        viewPromptMessage = findViewById(R.id.viewPromptMessage);
        buttonAction = findViewById(R.id.buttonAction);
        dataSittingWeight = findViewById(R.id.data_sitting_weight);
        weightUnits = findViewById((R.id.label_weightUnits));
        viewPromptAction = findViewById(R.id.viewPromptAction);
        buttonAction.setText("Save");
        //buttonAction.setEnabled(false);

        Arrays.fill(podData, (byte) 0);
        thresholdFrontReading = 0;
        thresholdBackReading = 0;
        //viewTitle.setText("Lifted Weight Calibration");
        viewTitle.setText("Sitting Weight Calibration");
        //viewPromptMessage.setText("Ensure all straps are tightened on the boot\n\nLift boot and hold it above the ground");
        viewPromptMessage.setText("Ensure all straps are tightened on the boot\n\nPlace the foot on the scale\nAsk patient to relax and do not apply additional weight");

        //viewPromptAction.setText ("Keep boot steady above the ground for 10 seconds\nPress SAVE when ready");
        viewPromptAction.setText ("Enter readings from scale\nPress SAVE when ready");
        //dataSittingWeight.setVisibility(View.INVISIBLE);
        //weightUnits.setVisibility(View.INVISIBLE);
        dataSittingWeight.setVisibility(View.VISIBLE);
        weightUnits.setVisibility(View.VISIBLE);
        if(MainActivity.weightUnitsIn_kg) {
            bodyWeight = (short)Math.round((double)(patientData[BodyWeight]) * 0.454);
            weightUnits.setText("kg");
        }
        else {
            bodyWeight = patientData[BodyWeight];
            weightUnits.setText("lb");
        }
        //currentState = CalibrateState.CALIBRATE_LIFTTED;
        currentState = CalibrateState.CALIBRATE_SITTING;

        registerReceiver(mBleServiceReceiver, makeGattUpdateIntentFilter());
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mBleServiceReceiver != null)
            unregisterReceiver(mBleServiceReceiver);
    }
    private void exitActivity() {
        unregisterReceiver(mBleServiceReceiver);
        mBleServiceReceiver = null;
        if(patientData[FrontPercent] < 100 && thresholdFrontReading >= 0 && thresholdFrontReading < sittingFrontReading)
            thresholdFrontReading = (short)(sittingFrontReading);
        if(patientData[BackPercent] < 100 && thresholdBackReading >= 0 && thresholdBackReading < sittingBackReading)
            thresholdBackReading = (short)(sittingBackReading);

        podData[0] = (byte) 10;
        podData[1] = (byte) sittingBackReading;
        podData[2] = (byte) thresholdBackReading;
        podData[3] = (byte) sittingFrontReading;
        podData[4] = (byte) thresholdFrontReading;
        podData[5] = (byte) patientData[FrontPercent];
        podData[6] = (byte) patientData[BackPercent];
        podData[7] = (byte) liftedFrontReading;
        podData[8] = (byte) liftedBackReading;
        Intent returnIntent = new Intent();
        returnIntent.putExtra(MainActivity.INTENT_RESPONSE_CALIBRATE_DATA, podData);
        setResult(MainActivity.RESPONSE_CALIBRATE_DATA, returnIntent);
        finish();
    }
    // START: Receiving POD data
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
                    if(!stopRequest) {
                        ByteBuffer bb = ByteBuffer.wrap(ar);
                        bb.order(ByteOrder.LITTLE_ENDIAN);
                        bb.position(7);
                        // Update parameters view
                        backWeight = bb.getShort();
                        frontWeight = bb.getShort();
                        // Collect calibration Data
                        recBackWeight[recIndex] = backWeight;
                        recFrontWeight[recIndex] = frontWeight;
                        recIndex = (recIndex + 1) % measureArraySize;
                        recTotal++;
                    }
                    else {
                        if(currentState == CalibrateState.CALIBRATE_LIFTTED) {
                            estimateLiftedWeight();
                            viewTitle.setText("Sitting Weight Calibration");
                            viewPromptMessage.setText("Place the boot on the scale\nAsk patient to relax and do not apply additional weight");
                            viewPromptAction.setText ("Enter readings from scale\nPress SAVE when ready");
                            currentState = CalibrateState.CALIBRATE_SITTING;
                            dataSittingWeight.setVisibility(View.VISIBLE);
                            weightUnits.setVisibility(View.VISIBLE);
                            stopRequest = false;
                        }
                        else if(currentState == CalibrateState.CALIBRATE_SITTING) {
                            String str = dataSittingWeight.getText().toString();
                            if (str.isEmpty()) {
                                stopRequest = false;
                                showToast("Please enter readings from the scale");
                            }
                            else {
                                sittingWeight = Integer.valueOf(str);
                            }
                            dataSittingWeight.setVisibility(View.INVISIBLE);
                            weightUnits.setVisibility(View.INVISIBLE);
                            dataSittingWeight.setVisibility(View.INVISIBLE);
                            weightUnits.setVisibility(View.INVISIBLE);
                            viewPromptAction.setText("Press SAVE when ready");

                            estimateSittingWeight();
                            if (patientData[FrontPercent] == 0) {
                                thresholdFrontReading = sittingFrontWeight;
                            }
                            if (patientData[BackPercent] == 0) {
                                thresholdBackReading = sittingBackWeight;
                            }
                            if (patientData[FrontPercent] > 0) {
                                viewTitle.setText("Front Weight Calibration");
                                if (patientData[FrontPercent] < 100) {
                                    if (MainActivity.weightUnitsIn_kg)
                                        updateMessage(String.format("Apply %skg to the BOOT FRONT", ((bodyWeight * patientData[FrontPercent]) / 100) + (sittingWeight * (100 - patientData[FrontPercent])) / 100));
                                    else
                                        updateMessage(String.format("Apply %slb to the BOOT FRONT", ((bodyWeight * patientData[FrontPercent]) / 100) + (sittingWeight * (100 - patientData[FrontPercent])) / 100));
                                }
                                else
                                    updateMessage("Apply full weight to the BOOT FRONT");
                                currentState = CalibrateState.CALIBRATE_FRONT;
                                stopRequest = false;
                            }
                            else if (patientData[BackPercent] > 0) {
                                viewTitle.setText("Back Weight Calibration");
                                if (patientData[BackPercent] < 100) {
                                    if (MainActivity.weightUnitsIn_kg)
                                        updateMessage(String.format("Apply %skg to the BOOT BACK", ((bodyWeight * patientData[BackPercent]) / 100) + (sittingWeight * (100 - patientData[BackPercent])) / 100));
                                    else
                                        updateMessage(String.format("Apply %slb to the BOOT BACK", ((bodyWeight * patientData[BackPercent]) / 100) + (sittingWeight * (100 - patientData[BackPercent])) / 100));
                                }
                                else
                                    updateMessage("Apply full weight to the BOOT BACK");
                                currentState = CalibrateState.CALIBRATE_BACK;
                                stopRequest = false;
                            }
                            else
                                exitActivity();

                        }
                        else if (currentState == CalibrateState.CALIBRATE_FRONT  ) {
                            viewTitle.setText("Back Weight Calibration");
                            estimateFrontWeight();
                            if(patientData[BackPercent] > 0 ) {
                                if(patientData[BackPercent] < 100) {
                                    if (MainActivity.weightUnitsIn_kg)
                                        updateMessage(String.format("Apply %skg to the BOOT BACK", ((bodyWeight * patientData[BackPercent]) / 100) + (sittingWeight * (100 - patientData[BackPercent])) / 100));
                                    else
                                        updateMessage(String.format("Apply %slb to the BOOT BACK", ((bodyWeight * patientData[BackPercent]) / 100) + (sittingWeight * (100 - patientData[BackPercent])) / 100));
                                }
                                else
                                    updateMessage("Apply full weight to the BOOT BACK");
                                currentState = CalibrateState.CALIBRATE_BACK;
                                stopRequest = false;
                            }
                            else
                                exitActivity();
                        }
                        else if (currentState == CalibrateState.CALIBRATE_BACK   ){
                            estimateBackWeight();
                            currentState = CalibrateState.STOP_TEST;
                            exitActivity();
                        }

                    }
                }
            }
        }
    };
    private void updateMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewPromptMessage.setText(message);
           }
        });
    }
    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();;;
            }
        });
    }
/*================================================================================================*/
// START: Data calculation
    private short averageWeight(short[] data) {
        short weight = 0;
        for (int i = 0; i < measureArraySize; i++) {
            weight += data[i];
        }
        weight = (short) (weight / measureArraySize);
        return weight;
    }
    private short maxWeight(short[] data) {
        short weight = 0;
        for (int i = 0; i < measureArraySize; i++) {
            if(weight < data[i])
                weight = data[i];
        }

        weight = (short) (weight +3);
        return weight;
    }
    private void estimateLiftedWeight() {
        liftedFrontReading = maxWeight(recFrontWeight);
        liftedBackReading = maxWeight(recBackWeight);
        recTotal = 0;
        recIndex = 0;
    }
    private void estimateSittingWeight() {
        sittingFrontReading = averageWeight(recFrontWeight);
        sittingBackReading  = averageWeight(recBackWeight);
        sittingFrontWeight  = maxWeight(recFrontWeight);
        sittingBackWeight   = maxWeight(recBackWeight);
        recTotal = 0;
        recIndex = 0;
    }
    private void estimateFrontWeight() {
        thresholdFrontReading = averageWeight(recFrontWeight);
        recTotal = 0;
        recIndex = 0;
    }
    private void estimateBackWeight() {
        thresholdBackReading = averageWeight(recBackWeight);
    }
/* switch states */
     public void onClickButtonAction(View view) {
         String msg;
         if(recTotal < 10)
             showToast("Please hold your weight steady");
             //viewPromptMessage.setText("Please hold your weight steady");
         else
             stopRequest = true;
    }
}

