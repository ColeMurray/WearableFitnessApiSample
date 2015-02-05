package com.murraycole.wearcommunication;

import android.app.Activity;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
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

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
    private final String LOG_TAG = MainActivity.class.getSimpleName();

    //API Variables
    private GoogleApiClient mClient;
    private GoogleApiClient mFitnessClient;
    private boolean mResolvingError = false;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    public static final String START_ACTIVITY_PATH = "/start/MainActivity";
    public static final String CONNECT_FITNESS = "/connect/fitness";
    //EndAPI

    private TextView mTextView;
    private Button mSendMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addApi(Wearable.API)
                .addOnConnectionFailedListener(this)
                .build();





        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mSendMsg = (Button) stub.findViewById(R.id.fitnessID);
                mSendMsg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new ConnectFitnessTask().execute();
                    }
                });
            }
        });
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "Connected");
        //Can now use WearableAPI

        //setupMessageListeners
        Wearable.MessageApi.addListener(mClient,this);

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG,"Connection suspended");

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d (LOG_TAG, "Connect Failed");

        if (mResolvingError){
            //currently resolving an error
            return;
        }
        else if (result.hasResolution()){
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            }
            catch (IntentSender.SendIntentException e){
                //Error with resolution intent. Try again
                mClient.connect();
            }
        }
        else{
            //no resolution
            //display Error dialog
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            this, REQUEST_RESOLVE_ERROR);
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError){
            mClient.connect();
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(mClient,this);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(START_ACTIVITY_PATH)){
            updateText();
            mFitnessClient = new GoogleApiClient.Builder(this)
                    .addApi(Fitness.API)
                    .addScope(Fitness.SCOPE_ACTIVITY_READ)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.d(LOG_TAG, "Connected to fitness API");
                        }

                        @Override
                        public void onConnectionSuspended(int i) {

                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.d(LOG_TAG, "Connection failed: " + connectionResult.getErrorCode());
                        }
                    })
                    .build();
            mFitnessClient.connect();
        }
    }
    private void updateText(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Msg received", Toast.LENGTH_SHORT).show();
            }
        });
    }
    //Send Msgs
    private void sendHandheldFitnessPrompt (String nodeId){
        Wearable.MessageApi.sendMessage(mClient,nodeId,CONNECT_FITNESS,new byte[0]).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()){
                            Log.e(LOG_TAG, "Failed to send msg, status code: " +
                                sendMessageResult.getStatus().getStatusCode() );
                        }
                    }
                }
        );
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mClient).await();
        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }
        return results;
    }
    // Send Prompt to Handheld to start FitnessAPI
    private class ConnectFitnessTask extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... params) {
            Collection<String> nodes = getNodes();
            for (String n : nodes){
                sendHandheldFitnessPrompt(n);
            }
            return null;
        }
    }

}
