package com.orthosium.inc.castoffpodmanager;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import static com.orthosium.inc.castoffpodmanager.PatientInfo.injuryThresholdCoefTable;

public class EntryDataActivity extends AppCompatActivity {

    private EditText    bodyWeight;
    private EditText    shoeSize;
    private RadioGroup  shoeTypeIndex;
    private RadioButton shoeType;
    private Spinner     injuryType;
    private EditText    frontPercent;
    private EditText    backPercent;
    private TextView    weightUnits;
    public  PatientInfo patientInfo;
    private MenuItem    unitsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry_data);

        bodyWeight = findViewById(R.id.data_BodyWeight);
        shoeSize = findViewById(R.id.data_ShoeSize);
        shoeTypeIndex = findViewById(R.id.data_ShoeTypeIndex);
        frontPercent = findViewById(R.id.data_frontPercent);
        backPercent = findViewById(R.id.data_backPercent);
        injuryType = findViewById(R.id.data_InjuryType);
        weightUnits = findViewById(R.id.view_weight_units);
        if(MainActivity.weightUnitsIn_kg)
            weightUnits.setText("kg");
        else
            weightUnits.setText("lb");

        injuryType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,long arg3) {

                String injuryIndx = arg0.getItemAtPosition(arg2).toString();
                if(injuryIndx.equals("Select Mode")) {
                    backPercent.setText("   -");
                    frontPercent.setText("   -");
                }
                else {
                    backPercent.setText(String.valueOf(injuryThresholdCoefTable.get(injuryIndx)[1]));
                    frontPercent.setText(String.valueOf(injuryThresholdCoefTable.get(injuryIndx)[2]));
//                    switch(injuryThresholdCoefTable.get(injuryIndx)[3]))
                    switch(arg2) {
                        case 1:
                            backPercent.setEnabled(false);
                            frontPercent.setEnabled(false);
                            break;
                        case 2:
                            backPercent.setEnabled(false);
                            frontPercent.setEnabled(true);
                            break;
                        case 3:
                            backPercent.setEnabled(false);
                            frontPercent.setEnabled(true);
                            break;
                        case 4:
                            backPercent.setEnabled(true);
                            frontPercent.setEnabled(true);
                            break;
                        case 5:
                            backPercent.setEnabled(false);
                            frontPercent.setEnabled(false);
                            break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }});
    }
    // Option Menu GUI implementation
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.weightunits_menu, menu);
        MenuItem entry = menu.findItem(R.id.view_weight_menu);
        if(MainActivity.weightUnitsIn_kg)
            entry.setTitle("Weight in lb");
        else
            entry.setTitle("Weight in kg");

        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if(id == R.id.view_weight_menu) {
            String n = item.getTitle().toString();
            String weight = bodyWeight.getText().toString();
            switch (n) {
                case "Weight in kg":
                    item.setTitle("Weight in lb");
                    bodyWeight.setText("");
                    weightUnits.setText("kg");
                    MainActivity.weightUnitsIn_kg = true;
                    break;
                case "Weight in lb":
                    item.setTitle("Weight in kg");
                    bodyWeight.setText("");
                    weightUnits.setText("lb");
                    MainActivity.weightUnitsIn_kg = false;
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    private String validateEntries(){
        String str = bodyWeight.getText().toString();
        if(str.isEmpty())
            return "Please enter BodyWeight";
        int data = Integer.valueOf(str);
        if(MainActivity.weightUnitsIn_kg)
            data = (int)Math.round((double)data / 0.454);
        if(data > 600 || data < 15)
            return "Please correct Body Weight";

        if(shoeSize.getText().toString().isEmpty())
            return "Please enter Shoe Size";
        shoeType = findViewById(shoeTypeIndex.getCheckedRadioButtonId());
        if(shoeType == null)
            return "Please select Shoe Type";
        if(injuryType.getSelectedItem().toString().equals("Select Mode"))
            return "Please select Weight Bearing";
        String shoeIndex = String.format("%s %s", shoeType.getText().toString(),shoeSize.getText().toString());
        if(PatientInfo.shoeCoefTable.get(shoeIndex) == null)
            return "Please verify shoe size and type";

        return "";
    }
    public void onButtonEmailDataClick(View view) {
        CharSequence validateMsg = validateEntries();
        if(!validateMsg.toString().isEmpty())
        {
            Toast.makeText(this, validateMsg, Toast.LENGTH_SHORT).show();
            return;
        }
        patientInfo = new PatientInfo();
        patientInfo.macAddress = MainActivity.deviceAddress;
        patientInfo.request = 2; // Initiate monitoring
        patientInfo.bodyWeight = Integer.valueOf(bodyWeight.getText().toString());
        if (MainActivity.weightUnitsIn_kg)
            patientInfo.bodyWeight = (int)(Math.round((double)patientInfo.bodyWeight / 0.454));
        String shoeIndex = String.format("%s %s", shoeType.getText().toString(),shoeSize.getText().toString());
        patientInfo.setAdjustment(shoeIndex);
        String injuryIndex = injuryType.getSelectedItem().toString();
        patientInfo.setThresholds(injuryIndex);
        patientInfo.backPercent = Integer.valueOf(backPercent.getText().toString());
        patientInfo.frontPercent = Integer.valueOf(frontPercent.getText().toString());
        patientInfo.backThreshold = (patientInfo.bodyWeight *  patientInfo.backPercent) / 100;
        patientInfo.frontThreshold = (patientInfo.bodyWeight * patientInfo.frontPercent ) / 100;

        if(storePatientInfo()) {
            byte[] podData = patientInfo.initOpData((byte)0);
            Intent returnIntent = new Intent();
            returnIntent.putExtra(MainActivity.INTENT_RESPONSE_ENTRY_DATA,podData);
            setResult(MainActivity.RESPONSE_ENTRY_DATA, returnIntent);
            finish();
        }
    }
    private boolean storePatientInfo() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, PatientInfo.patientFileName);
        if(! patientInfo.storePatientInfo(file))
        {
            Toast.makeText(this, R.string.status_FailToSaveInfo, Toast.LENGTH_SHORT).show();
            return false;
        }
        Toast.makeText(this, R.string.status_InfoSaved, Toast.LENGTH_SHORT).show();
        return true;

    }

}
