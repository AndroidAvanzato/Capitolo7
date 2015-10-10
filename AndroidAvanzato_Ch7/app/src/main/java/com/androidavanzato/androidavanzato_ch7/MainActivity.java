/**
 *
 */
package com.androidavanzato.androidavanzato_ch7;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

/**
 *
 */
public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = MainActivity.class.getName();

    private final String RECEIVER_APPLICATION_ID = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
    private final int VOLUME_INCREMENT = 1;

    private MediaRouter mMediaRouter;
    private CastDevice mSelectedDevice;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.RouteInfo mSelectedRouteInfo;
    private MyMediaRouterCallback mMyMediaRouterCallback = new MyMediaRouterCallback();

    private GoogleApiClient mApiClient;
    private boolean mApplicationStarted;
    private boolean mWaitingForReconnect;

    private MyCustomChannel mMyCustomChannel;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private String mSessionId;

    Cast.Listener mCastClientListener = new Cast.Listener() {

        @Override
        public void onApplicationStatusChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onApplicationStatusChanged: "
                        + Cast.CastApi.getApplicationStatus(mApiClient));
            }
        }

        @Override
        public void onVolumeChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
            }
        }

        @Override
        public void onApplicationDisconnected(int errorCode) {
            teardown();
        }
    };

    /**
     *
     */
    private void connectApiClient() {
        Cast.CastOptions apiOptions = Cast.CastOptions.builder(mSelectedDevice,
                mCastClientListener)
                .setVerboseLoggingEnabled(true)
                .build();

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptions)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mApiClient.connect();
    }

    /**
     *
     */
    private void disconnectApiClient() {
        mApiClient.disconnect();
    }

    /**
     *
     * @param view
     */
    public void doLeaveReceiver(View view) {
        leaveApplicationReceiver();
    }

    /**
     *
     * @param view
     */
    public void doLaunchReceiver(View view) {
        if(mApiClient == null || !mApiClient.isConnected()) {
            connectApiClient();
        } else {
            launchApplicationReceiver();
        }
    }

    /**
     *
     * @param view
     */
    public void doPlay(View view) {
        loadAndPlayMediaContent();
    }

    /**
     *
     * @param view
     */
    public void doPause(View view) {
        pauseMediaContent();
    }

    /**
     *
     */
    private void launchApplicationReceiver() {
        Cast.CastApi.launchApplication(mApiClient, RECEIVER_APPLICATION_ID, false)
                .setResultCallback(
                        new ResultCallback<Cast.ApplicationConnectionResult>() {
                            @Override
                            public void onResult(Cast.ApplicationConnectionResult result) {
                                Status status = result.getStatus();
                                if (status.isSuccess()) {
                                    ApplicationMetadata applicationMetadata = result.getApplicationMetadata();
                                    mSessionId = result.getSessionId();
                                    String applicationStatus = result.getApplicationStatus();
                                    boolean wasLaunched = result.getWasLaunched();

                                    mApplicationStarted = true;

                                    findViewById(R.id.bt_halt_receiver).setEnabled(true);
                                    findViewById(R.id.bt_start_receiver).setEnabled(false);

                                    setupChannels();
                                }
                            }
                        });
    }

    /**
     *
     */
    private void leaveApplicationReceiver() {
        Cast.CastApi.leaveApplication(mApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status result) {
                Status status = result.getStatus();
                if (status.isSuccess()) {
                    mApplicationStarted = false;

                    findViewById(R.id.bt_halt_receiver).setEnabled(false);
                    findViewById(R.id.bt_start_receiver).setEnabled(true);
                    findViewById(R.id.bt_play).setEnabled(false);
                    findViewById(R.id.bt_pause).setEnabled(false);
                }
            }
        });
    }

    /**
     *
     */
    private void loadAndPlayMediaContent() {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, "My video");
        MediaInfo mediaInfo = new MediaInfo.Builder(
                "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4")
                .setContentType("video/mp4")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
        try {
            mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
                    .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                            if (result.getStatus().isSuccess()) {
                                Log.d(TAG, "Media loaded successfully");
                                findViewById(R.id.bt_play).setEnabled(false);
                                findViewById(R.id.bt_pause).setEnabled(true);
                            }
                        }
                    });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Problem occurred with media during loading", e);
        } catch (Exception e) {
            Log.e(TAG, "Problem opening media during loading", e);
        }
    }

    /**
     *
     * @param connectionHint
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        if (mWaitingForReconnect) {
            mWaitingForReconnect = false;
            reconnectChannels();
        } else {
            findViewById(R.id.bt_start_receiver).setEnabled(true);
        }
    }

    /**
     *
     * @param cause
     */
    @Override
    public void onConnectionSuspended(int cause) {
        mWaitingForReconnect = true;
        findViewById(R.id.bt_start_receiver).setEnabled(false);
        findViewById(R.id.bt_halt_receiver).setEnabled(false);
        findViewById(R.id.bt_pause).setEnabled(false);
        findViewById(R.id.bt_play).setEnabled(false);
    }

    /**
     *
     * @param result
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
    }

    /**
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(RECEIVER_APPLICATION_ID))
                .build();

        mRemoteMediaPlayer = new RemoteMediaPlayer();
        mRemoteMediaPlayer.setOnStatusUpdatedListener(
                new RemoteMediaPlayer.OnStatusUpdatedListener() {
                    @Override
                    public void onStatusUpdated() {
                        MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
                        if(mediaStatus != null) {
                            boolean isPlaying = mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
                        }
                    }
                });

        mRemoteMediaPlayer.setOnMetadataUpdatedListener(
                new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                    @Override
                    public void onMetadataUpdated() {
                        MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();
                        if (mediaInfo != null) {
                            MediaMetadata metadata = mediaInfo.getMetadata();
                        }
                    }
                });
    }

    /**
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);

        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);

        return true;
    }

    /**
     *
     * @param event
     * @return
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume < 1.0) {
                            try {
                                Cast.CastApi.setVolume(mApiClient, Math.min(currentVolume + VOLUME_INCREMENT, 1.0));
                            } catch (Exception e) {
                                Log.e(TAG, "unable to set volume", e);
                            }
                        }
                    } else {
                        Log.e(TAG, "dispatchKeyEvent - volume up");
                    }
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume > 0.0) {
                            try {
                                Cast.CastApi.setVolume(mApiClient, Math.max(currentVolume - VOLUME_INCREMENT, 0.0));
                            } catch (Exception e) {
                                Log.e(TAG, "unable to set volume", e);
                            }
                        }
                    } else {
                        Log.e(TAG, "dispatchKeyEvent - volume down");
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    /**
     *
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        teardown();

        mMediaRouter = null;
        mMediaRouteSelector = null;
        mMyMediaRouterCallback = null;
    }

    /**
     *
     */
    @Override
    protected void onStart() {
        super.onStart();
        mMediaRouter.addCallback(mMediaRouteSelector, mMyMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    /**
     *
     */
    @Override
    protected void onStop() {
        mMediaRouter.removeCallback(mMyMediaRouterCallback);
        super.onStop();
    }

    /**
     *
     */
    private void pauseMediaContent() {
        mRemoteMediaPlayer.pause(mApiClient).setResultCallback(
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        Status status = result.getStatus();
                        if (!status.isSuccess()) {
                            Log.w(TAG, "Unable to toggle pause: " + status.getStatusCode());
                        } else {
                            findViewById(R.id.bt_play).setEnabled(true);
                            findViewById(R.id.bt_pause).setEnabled(false);
                        }
                    }
                });
    }

    /**
     *
     */
    private void reconnectChannels() {

    }

    /**
     *
     * @param message
     */
    private void sendMessage(String message) {
        if (mApiClient != null && mMyCustomChannel != null) {
            try {
                Cast.CastApi.sendMessage(mApiClient, mMyCustomChannel.getNamespace(), message)
                        .setResultCallback(
                                new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(Status result) {
                                        if (!result.isSuccess()) {
                                            Log.e(TAG, "Sending message failed");
                                        }
                                    }
                                });
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
            }
        }
    }

    /**
     *
     */
    private void setupChannels() {
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating media channel", e);
        }

        mRemoteMediaPlayer.requestStatus(mApiClient)
                .setResultCallback(
                        new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    Log.e(TAG, "Failed to request status.");
                                } else {
                                    findViewById(R.id.bt_play).setEnabled(true);
                                }
                            }
                        });

        mMyCustomChannel = new MyCustomChannel();
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
                    mMyCustomChannel.getNamespace(),
                    mMyCustomChannel);

        } catch (IOException e) {
            Log.e(TAG, "Exception while creating channel", e);
        }
    }

    /**
     *
     */
    private void teardown() {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    try {
                        Cast.CastApi.stopApplication(mApiClient, mSessionId);
                        if (mMyCustomChannel != null) {
                            Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, mMyCustomChannel.getNamespace());
                            mMyCustomChannel = null;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while removing channel", e);
                    }
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;
    }

    /**
     *
     */
    private class MyCustomChannel implements Cast.MessageReceivedCallback {

        /**
         *
         * @return
         */
        public String getNamespace() {
            return "urn:x-cast:com.channel.custom";
        }

        /**
         *
         * @param castDevice
         * @param namespace
         * @param message
         */
        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            Log.d(TAG, "onMessageReceived: " + message);
        }
    }

    /**
     *
     */
    private class MyMediaRouterCallback extends MediaRouter.Callback {

        /**
         *
         * @param router
         * @param info
         */
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());
            mSelectedRouteInfo = info;
            connectApiClient();
        }

        /**
         *
         * @param router
         * @param info
         */
        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            mSelectedDevice = null;
            mSelectedRouteInfo = null;
            disconnectApiClient();
        }
    }
}
