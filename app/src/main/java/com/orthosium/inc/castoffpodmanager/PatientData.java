package com.orthosium.inc.castoffpodmanager;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class PatientData implements Serializable {
    static final String[] injuryString = {"None","Toe Touch","Heel","Partial","Full"};
    static final int size = 121 + 12;
    private int noOfDays;
    private int lastIndex;
    public int[] steps;
    public int[] alarms;
    public Calendar startDate;

    public int injuryType;
    public int bodyWeight;
    public int frontPercent;
    public int backPercent;

    public PatientData () {
        noOfDays = 0;
        lastIndex = 0;
        injuryType = 0;
        alarms = new int[size];
        steps = new int[size];
        Arrays.fill(alarms, 0);
        Arrays.fill(steps, 0);
    }
    public void setDataArrays(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.position(121 * 4);
        noOfDays = (int)bb.getShort();
        int startYear = (int)bb.getShort();
        int startDay = (int)bb.getShort();
        injuryType = (int)bb.getShort();
        bodyWeight = (int)bb.getShort();
        backPercent = (int)bb.get();
        frontPercent = (int)bb.get();

        // set weekly start index
        startDate = new GregorianCalendar();
        startDate.set(Calendar.YEAR, startYear);
        startDate.set(Calendar.DAY_OF_YEAR, startDay);
        int firstDayIndex = startDate.get(Calendar.DAY_OF_WEEK) - 1;
        startDate.add(Calendar.DAY_OF_YEAR, - firstDayIndex);
        bb.rewind();
        Arrays.fill(alarms, 0);
        Arrays.fill(steps, 0);
        lastIndex = noOfDays+firstDayIndex;
        for (int i = firstDayIndex; i < (lastIndex + 1); i++) {
            steps[i] = bb.getShort();
            alarms[i] = bb.getShort();
        }
    }
    public File storeMonitoringData() {
        final String[] weekDay = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        String date = String.format("Week Start:	%1$tb %1$td, %1$tY", startDate);
        try {
            String patientDataFile = "Pod_" + MainActivity.deviceAddress + ".txt";
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, patientDataFile);

            FileOutputStream fileStream = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fileStream);

            writer.write(String.format(
            "Body Weight:	%d	Weight-Bearing: %s  MAC Addr:	%s\n",
             bodyWeight, injuryString[injuryType], MainActivity.deviceAddress));
            writer.write(String.format(
            "Week Start:	%1$tb %1$td, %1$tY\n",
             startDate));
            writer.write(String.format("    Steps	Alerts\n"));
            for (int i =0; i < lastIndex; i++) {
                if (i % 7 == 0)
                    writer.write(String.format("Week %d\n", i / 7 + 1));
                writer.write(String.format("%s  %d  %d\n", weekDay[i % 7], steps[i], alarms[i]));
            }
            writer.flush();
            fileStream.getFD().sync();
            writer.close();
            return  file;
        } catch (Exception e) {
            Log.i("Serialize Exception", "TotalMonitorArray: ");
            return null;
        }
    }
    public void removeMonitoringData() {
        try {
            String patientDataFile = "Pod_" + MainActivity.deviceAddress + ".dat";
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, patientDataFile);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            String msg = e.getMessage();
        }
    }
    public float[] getAlarms() {
        float[] data = new float[size];
        for (int i = 0; i < size; i++)
            data[i] = (float) alarms[i];
        return data;
    }
    public float[] getSteps() {
        float[] data = new float[size];
        for (int i = 0; i < size; i++)
            data[i] = (float)steps[i];
        return data;
    }
    public int getNoOfDays() {
        return noOfDays;
    }
    public int getLastIndex() {
        return lastIndex;
    }
    public int getWeeklyDayAlarms(int index) {
        int sum = 0;
        for(int i=0; i<7; i++)
            sum += alarms[index++];
        return sum;
    }
    public int getWeeklyDaySteps(int index) {
        int sum = 0;
        for(int i=0; i<7; i++)
            sum += steps[index++];
        return sum;
    }
    public String getInjuryType() {
        final String[] injuryString = {"None","Toe Touch","Heel","Partial","Full"};
        return injuryString[injuryType];
    }
    static public String getInjuryType(int index) {
        return injuryString[index];
    }
}
