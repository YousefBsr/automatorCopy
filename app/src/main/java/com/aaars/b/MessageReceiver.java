package com.aaars.b;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;

public class MessageReceiver extends BroadcastReceiver {
    private static MessageListener mListener;
    private SmsManager smsManager;
    private Module md2;
    private DatabaseReference last;
    private LastRun lr;
    private GoogleSignInAccount account;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        try {
            account = GoogleSignIn.getLastSignedInAccount(context);
            if (account != null && Root.USER_ID == null) {
                Root.USER_ID = account.getId();
            }
            smsManager = SmsManager.getDefault();
            boolean flag = true;
            //LASTRUN
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            last = database.getInstance().getReference().child("users").child(Root.USER_ID).child("lastrun");
            ValueEventListener lastr = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if(dataSnapshot.exists() && flag) {
                        lr = dataSnapshot.getValue(LastRun.class);
                        processMessage(context, intent);
                        flag = false;
                    }
                }
                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Toast.makeText(context, "Failed to load post.", Toast.LENGTH_SHORT).show();
                }
            };
            last.addValueEventListener(lastr);
        }
        catch (Exception e) {
            Toast.makeText(context, "Module Ran, Restart app to sync with server", Toast.LENGTH_LONG).show();
        }
    }

    private void processMessage(Context context, Intent intent) {
        try {
            md2 = new Module();
            if (account != null && Root.USER_ID == null) {
                Root.USER_ID = account.getId();
            }
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            DatabaseReference drmd2 = database.getInstance().getReference().child("users").child(Root.USER_ID).child("modules").child("6");
            ValueEventListener wifiTimerListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        md2 = dataSnapshot.getValue(Module.class);
                        Bundle data = intent.getExtras();
                        Object[] pdus = (Object[]) data.get("pdus");
                        for (int i = 0; i < pdus.length; i++) {
                            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdus[i]);
                            processSmsMessage(context, smsMessage);
                        }
                    }
                }
                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Toast.makeText(context, "Failed to load post.", Toast.LENGTH_SHORT).show();
                }
            };
            drmd2.addValueEventListener(wifiTimerListener);
        } catch (Exception e) {
            Toast.makeText(context, "Module Ran, Restart app to sync with server", Toast.LENGTH_LONG).show();
        }
    }

    private void processSmsMessage(Context context, SmsMessage smsMessage) {
        try {
            if (smsMessage != null) {
                String body = smsMessage.getDisplayMessageBody();
                if (body != null) {
                    String[] parts = body.split(" ");
                    if (parts.length >= 2) {
                        String command = parts[0].toLowerCase();
                        String parameter = parts[1];
                        switch (command) {
                            case "wifi":
                                handleWifiCommand(context, parameter);
                                break;
                            case "volume":
                                handleVolumeCommand(context, parameter);
                                break;
                            case "location":
                                handleLocationCommand(context, parameter);
                                break;
                            case "lostphone":
                                handleLostPhoneCommand(context, parameter);
                                break;
                            default:
                                // Handle unrecognized commands
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error processing SMS message", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleWifiCommand(Context context, String parameter) {
        if (md2.parameters.get(1).equals("true")) {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                boolean enable = parameter.equalsIgnoreCase("on");
                wifiManager.setWifiEnabled(enable);
                Calendar cc = Calendar.getInstance();
                lr.lastrun.set(enable ? 6 : 7,"" + cc.getTime());
                last.setValue(lr);
            }
        }
    }

    private void handleVolumeCommand(Context context, String parameter) {
        AudioManager mobilemode = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mobilemode.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        if (md2.parameters.get(3).equals("true")) {
            if (parameter.equalsIgnoreCase("max")) {
                setVolumeToMax(mobilemode);
            } else if (parameter.equalsIgnoreCase("min")) {
                setVolumeToMin(mobilemode);
            }
        }
    }

    private void setVolumeToMax(AudioManager mobilemode) {
        mobilemode.setStreamVolume(AudioManager.STREAM_MUSIC, mobilemode.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        mobilemode.setStreamVolume(AudioManager.STREAM_ALARM, mobilemode.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        mobilemode.setStreamVolume(AudioManager.STREAM_RING, mobilemode.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
        Calendar cc = Calendar.getInstance();
        lr.lastrun.set(8,"" + cc.getTime());
        last.setValue(lr);
    }

    private void setVolumeToMin(AudioManager mobilemode) {
        mobilemode.setStreamVolume(AudioManager.STREAM_MUSIC, mobilemode.getStreamMinVolume(AudioManager.STREAM_MUSIC), 0);
        mobilemode.setStreamVolume(AudioManager.STREAM_ALARM, mobilemode.getStreamMinVolume(AudioManager.STREAM_ALARM), 0);
        mobilemode.setStreamVolume(AudioManager.STREAM_RING, mobilemode.getStreamMinVolume(AudioManager.STREAM_RING), 0);
        Calendar cc = Calendar.getInstance();
        lr.lastrun.set(9,"" + cc.getTime());
        last.setValue(lr);
    }

    private void handleLocationCommand(final Context context, String parameter) {
        if (md2.parameters.get(6).equals("true")) {
            final LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(final Location location) {
                    FirebaseDatabase database = FirebaseDatabase.getInstance();
                    DatabaseReference ref = database.getReference("users").child(Root.USER_ID).child("modules");
                    ref.child("1").child("currentLocation").setValue(location);
                    Calendar cc = Calendar.getInstance();
                    lr.lastrun.set(12,"" + cc.getTime());
                    last.setValue(lr);
                }
                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {}
                @Override
                public void onProviderEnabled(String s) {}
                @Override
                public void onProviderDisabled(String s) {}
            };
            locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
            if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15,
                        50, locationListener);
            Calendar cc = Calendar.getInstance();
            lr.lastrun.set(12,"" + cc.getTime());
            last.setValue(lr);
            Location lc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            String content = (lc != null) ? "I am here: https://www.google.com/maps/search/?api=1&query=" + lc.getLatitude() + "," + lc.getLongitude() + " - Sent via Automator" : "UNABLE";
            smsManager.sendTextMessage(smsMessage.getOriginatingAddress(), null, content, null, null);
        }
    }

    private void handleLostPhoneCommand(final Context context, String parameter) {
        if (md2.parameters.get(5).equals("true")) {
            // Set audio to max
            setVolumeToMax((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
            // Enable WiFi
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.setWifiEnabled(true);
            }
            // Send location
            sendLocation(context);
            // Play alarm sound
            MediaPlayer mpintro = MediaPlayer.create(context, R.raw.alarm);
            mpintro.setLooping(false);
            mpintro.start();
        }
    }

    private void sendLocation(Context context) {
        final LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(final Location location) {
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                DatabaseReference ref = database.getReference("users").child(Root.USER_ID).child("modules");
                ref.child("1").child("currentLocation").setValue(location);
            }
            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {}
            @Override
            public void onProviderEnabled(String s) {}
            @Override
            public void onProviderDisabled(String s) {}
        };
        locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15,
                    50, locationListener);
        Location lc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        String content = (lc != null) ? "I am here: https://www.google.com/maps/search/?api=1&query=" + lc.getLatitude() + "," + lc.getLongitude() + " - Sent via Automator" : "UNABLE";
        smsManager.sendTextMessage(smsMessage.getOriginatingAddress(), null, content, null, null);
    }

    public static void bindListener(MessageListener listener){
        mListener = listener;
    }
}
