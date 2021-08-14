package com.orthosium.inc.castoffpodmanager;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class UpdateEntryDataActivity extends AppCompatActivity {

    private TextView bodyWeight;
    private TextView injuryType;
    private EditText frontPercent;
    private EditText backPercent;
    private TextView weightUnits;
    private TextView titleBodyWeight;
    short[] patientData;
    int bodyWeightIn_lb;
    int bodyWeightIn_kg;

    //private boolean mWeightInKg;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_entry_data);

        Bundle extraData = getIntent().getExtras();
        patientData = extraData.getShortArray(MainActivity.INTENT_UPDATE_ENTRY_ACTIVITY);

        titleBodyWeight = findViewById(R.id.textBodyWeight);
        bodyWeight   = findViewById(R.id.data_bodyWeight);
        injuryType   = findViewById(R.id.data_injuryType);
        frontPercent = findViewById(R.id.data_frontPercent2);
        backPercent  = findViewById(R.id.data_backPercent2);
        weightUnits  = findViewById(R.id.view_weight_menu);

        if(patientData[2] < 255 ) {
            bodyWeightIn_lb = patientData[2];
            bodyWeightIn_kg = (int) ((double) bodyWeightIn_lb * 0.454);;
            if (MainActivity.weightUnitsIn_kg) {
                bodyWeight.setText(String.valueOf(bodyWeightIn_kg));
                weightUnits.setText("kg");
            }
            else {
                weightUnits.setText("lb");
                bodyWeight.setText(String.valueOf(bodyWeightIn_lb));
            }
        }
        else {
            bodyWeight.setVisibility(View.INVISIBLE);
            weightUnits.setVisibility(View.INVISIBLE);
            titleBodyWeight.setVisibility(View.INVISIBLE);
        }

        injuryType.setText(PatientData.getInjuryType((int)patientData[3]));
        frontPercent.setText(String.valueOf(patientData[1]));
        backPercent.setText(String.valueOf(patientData[0]));

    }

    // Option Menu GUI implementation
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        if(patientData[2] < 255 ) {
            getMenuInflater().inflate(R.menu.weitunit_menu2, menu);
            MenuItem entry = menu.findItem(R.id.weight_units);
            if(MainActivity.weightUnitsIn_kg)
                entry.setTitle("Weight in lb");
            else
                entry.setTitle("Weight in kg");
        }
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if(id == R.id.weight_units) {
            String n = item.getTitle().toString();
            switch (n) {
                case "Weight in kg":
                    item.setTitle("Weight in lb");
                    bodyWeight.setText(String.valueOf(bodyWeightIn_kg));
                    weightUnits.setText("kg");
                    MainActivity.weightUnitsIn_kg = true;
                    break;
                case "Weight in lb":
                    item.setTitle("Weight in kg");
                    bodyWeight.setText(String.valueOf(bodyWeightIn_lb));
                    weightUnits.setText("lb");
                    MainActivity.weightUnitsIn_kg = false;
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    public void onBackPressed() {
        super.onBackPressed();
    }
    public void onButtonClick(View view) {
        patientData[1] = Short.valueOf(frontPercent.getText().toString());
        patientData[0] = Short.valueOf(backPercent.getText().toString());

        Intent returnIntent = new Intent();
        returnIntent.putExtra(MainActivity.INTENT_UPDATE_ENTRY_DATA,patientData);
        setResult(MainActivity.RESPONSE_UPDATE_DATA, returnIntent);
        finish();
    }
}
