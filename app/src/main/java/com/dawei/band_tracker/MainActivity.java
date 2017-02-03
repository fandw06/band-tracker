package com.dawei.band_tracker;

import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandAmbientLightEvent;
import com.microsoft.band.sensors.BandAmbientLightEventListener;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandGyroscopeEvent;
import com.microsoft.band.sensors.BandGyroscopeEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;
import com.microsoft.band.sensors.GsrSampleRate;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.SampleRate;

public class MainActivity extends AppCompatActivity {

    private BandClient client = null;
    private Button btnStart;
    private TextView txtStatus;
    private TextView txtGSR;
    private TextView txtEcg;
    private TextView txtTemp;
    private TextView txtLight;
    private TextView txtAcc;
    private TextView txtGyr;
    private CheckBox cbSave;
    private enum SENSOR {GSR, ECG, TEMP, LIGHT, ACC, GYRO};
    private DataWriter writer;

    private static final String dir = "GSRData";
    private File FILES_DIR;
    //	private File file;
//	private FileWriter writer;
    private boolean isRunning = false;
    private boolean isSaved = false;

    private HeartRateConsentListener mHeartRateConsentListener = new HeartRateConsentListener() {
        @Override
        public void userAccepted(boolean b) {
            // handle user's heart rate consent decision
            if (b == true) {
                // Consent has been given, start HR sensor event listener
                startHRListener();
            } else {
                // Consent hasn't been given
                appendToUI(txtStatus, String.valueOf(b));
            }
        }
    };

    public void startHRListener() {
        try {
            // register HR sensor event listener
            client.getSensorManager().registerHeartRateEventListener(mHeartRateEventListener);
        } catch (BandIOException ex) {
            appendToUI(txtStatus, ex.getMessage());
        } catch (BandException e) {
            String exceptionMessage="";
            switch (e.getErrorType()) {
                case UNSUPPORTED_SDK_VERSION_ERROR:
                    exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.";
                    break;
                case SERVICE_ERROR:
                    exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.";
                    break;
                default:
                    exceptionMessage = "Unknown error occurred: " + e.getMessage();
                    break;
            }
            appendToUI(txtStatus, exceptionMessage);

        } catch (Exception e) {
            appendToUI(txtStatus, e.getMessage());
        }
    }

    private BandHeartRateEventListener mHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                String value = String.format("%d /min, %s", event.getHeartRate(), event.getQuality());
                appendToUI(txtEcg, value);
                if (isSaved) {
                    writer.write(SENSOR.ECG, value);
                }
            }
        }
    };

    private BandGsrEventListener mGsrEventListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(final BandGsrEvent event) {
            if (event != null) {
                int value = event.getResistance();
                appendToUI(txtGSR, Integer.toString(value)+" koms");

                if (isSaved) {
                    writer.write(SENSOR.GSR, Integer.toString(value));
                }
            }
        }
    };

    private BandAmbientLightEventListener mAmbientLightEventListener = new BandAmbientLightEventListener() {
        @Override
        public void onBandAmbientLightChanged(final BandAmbientLightEvent event) {
            if (event != null) {
                int value = event.getBrightness();
                appendToUI(txtLight, String.format("%d lux", value));
                if (isSaved) {
                    writer.write(SENSOR.LIGHT, Integer.toString(value));
                }
            }
        }
    };

    private BandSkinTemperatureEventListener mSkinTemperatureEventListener = new BandSkinTemperatureEventListener() {
        @Override
        public void onBandSkinTemperatureChanged(final BandSkinTemperatureEvent event) {
            if (event != null) {
                float value = event.getTemperature();
                appendToUI(txtTemp, String.format("%f Celsius", value));
                if (isSaved) {
                    writer.write(SENSOR.TEMP, Float.toString(value));
                }
            }
        }
    };

    private BandAccelerometerEventListener mAccelerometerEventListener = new BandAccelerometerEventListener() {
        @Override
        public void onBandAccelerometerChanged(final BandAccelerometerEvent event) {
            if (event != null) {
                String value = String.format("%.3f, %.3f, %.3f", event.getAccelerationX(), event.getAccelerationY(), event.getAccelerationZ());
                appendToUI(txtAcc, value);
                if (isSaved) {
                    writer.write(SENSOR.ACC, value);
                }
            }
        }
    };

    private BandGyroscopeEventListener mGyroscopeEventListener = new BandGyroscopeEventListener() {
        @Override
        public void onBandGyroscopeChanged(final BandGyroscopeEvent event) {
            if (event != null) {
                String value = String.format(" %.3f %.3f %.3f", event.getAngularVelocityX(), event.getAngularVelocityY(), event.getAngularVelocityZ());
                appendToUI(txtGyr, value);
                if (isSaved) {
                    writer.write(SENSOR.GYRO, value);
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = (TextView) findViewById(R.id.txtStatus);
        txtGSR = (TextView) findViewById(R.id.value_gsr);
        txtEcg = (TextView) findViewById(R.id.value_ecg);
        txtTemp = (TextView) findViewById(R.id.value_temp);
        txtLight = (TextView) findViewById(R.id.value_light);
        txtAcc = (TextView) findViewById(R.id.value_acc);
        txtGyr = (TextView) findViewById(R.id.value_gyr);
        cbSave = (CheckBox) findViewById(R.id.save);
        btnStart = (Button) findViewById(R.id.btnStart);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRunning) {
                    isRunning = true;
                    btnStart.setText("Stop");
                    txtStatus.setText("");
                    cbSave.setEnabled(false);
                    FILES_DIR = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS), dir);
                    if (isSaved)
                        writer = new DataWriter(FILES_DIR);
                    new SubscriptionTask().execute();
                } else {
                    isRunning = false;
                    btnStart.setText("Start");
                    cbSave.setEnabled(true);
                    if (client != null) {
                        try {
                            // Unregister all listeners.
                            client.getSensorManager().unregisterGsrEventListener(mGsrEventListener);
                            client.getSensorManager().unregisterHeartRateEventListener(mHeartRateEventListener);
                            client.getSensorManager().unregisterAmbientLightEventListener(mAmbientLightEventListener);
                            client.getSensorManager().unregisterSkinTemperatureEventListener(mSkinTemperatureEventListener);
                            client.getSensorManager().unregisterAccelerometerEventListener(mAccelerometerEventListener);
                            client.getSensorManager().unregisterGyroscopeEventListener(mGyroscopeEventListener);
                        } catch (BandException e) {
                            // Do nothing as this is happening during destroy
                        }
                    }
                    if (isSaved)
                        writer.finalize();
                }
            }
        });

        cbSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cbSave.isChecked()) {
                    isSaved = true;
                    appendToUI(txtStatus, "Data is saved to a file");
                }
                else {
                    isSaved = true;
                    appendToUI(txtStatus, "Data is not saved");
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        txtStatus.setText("");
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (client != null) {
            try {
                // Unregister all listeners.
                client.getSensorManager().unregisterGsrEventListener(mGsrEventListener);
                client.getSensorManager().unregisterAmbientLightEventListener(mAmbientLightEventListener);
                client.getSensorManager().unregisterSkinTemperatureEventListener(mSkinTemperatureEventListener);
                client.getSensorManager().unregisterAccelerometerEventListener(mAccelerometerEventListener);
                client.getSensorManager().unregisterGyroscopeEventListener(mGyroscopeEventListener);
                client.disconnect().await();
            } catch (InterruptedException e) {
                // Do nothing as this is happening during destroy
            } catch (BandException e) {
                // Do nothing as this is happening during destroy
            }
        }
        super.onDestroy();
    }

    private class DataWriter {

        /**
         * Local folder which includes sensor data profile and a description file.
         */
        File local;
        /**
         * Currently there are 6 stress related sensors:
         * 	SENSOR {GSR, ECG, TEMP, LIGHT, ACC, GYRO};
         */
        static final int NUMBER = 6;
        /**
         * Start and end time.
         */
        FileWriter info;
        FileWriter dataWriter[];

        public DataWriter(File root) {
            local = new File(root, Long.toString(System.currentTimeMillis()));
            if (!local.mkdir()) {
                Log.w("LocalDir", "Local directory is not created!");
            }
            /**
             * Create related files.
             */
            File dataFile[] = new File[NUMBER];
            for (int i = 0; i<NUMBER; i++) {
                dataFile[i] = new File(local, SENSOR.values()[i].toString()+".txt");
            }
            dataWriter = new FileWriter[NUMBER];
            try {
                for (int i = 0; i<NUMBER; i++)
                    dataWriter[i] = new FileWriter(dataFile[i]);

            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                info = new FileWriter(new File(local, "info.txt"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            long time = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(System.currentTimeMillis());
            try {
                info.append(Long.toString(time)+"\n");
                info.append(cal.getTime().toString()+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void write(SENSOR s, String data) {
            try {
                dataWriter[s.ordinal()].append(data+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void finalize() {
            try {
                long time = System.currentTimeMillis();
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(System.currentTimeMillis());

                info.append(Long.toString(time)+"\n");
                info.append(cal.getTime().toString()+"\n");
                info.close();
                for (int i = 0; i<NUMBER; i++)
                    dataWriter[i].close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        appendToUI(txtStatus, "Band is connected.\n");

                        // Register all listeners.
                        client.getSensorManager().registerGsrEventListener(mGsrEventListener, GsrSampleRate.MS5000);
                        client.getSensorManager().registerAmbientLightEventListener(mAmbientLightEventListener);
                        client.getSensorManager().registerSkinTemperatureEventListener(mSkinTemperatureEventListener);
                        client.getSensorManager().registerAccelerometerEventListener(mAccelerometerEventListener, SampleRate.MS128);
                        client.getSensorManager().registerGyroscopeEventListener(mGyroscopeEventListener, SampleRate.MS128);
                        if (client.getSensorManager().getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                            startHRListener();
                        } else {
                            client.getSensorManager().requestHeartRateConsent(MainActivity.this, mHeartRateConsentListener);
                        }
                    } else {
                        appendToUI(txtStatus, "The Gsr sensor is not supported with your Band version. Microsoft Band 2 is required.\n");
                    }
                }
                else
                    appendToUI(txtStatus, "Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");

            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(txtStatus, exceptionMessage);

            } catch (Exception e) {
                appendToUI(txtStatus, e.getMessage());
            }
            return null;
        }
    }

    private void appendToUI(final TextView view, final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.setText(string);
            }
        });
    }

    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (client == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                appendToUI(txtStatus, "Band isn't paired with your phone.\n");
                return false;
            }
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }

        appendToUI(txtStatus, "Band is connecting...\n");
        return ConnectionState.CONNECTED == client.connect().await();
    }
}
