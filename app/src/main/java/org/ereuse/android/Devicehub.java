package org.ereuse.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.commons.codec.binary.Hex;

/**
 * Bridge activity between a Devicehub and the Android App.
 * <p>
 * This activity loads a passed-in url (expected to be a Devicehub) and bridges the NFC
 * and barcode Android capabilities to the website.
 * <p>
 * See inner class `WebviewJavascriptInterface` to know how to call the interface from JS.
 */
public class Devicehub extends AppCompatActivity {
    static final String URL = "url";
    private static final String TAG = "eReuse.org";
    WebView webview;
    /**
     * The name of the event where to broadcast the Serial Number of the nfc.
     * <p>
     * If null, the app does not broadcast.
     */
    String nfcEvent;
    NfcAdapter nfcAdapter;

    String barcodeEvent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_devicehub);
        Intent i = getIntent();
        String url = i.getStringExtra(URL);
        loadWebview(url);

        // Load and check nfc
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) Log.i(TAG, "No NFC on this device.");
        if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
            Toast.makeText(this,
                    "Enable NFC in settings if you want to read NFC tags.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // NFC
    // ---
    // The following methods are used to read NFCs
    @Override
    protected void onResume() {
        super.onResume();
        // Start listening for NFCs when the app is in the foreground
        if (nfcAdapter != null) { // Phone has NFC
            Intent intent = new Intent(this, getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndef.addDataType("*/*");
                ndef.addDataType("text/html");
            } catch (IntentFilter.MalformedMimeTypeException e) {
                e.printStackTrace();
            }
            IntentFilter[] intentFilters = new IntentFilter[]{};
            try {
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null);
            } catch (UnsupportedOperationException ignored) {
            } // nfc not enabled
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        // Stop listening for NFCs when the user leaves the app
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableForegroundDispatch(this);
            } catch (UnsupportedOperationException ignored) {
            } // nfc not enabled
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // We have a new NFC; get the ID and write it in an HTML, if any
        if (intent.hasExtra((NfcAdapter.EXTRA_TAG))) {
            String tagId = new String(Hex.encodeHex(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)));
            Log.d(TAG, "NFC tag read: " + tagId);
            if (nfcEvent == null) {
                Toast.makeText(this,
                        "Be in a form before reading a Tag.",
                        Toast.LENGTH_SHORT).show();
            } else {
                broadcast(nfcEvent, tagId);
            }
        }
    }

    // BARCODE
    // -------
    // The following methods are used to scan barcodes

    /**
     * Opens the camera to read a new barcode.
     * <p>
     * This method asks for permissions if the user did not grant them before, in which
     * case `onRequestPermissionsResult` will handle opening the camera.
     */
    public void getBarcode() {
        if (Build.VERSION.SDK_INT >= 23) {
            String PERM = Manifest.permission.CAMERA;
            int cameraPermissionCheck = ContextCompat.checkSelfPermission(this, PERM);

            if (cameraPermissionCheck != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{PERM}, 99);
                return;
            }
        }
        // Initiate scan
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.initiateScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] perms,
                                           @NonNull int[] grantResults) {
        // This method is invoked by Android after `getBarcode` requests for permission with the result
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Initiate scan
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.initiateScan();
        } else {
            Toast.makeText(this,
                    "Camera does not have permissions.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // this method is invoked after detecting a barcode with the camera
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null) broadcast(barcodeEvent, scanResult.getContents());
    }

    /**
     * Broadcasts an Angular event to the executing Angular from the $rootScope.
     *
     * @param event Event name.
     * @param value Value.
     */
    protected void broadcast(String event, String value) {
        // From https://stackoverflow.com/a/24596251
        String js = "" +
                "var $rScope = angular.element(document.body).scope().$root;" +
                "$rScope.$broadcast('" + event + "', '" + value + "');";
        webview.evaluateJavascript(js, null);
    }


    // WEBVIEW
    // -------

    /**
     * Loads the passed-in `url` in the webview, allowing self-signed DeviceTag.io certificates
     * and exposing a Javascript interface that the web can use to get barcodes and nfc ids.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void loadWebview(String url) {
        webview = this.findViewById(R.id.webview);
        webview.addJavascriptInterface(new WebviewJavascriptInterface(), "AndroidApp");
        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // access localStorage
        settings.setDatabaseEnabled(true); // access sessionStorage
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int e, String desc, String failingUrl) {
                // Shows a message in any not handled error
                webview.loadData(desc, "text/plain", "UTF-8");
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Allows self-signed certificates from DeviceTag.io to pass-in
                // this is required when connecting to a WorkbenchServer from the local network
                // from https://stackoverflow.com/a/35618839
                if (error.getPrimaryError() == SslError.SSL_UNTRUSTED) {
                    SslCertificate certificate = error.getCertificate();
                    SslCertificate.DName issuedBy = certificate.getIssuedBy();
                    if (issuedBy.getOName().equals("DeviceTag.io")
                            && issuedBy.getCName().equals("localhost")) {
                        handler.proceed();
                    } else {
                        handler.cancel();
                    }
                } else {
                    handler.cancel();
                }
            }
        });
        webview.loadUrl(url);
    }

    /**
     * This app's interface to Javascript. This interface is exposed to the Javascript by
     * adding a global object called `AndroidApp`, which you can access with `window.AndroidApp`.
     * You can call the methods the following way: `window.AndroidApp.scanBarcode('eventName');`,
     * and so on.
     */
    public class WebviewJavascriptInterface {

        /**
         * Open the camera of the smartphone and read a barcode, broadcasting an Angular
         * event with the value of the barcode.
         */
        @JavascriptInterface
        public void scanBarcode(String event) {
            barcodeEvent = event;
            getBarcode();
        }

        /**
         * Broadcast as the value of an Angular event the id of any new incoming NFC tag.
         * <p>
         * This will keep broadcasting with values until `stopNFC()` is called.
         */
        @JavascriptInterface
        public void startNFC(String id) {
            nfcEvent = id;
        }

        /**
         * Stop accepting incoming NFC tags.
         */
        @JavascriptInterface
        public void stopNFC() {
            nfcEvent = null;
        }
    }
}
