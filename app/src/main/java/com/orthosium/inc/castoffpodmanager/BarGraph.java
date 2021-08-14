package com.orthosium.inc.castoffpodmanager;


import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import java.util.Calendar;

/**
 * TODO: document your custom view class.
 */
public class BarGraph extends View implements
        GestureDetector.OnGestureListener {
    public final static String ACTION_CHANGE_GRAPH =
            "orthosium.inc.castoffpodmanager.ACTION_CHANGE_GRAPH";
    public final static String ACTION_WEEK_INDEX =
            "orthosium.inc.castoffpodmanager.ACTION_WEEK_INDEX";
    private GestureDetectorCompat mDetector;
    static final int fontSize = 14;
    float density;
    static final float smallTick = 10f;

    private float yTickTextWidth;
    private float tickTextHeight;
    private Paint gPaint;
    private float[] stepNo;
    private float[] alarmNo;
    private int firstViewIndex;
    private int lastIndex;
    private int intervalSize;

    private float    yMaxValue;
    private String[] xMajorStrings = {"S","M","T","W","T","F","S"};

    Calendar startDateTime;
    private String startDay;
    private String endDay;


    Handler mHandler;
    public BarGraph(Context context) {
        super(context);
        init(null, 0);
        //   mDetector = new GestureDetectorCompat(context,this);
        // mDetector.setOnDoubleTapListener(this);
    }
    public BarGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
        mDetector = new GestureDetectorCompat(context,this);
    }
    public BarGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
        mDetector = new GestureDetectorCompat(context,this);
    }
    private void init(AttributeSet attrs, int defStyle) {
        // Set up a default TextPaint object
        gPaint = new Paint();
        gPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        density = getContext().getResources().getDisplayMetrics().scaledDensity;
        gPaint.setTextSize(fontSize * density);
        yMaxValue = 5000f;
        intervalSize = 7;
        mHandler = new Handler();

    }
    // Instantiate the gesture detector with the
    // application context and an implementation of
    // GestureDetector.OnGestureListener



    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int graphFontSize = 14;
        final int msgFontSize = 18;
//        float density = canvas.getDensity();
//
//        gPaint.setTextSize(graphFontSize * density);
        tickTextHeight = (gPaint.getFontMetricsInt().bottom - gPaint.getFontMetrics().top);
        float posX0 = smallTick;
        float posXmax = getWidth();
        float posYmax = 2 * tickTextHeight;
        float posY0 = getHeight() - tickTextHeight - smallTick;

        // draw plot box
        gPaint.setColor(Color.BLACK);
        gPaint.setStrokeWidth(1f);
        canvas.drawLine(posX0, posY0,posXmax, posY0,gPaint);
        canvas.drawLine(posXmax, posY0,posXmax, posYmax,gPaint);
        canvas.drawLine(posXmax, posYmax,posX0, posYmax,gPaint);
        canvas.drawLine(posX0, posYmax,posX0, posY0,gPaint);


        if(startDateTime != null) {
            Calendar datetime = (Calendar)startDateTime.clone();
            datetime.add(Calendar.DAY_OF_YEAR, firstViewIndex);
            String data = String.format("%1$tb %1$td    -    ", datetime);
            datetime.add(Calendar.DAY_OF_YEAR, 6);
            data += String.format("%1$tb %1$td", datetime);
            gPaint.setTextAlign(Paint.Align.RIGHT);
            gPaint.setTextSize(msgFontSize * density);
            canvas.drawText(data, posXmax-20, (1.5f * tickTextHeight), gPaint);
            gPaint.setTextSize(graphFontSize * density);
        }
        // draw X-axis ticks and labels
        // draw data bars
        float dX = (posXmax - posX0) / (intervalSize );
        float dY = (posY0- posYmax) /yMaxValue;
        float barStart = posX0 + dX / 8f;
        float barEnd = posX0 + dX * 7f / 8f;
        float barCenter = posX0 + dX / 2f;
        int dataIndex = firstViewIndex;

        if(stepNo != null && alarmNo != null) {
            gPaint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < intervalSize; i++) {
                float correction = alarmNo[dataIndex] > 0 ? 2: 1;
                float stepY = (stepNo[dataIndex] * dY < correction*tickTextHeight)? posY0 - correction*tickTextHeight: posY0 - stepNo[dataIndex] * dY;
                float alarmY = (alarmNo[dataIndex] * dY < tickTextHeight)? posY0 - tickTextHeight: posY0 - alarmNo[dataIndex] * dY;
                if (stepNo[dataIndex] > 0) {
                    gPaint.setColor(Color.GRAY);
                    canvas.drawRect(barStart + dX * i, stepY, barEnd + dX * i, posY0, gPaint);
                    gPaint.setColor(Color.WHITE);
                    String data = String.format("%1d", (int) stepNo[dataIndex]);
                    canvas.drawText(data, barCenter + dX * i, stepY + tickTextHeight * 0.75f, gPaint);
                }
                if (alarmNo[dataIndex] > 0) {
                    gPaint.setColor(Color.RED);
                    canvas.drawRect(barStart + dX * i, alarmY, barEnd + dX * i, posY0, gPaint);
                    gPaint.setColor(Color.DKGRAY);
                    String data = String.format("%1d", (int) alarmNo[dataIndex]);
                    canvas.drawText(data, barCenter + dX * i, alarmY + tickTextHeight * 0.75f, gPaint);
                }
                dataIndex++;
            }
        }
        // draw X-axis ticks and tick labels
        gPaint.setTextAlign(Paint.Align.CENTER);
        gPaint.setColor(Color.BLACK);
        dX = (posXmax - posX0) / (xMajorStrings.length);
        for(int i = 0; i < xMajorStrings.length; i++)
        {
            float xPoint = posX0 + dX * (i + 0.5f);
            canvas.drawLine(xPoint, posY0, xPoint,posY0 + smallTick, gPaint);
            canvas.drawText(xMajorStrings[i],xPoint,posY0 + smallTick + tickTextHeight * 0.7f, gPaint);
        }
    }
    public void updateGraph(float[] steps, float[]alarms, int indx) {
        stepNo = steps.clone();
        alarmNo = alarms.clone();
        lastIndex = indx;
        invalidate();
    }
    public void setLimits(float maxYValue, int lastIndx, Calendar date) {
        yMaxValue = maxYValue;
        lastIndex = lastIndx;
        firstViewIndex = (lastIndex / intervalSize) * intervalSize;
        startDateTime = date;
    }
    public int weekIndex () {return firstViewIndex; }
    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }
    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2,
                           float velocityX, float velocityY) {
        int oldInterval = firstViewIndex;
        if(velocityX > 100)
        {
            firstViewIndex = (firstViewIndex - intervalSize) < 0 ? 0 : firstViewIndex - intervalSize;
        }
        else if(velocityX < -100)
        {
            firstViewIndex = (firstViewIndex + intervalSize) > lastIndex ? firstViewIndex : firstViewIndex + intervalSize;
        }
        if(oldInterval != firstViewIndex) {
            invalidate();
            if(intervalSize == 7) {
                final Intent intent = new Intent(ACTION_CHANGE_GRAPH);
                intent.putExtra(ACTION_WEEK_INDEX,firstViewIndex);
                getContext().sendBroadcast(intent);
            }
        }
        return false;    }
    @Override
    public void onLongPress(MotionEvent event) {
        // Log.d(DEBUG_TAG, "onLongPress: " + event.toString());
    }
    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                            float distanceY) {
        return true;
    }
    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }
    @Override
    public void onShowPress(MotionEvent event) {

    }
    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        // Log.d(DEBUG_TAG, "onSingleTapUp: " + event.toString());
        return true;
    }
}


