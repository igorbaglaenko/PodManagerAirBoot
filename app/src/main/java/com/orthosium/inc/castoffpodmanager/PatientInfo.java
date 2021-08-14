package com.orthosium.inc.castoffpodmanager;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Hashtable;

public class PatientInfo implements Serializable {
    public static final String patientFileName = "PODProfile.dat";

    public int      size;
    public int      request;    // 0 - invalid; 1 - update; 2 - initialize
    public int      bodyWeight;
    public int      weightAdjustment;
    public int      totalThreshold;
    public int      backThreshold;
    public int      frontThreshold;
    public int      frontPercent;
    public int      backPercent;
    public int      injuryType;
    public String   macAddress;

    public static Hashtable <String, Integer[]> injuryThresholdCoefTable =
            new Hashtable<String,Integer[]>();
    static {
        // Array of coefficients for                            [ Total, Back, Front, InjuryType]
        injuryThresholdCoefTable.put("None",new Integer[]       { 0,     0,    0,     0         });
        injuryThresholdCoefTable.put("Toe Touch",new Integer[]  { 0,     0,    10,    1         });
        injuryThresholdCoefTable.put("Heel",new Integer[]       { 0,     100,  0,     2         });
        injuryThresholdCoefTable.put("Partial",new Integer[]    { 50,    50,   50,    3         });
        injuryThresholdCoefTable.put("Full",new Integer[]       { 100,   100,  100,   4         });
    }
    public static Hashtable <String, Integer> shoeCoefTable =
            new Hashtable<String,Integer>();
    static {
        shoeCoefTable.put("Kids 10", 0);
        shoeCoefTable.put("Kids 11", 1);
        shoeCoefTable.put("Kids 12", 2);
        shoeCoefTable.put("Kids 13", 3);
        shoeCoefTable.put("Kids 1",  4);
        shoeCoefTable.put("Kids 2",  5);
        shoeCoefTable.put("Kids 3",  6);
        shoeCoefTable.put("Kids 4",  7);
        shoeCoefTable.put("Kids 5",  8);
        shoeCoefTable.put("Kids 6",  9);

        shoeCoefTable.put("Women 4", 10);
        shoeCoefTable.put("Women 5", 11);
        shoeCoefTable.put("Women 6", 12);
        shoeCoefTable.put("Women 7", 13);
        shoeCoefTable.put("Women 8", 14);
        shoeCoefTable.put("Women 9", 15);
        shoeCoefTable.put("Women 10",16);
        shoeCoefTable.put("Women 11",17);
        shoeCoefTable.put("Women 12",18);

        shoeCoefTable.put("Men 6",   19);
        shoeCoefTable.put("Men 7",   20);
        shoeCoefTable.put("Men 8",   21);
        shoeCoefTable.put("Men 9",   22);
        shoeCoefTable.put("Men 10",  23);
        shoeCoefTable.put("Men 11",  24);
        shoeCoefTable.put("Men 12",  25);
        shoeCoefTable.put("Men 13",  26);
    }
    public PatientInfo() {
        size = 8*4 + 17;    // 8 integers + MAC addr string
        request = 0;        // 0 - invalid; 1 - update; 2 - initialize
        bodyWeight = 0;
        weightAdjustment = 0;
        totalThreshold = 0;
        backThreshold = 0;
        frontThreshold = 0;
        frontPercent = 0;
        backPercent = 0;
        injuryType = 0;
        macAddress = "";
    }
    public boolean storePatientInfo(File file) {
        boolean res = false;
        try {
            FileOutputStream output = new FileOutputStream(file);
            byte[] macAddrBytes = macAddress.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(size );
                 buf.putInt(size)
                    .putInt(request)
                    .putInt(bodyWeight)
                    .putInt(weightAdjustment)
                    .putInt(totalThreshold)
                    .putInt(backThreshold)
                    .putInt(frontThreshold)
                    .putInt(injuryType)
                    .put(macAddrBytes);
            output.write(buf.array());
            output.flush();
            output.close();
            res = true;
        }
        catch (Exception e) {

            Log.i("Exception", e.getMessage());
        }
        return res;
    }
    public void resetPatientInfo(File file){
        if(file.exists())
            file.delete();
        size = 8*4 + 17;    // 8 integers + MAC addr string
        request = 0;        // 0 - invalid; 1 - update; 2 - initialize
        bodyWeight = 0;
        weightAdjustment = 0;
        totalThreshold = 0;
        backThreshold = 0;
        frontThreshold = 0;
        frontPercent = 0;
        backPercent = 0;
        injuryType = 0;
        macAddress = "";
    }
    public void setThresholds(String injuryIndx) {
        totalThreshold =  0;//(bodyWeight * injuryThresholdCoefTable.get(injuryIndx)[0]) / 100;
        backThreshold = 0;//(bodyWeight * injuryThresholdCoefTable.get(injuryIndx)[1]) / 100;
        frontThreshold = 0;//(bodyWeight * injuryThresholdCoefTable.get(injuryIndx)[2]) / 100;
        injuryType = injuryThresholdCoefTable.get(injuryIndx)[3];
    }
    public void setAdjustment(String shoeIndx) {
        weightAdjustment =  shoeCoefTable.get(shoeIndx);
    }
    public byte[] initOpData(byte mode) {
        final byte[] res = new byte[20];
        ByteBuffer bb = ByteBuffer.wrap(res);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte)mode);
        bb.put((byte) weightAdjustment);
        bb.put((byte) backThreshold);
        bb.put((byte) frontThreshold);
        bb.put((byte) totalThreshold);
        bb.put((byte) frontPercent);
        bb.put((byte) backPercent);
        bb.put((byte) injuryType);
        bb.put((byte) (bodyWeight > 255 ? 255 : bodyWeight));
        Calendar datetime = new GregorianCalendar();
        datetime.setTimeInMillis(System.currentTimeMillis());
        bb.put((byte) datetime.get(Calendar.MINUTE));
        bb.put((byte) datetime.get(Calendar.SECOND));
        bb.put((byte) datetime.get(Calendar.HOUR_OF_DAY));
        bb.putShort((short) datetime.get(Calendar.MILLISECOND));
        bb.putShort((short) datetime.get(Calendar.DAY_OF_YEAR));
        bb.putShort((short) datetime.get(Calendar.YEAR));
        return res;
    }
}
