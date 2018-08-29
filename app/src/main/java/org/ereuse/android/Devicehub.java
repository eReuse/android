package org.ereuse.android;

import android.Manifest;
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
     * The id of the field where to write the Serial Number of the nfc.
     * <p>
     * If null, the app does not write to any html field and warn the user.
     */
    String idForNfc;
    NfcAdapter nfcAdapter;

    String idForBarcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_devicehub);
        Intent i = getIntent();
        String url = i.getStringExtra(URL);
        loadWebview(url);

        idForNfc = "foo";
        idForBarcode = "bar";

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
        try {
            nfcAdapter.disableForegroundDispatch(this);
        } catch (UnsupportedOperationException ignored) {
        } // nfc not enabled
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // We have a new NFC; get the ID and write it in an HTML, if any
        if (intent.hasExtra((NfcAdapter.EXTRA_TAG))) {
            String tagId = new String(Hex.encodeHex(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)));
            Log.d(TAG, "NFC tag read: " + tagId);
            if (idForNfc == null) {
                Toast.makeText(this,
                        "Be in a form before reading a Tag.",
                        Toast.LENGTH_SHORT).show();
            } else {
                fillHtmlField(idForNfc, tagId);
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
        if (scanResult != null) fillHtmlField(idForBarcode, scanResult.getContents());
    }

    /**
     * Writes the passed-in `value` to the HTML `fieldId`.
     */
    protected void fillHtmlField(String fieldId, String value) {
        String message = "Setting " + value + " to " + fieldId;
        Log.i(TAG, "fillHtmlField: " + message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        String js = "javascript:(function(){" +
                "var input = $('#" + fieldId + "');" +
                "input.val('" + value + "');" +
                "input.trigger('change');" +
                "})()";
        webview.loadUrl(js);
    }

    // WEBVIEW
    // -------

    /**
     * Loads the passed-in `url` in the webview, allowing self-signed DeviceTag.io certificates
     * and exposing a Javascript interface that the web can use to get barcodes and nfc ids.
     */
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
     * This app's interface to Javascript. You can call the methods of this class
     * within Javascript the following way: `AndroidApp.scanBarcode('foo')`.
     */
    public class WebviewJavascriptInterface {

        /**
         * Open the camera of the smartphone and read a barcode, storing the result in
         * the html tag with the passed-in html id.
         */
        @JavascriptInterface
        public void scanBarcode(String id) {
            idForBarcode = id;
            getBarcode();
        }

        /**
         * Save the id of any incoming NFC tag to the passed-in html field id.
         * <p>
         * This will keep overriding the html field in with IDs from NFCs until
         * `stopAcceptingNfc()` is called.
         */
        @JavascriptInterface
        public void acceptNfc(String id) {
            idForNfc = id;
        }

        /**
         * Stop accepting incoming NFC tags.
         * <p>
         * After this method is called Android won't fill any html tag with new
         * incoming NFCs. Instead, it will inform the user to be back on a form before
         * reading new tags.
         */
        @JavascriptInterface
        public void stopAcceptingNfc() {
            idForNfc = null;
        }
    }
}
