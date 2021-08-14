package com.orthosium.inc.castoffpodmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class ReviseDataActivity extends AppCompatActivity {
    public static final int REQUEST_EMAIL_RESULTS = 70;

    public PatientData patientData;
    private BarGraph mWeeklyGraph;
    private TextView noOfSteps;
    private TextView noOfAlerts;
    private TextView injuryType;
    private TextView totalDays;
    private TextView frontPercent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_revise_data);

        noOfSteps = findViewById(R.id.data_NoOFSteps);
        noOfAlerts = findViewById(R.id.data_NoOfAlerts);
        injuryType = findViewById(R.id.data_InjuryType);
        totalDays = findViewById(R.id.data_NumberOfDays);
        frontPercent = findViewById(R.id.data_front_percent);

        Bundle extraData = getIntent().getExtras();
        byte[] podData = extraData.getByteArray(MainActivity.INTENT_REVISE_DATA_ACTIVITY);
        patientData = new PatientData();
        patientData.setDataArrays(podData);
        injuryType.setText(patientData.getInjuryType());
        totalDays.setText(Integer.toString(patientData.getNoOfDays()));
        frontPercent.setText(String.format("Front weight: %d %%; Back weight: %d %%",patientData.frontPercent,patientData.backPercent));
        mWeeklyGraph = findViewById(R.id.barGraph);
        mWeeklyGraph.setLimits(5000f, patientData.getLastIndex(), patientData.startDate );
        mWeeklyGraph.updateGraph(patientData.getSteps(), patientData.getAlarms(),patientData.getLastIndex());

        noOfAlerts.setText(Integer.toString(patientData.getWeeklyDayAlarms(mWeeklyGraph.weekIndex())));
        noOfSteps.setText(Integer.toString(patientData.getWeeklyDaySteps(mWeeklyGraph.weekIndex())));

        registerReceiver(mGraphUpdateReceiver, makeGraphUpdateIntentFilter());
    }
    @Override
    public void onBackPressed() {
 //       unregisterReceiver(mGraphUpdateReceiver);
        setResult(MainActivity.RESPONSE_REVISE_DATA_SENT);
        super.onBackPressed();
    }
    @Override
    protected void onDestroy() {
        unregisterReceiver(mGraphUpdateReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.podmanager_menu,menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.store_result:
                onSendResults();
                break;
            case R.id.change_bearing:
                setResult(MainActivity.RESPONSE_REVISE_THRESHOLDS);
                finish();
                break;
            case R.id.reset_pod:
                setResult(MainActivity.RESPONSE_REVISE_ENTRY);
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
/* END: Option Menu GUI implementation */
    private final BroadcastReceiver mGraphUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BarGraph.ACTION_CHANGE_GRAPH.equals(action)) {
                int weekIndex = intent.getIntExtra(BarGraph.ACTION_WEEK_INDEX, 0);
                noOfAlerts.setText(Integer.toString(patientData.getWeeklyDayAlarms(weekIndex)));
                noOfSteps.setText(Integer.toString(patientData.getWeeklyDaySteps(weekIndex)));
            }
        }
    };
    private static IntentFilter makeGraphUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BarGraph.ACTION_CHANGE_GRAPH);
        return intentFilter;
    }

// START: Popup Dialog Implementation
    private View popupInputDialogView = null;
    private Spinner injuryList = null;
    private TextView comStatus;
    private Button saveUserDataButton = null;
    private Button cancelUserDataButton = null;
    private void initPopupViewControls() {
        // Get layout inflater object.
        LayoutInflater layoutInflater = LayoutInflater.from(ReviseDataActivity.this);

        // Inflate the popup dialog from a layout xml file.
        popupInputDialogView = layoutInflater.inflate(R.layout.popup_restore_req, null);

        // Get user input edittext and button ui controls in the popup dialog.
        injuryList =  popupInputDialogView.findViewById(R.id.data_InjuryType);
        injuryList.setSelection(0);
        saveUserDataButton = popupInputDialogView.findViewById(R.id.button_save_user_data);
        cancelUserDataButton = popupInputDialogView.findViewById(R.id.button_cancel_user_data);
        comStatus = popupInputDialogView.findViewById(R.id.data_BleCommunicationStatus);

    }
    public void displayCompleteMsg() {
        // Create a AlertDialog Builder.
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ReviseDataActivity.this);
        // Set title, icon, can not cancel properties.
        alertDialogBuilder.setTitle(getString(R.string.menu_ChangeWeightBearing));
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
            public void onClick(View view)
            {
                // Get user data from popup dialog editeext.
                int injuryIndex = injuryList.getSelectedItemPosition();
                if(injuryIndex > 0) {
                    Intent intent = new Intent();
                    intent.putExtra(MainActivity.INTENT_RESPONSE_REVISE_DATA,(byte)(injuryIndex-1));
                    setResult(MainActivity.RESPONSE_REVISE_THRESHOLDS, intent);
                    finish();
                    alertDialog.dismiss();
                }
                else {
                    Toast.makeText(getApplicationContext(), "Please select Weight Bearing", Toast.LENGTH_SHORT).show();
                }
//                onSendData(email);
            }
        });

        cancelUserDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog.dismiss();
            }
        });
    }
// END: Popup Dialog Implementation
    private void onSendResults() {
        File resultFile = patientData.storeMonitoringData();
        if(resultFile != null) {
            Intent email = new Intent(Intent.ACTION_SEND);
            email.setType("text/plain");
            email.putExtra(Intent.EXTRA_EMAIL, new String[] {"support@orthosium.com"});
            email.putExtra(Intent.EXTRA_SUBJECT, "Pressure Monitor Results");
            String message ="Results for POD MAC: " + MainActivity.deviceAddress;
            email.putExtra(Intent.EXTRA_TEXT,message);
            Uri uri = FileProvider.getUriForFile(this, getString(R.string.file_provider_authority), resultFile);
            email.putExtra(Intent.EXTRA_STREAM, uri);
            email.setType("message/rfc822");
            startActivityForResult(email, REQUEST_EMAIL_RESULTS);
        }
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_EMAIL_RESULTS) {
            patientData.removeMonitoringData();
            setResult(MainActivity.RESPONSE_REVISE_DATA_SENT);
            finish();
        }
    }
}
