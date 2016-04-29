package com.firebase.ui.auth.api;

import android.app.ProgressDialog;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.firebase.ui.auth.ui.credentials.CredentialsBaseActivity;

/**
 * Created by serikb on 4/22/16.
 */
public class CredentialsAPI implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final int RC_CREDENTIALS_READ = 2;
    private static final String TAG = "CredentialsAPI";

    private GoogleApiClient mGoogleApiClient;
    private boolean mAutoSignInAvailable;
    private boolean mSignInResolutionNeeded;
    private CredentialsBaseActivity mActivity;
    private CredentialRequestResult mCredentialRequestResult;
    private ProgressDialog mProgressDialog;
    private Credential mCredential;

    public CredentialsAPI(CredentialsBaseActivity activity) {
        mAutoSignInAvailable = false;
        mSignInResolutionNeeded = false;
        mActivity = activity;

        initGoogleApiClient(null);
        requestCredentials(true /* shouldResolve */, false /* onlyPasswords */);
    }

    // TODO: (serikb) find the way to check if Credentials is available on top of GMSCore
    public boolean isCredentialsAvailable() {
        return true;
    }

    public boolean isAutoSignInAvailable() {
        return mAutoSignInAvailable;
    }

    public boolean isSignInResolutionNeeded() {
        return mSignInResolutionNeeded;
    }

    public void resolveSignIn() {
        mSignInResolutionNeeded = false;
    }

    public void resolveSavedEmails(CredentialsBaseActivity activity) {
        if (mCredentialRequestResult == null || mCredentialRequestResult.getStatus() == null) {
            return;
        }
        Status status = mCredentialRequestResult.getStatus();
        if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED) {
            try {
                status.startResolutionForResult(activity, RC_CREDENTIALS_READ);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "Failed to send Credentials intent.", e);
            }
        }
    }

    public boolean isGMSCoreAvailable() {
        return GoogleApiAvailability
                .getInstance()
                .isGooglePlayServicesAvailable(mActivity.getApplicationContext()) ==
                ConnectionResult.SUCCESS;
    }

    public String getEmailFromCredential() {
        if (mCredential == null) {
            return null;
        }
        return mCredential.getId();
    }

    public String getAccountTypeFromCredential() {
        if (mCredential == null) {
            return null;
        }
        return mCredential.getAccountType();
    }

    public String getPasswordFromCredential() {
        if (mCredential == null) {
            return null;
        }
        return mCredential.getPassword();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    private void initGoogleApiClient(String accountName) {
        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        if (accountName != null) {
            gsoBuilder.setAccountName(accountName);
        }

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(mActivity)
                .addConnectionCallbacks(this)
                .addApi(Auth.CREDENTIALS_API)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gsoBuilder.build());

        mGoogleApiClient = builder.build();
    }

    public void googleSilentSignIn() {
        // Try silent sign-in with Google Sign In API
        OptionalPendingResult<GoogleSignInResult> opr =
                Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (opr.isDone()) {
            GoogleSignInResult gsr = opr.get();
        } else {
            showProgress();
            opr.setResultCallback(
                    new ResultCallback<GoogleSignInResult>() {
                        @Override
                        public void onResult(GoogleSignInResult googleSignInResult) {
                            hideProgress();
                            mActivity.asyncTasksDone();
                        }
                    });
        }
    }

    public void handleCredential(Credential credential) {
        mCredential = credential;

        if (IdentityProviders.GOOGLE.equals(credential.getAccountType())) {
            // Google account, rebuild GoogleApiClient to set account name and then try
            initGoogleApiClient(credential.getId());
            googleSilentSignIn();
        } else {
            // Email/password account
            String status = String.format("Signed in as %s", credential.getId());
            Log.e(TAG, status);
        }
    }

    public void requestCredentials(final boolean shouldResolve, boolean onlyPasswords) {
        CredentialRequest.Builder crBuilder = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true);

        if (!onlyPasswords) {
            crBuilder.setAccountTypes(IdentityProviders.GOOGLE);
        }

        showProgress();
        Auth.CredentialsApi.request(mGoogleApiClient, crBuilder.build())
                .setResultCallback(
                        new ResultCallback<CredentialRequestResult>() {
                            @Override
                            public void onResult(CredentialRequestResult credentialRequestResult) {
                                mCredentialRequestResult = credentialRequestResult;
                                Status status = credentialRequestResult.getStatus();

                                if (status.isSuccess()) {
                                    // Auto sign-in success
                                    mAutoSignInAvailable = true;
                                    handleCredential(credentialRequestResult.getCredential());
                                } else if (status.getStatusCode() ==
                                        CommonStatusCodes.RESOLUTION_REQUIRED && shouldResolve) {
                                    mSignInResolutionNeeded = true;
                                    // Getting credential needs to show some UI, start resolution
                                }
                                hideProgress();
                                mActivity.asyncTasksDone();
                            }
                        });
    }

    private void showProgress() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage(mActivity.getString(com.firebase.ui.auth.R.string.loading_text));
        }

        mProgressDialog.show();
    }

    private void hideProgress() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(mActivity, "An error has occurred.", Toast.LENGTH_SHORT).show();
    }

    public boolean isGoogleApiClient() {
        return mGoogleApiClient != null;
    }

    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }
}
