package com.murraycole.wearcommunication;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Collection;
import java.util.HashSet;


public class GooglePlayServicesActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

    private static final String TAG = "GooglePlayServicesActivity";

    private static final String KEY_IN_RESOLUTION = "is_in_resolution";
    private static final String FITNESS_IN_RES = "is_in_resolution";

    public static final String START_ACTIVITY_PATH = "/start/MainActivity";

    public static final String CONNECT_FITNESS = "/connect/fitness";

    /**
     * Request code for auto Google Play Services error resolution.
     */
    protected static final int REQUEST_CODE_RESOLUTION = 1;
    protected static final int REQUEST_FITNESS_RES = 2;

    /**
     * Google API client.
     */
    private GoogleApiClient mGoogleApiClient;

    private GoogleApiClient mFitnessClient;

    /**
     * Determines if the client is in a resolution state, and
     * waiting for resolution intent to return.
     */
    private boolean mWearIsInResolution;
    private boolean mFitnessIsInRes;

    /**
     * Called when the activity is starting. Restores the activity state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mWearIsInResolution = savedInstanceState.getBoolean(KEY_IN_RESOLUTION, false);
            mFitnessIsInRes = savedInstanceState.getBoolean(FITNESS_IN_RES,false);
        }

        setContentView(R.layout.activity_main);
        buildFitnessClient();
        Button msgButton = (Button) findViewById(R.id.msgButton);
        msgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new StartWearableTask().execute();
            }
        });
    }

    /**
     * Called when the Activity is made visible.
     * A connection to Play Services need to be initiated as
     * soon as the activity is visible. Registers {@code ConnectionCallbacks}
     * and {@code OnConnectionFailedListener} on the
     * activities itself.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        mGoogleApiClient.connect();

        if (mFitnessClient == null){
            buildFitnessClient();
        }
        mFitnessClient.connect();
    }

    /**
     * Called when activity gets invisible. Connection to Play Services needs to
     * be disconnected as soon as an activity is invisible.
     */
    @Override
    protected void onStop() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            Wearable.MessageApi.removeListener(mGoogleApiClient,this);
        }
        if (mFitnessClient != null){
            mFitnessClient.disconnect();
        }
        super.onStop();
    }

    /**
     * Saves the resolution state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_RESOLUTION, mWearIsInResolution);
        outState.putBoolean(FITNESS_IN_RES,mFitnessIsInRes);
    }

    /**
     * Handles Google Play Services resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_RESOLUTION:
                retryConnecting();
                break;
            case REQUEST_FITNESS_RES:
                retryFitnessConnect();
                break;
        }
    }

    private void retryConnecting() {
        mWearIsInResolution = false;
        if (!mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }

    }

    /**
     * Called when {@code mGoogleApiClient} is connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "GoogleApiClient connected");
        // TODO: Start making API requests.
        Wearable.MessageApi.addListener(mGoogleApiClient,this);
    }

    /**
     * Called when {@code mGoogleApiClient} connection is suspended.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
        retryConnecting();
    }

    /**
     * Called when {@code mGoogleApiClient} is trying to connect but failed.
     * Handle {@code result.getResolution()} if there is a resolution
     * available.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // Show a localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(
                    result.getErrorCode(), this, 0, new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            retryConnecting();
                        }
                    }).show();
            return;
        }
        // If there is an existing resolution error being displayed or a resolution
        // activity has started before, do nothing and wait for resolution
        // progress to be completed.
        if (mWearIsInResolution) {
            return;
        }
        mWearIsInResolution = true;
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
            retryConnecting();
        }
    }

    private void sendStartActivityMessage (String nodeId){
        Wearable.MessageApi.sendMessage(
                mGoogleApiClient,nodeId,START_ACTIVITY_PATH, new byte[0]).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()){
                            Log.e(TAG,"Failed to send msg with status code: "
                                + sendMessageResult.getStatus().getStatusCode());
                        }
                    }
                }
        );

    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }
        return results;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(CONNECT_FITNESS)){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(GooglePlayServicesActivity.this,"FitnessConnect",Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private class StartWearableTask extends AsyncTask<Void,Void,Void>{
        @Override
        protected Void doInBackground(Void... params) {
            Collection<String> nodes = getNodes();
            for (String n : nodes){
                sendStartActivityMessage(n);
            }
            return null;
        }
    }


    private void buildFitnessClient(){
        mFitnessClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.API)
                .addScope(Fitness.SCOPE_ACTIVITY_READ)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.d(TAG, "Fitness client connected");
                        new StartWearableTask().execute();

                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d(TAG, "Fitness client suspended");
                        retryFitnessConnect();

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "Fitness Connection failed");
                        if (!result.hasResolution()) {
                            // Show a localized error dialog.
                            GooglePlayServicesUtil.getErrorDialog(
                                    result.getErrorCode(), GooglePlayServicesActivity.this, 0, new OnCancelListener() {
                                        @Override
                                        public void onCancel(DialogInterface dialog) {
                                            retryFitnessConnect();
                                        }
                                    }).show();
                            return;
                        }
                        if (mFitnessIsInRes){
                            return;
                        }

                        try {
                            result.startResolutionForResult(GooglePlayServicesActivity.this, REQUEST_FITNESS_RES);
                        } catch (SendIntentException e) {
                            Log.e(TAG, "Exception while starting resolution activity", e);
                            retryFitnessConnect();
                        }
                    }
                })
                .build();
    }
    public void retryFitnessConnect(){
        mFitnessIsInRes = false;
        if (!mFitnessClient.isConnecting()) {
            mFitnessClient.connect();
        }
    }
}
