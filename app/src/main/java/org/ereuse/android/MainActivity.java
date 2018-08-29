package org.ereuse.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Main App activity that asks the user to select a Devicehub.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("Select your DeviceHub");
        createListView();
    }

    /**
     * Populates serverView with the Devicehubs.
     */
    private void createListView() {
        // From https://www.androidbegin.com/tutorial/android-simple-listview-tutorial/
        final String[] serversName = new String[]{
                "DeviceTag.io Europe",
                "DeviceTag.io United States",
                "eReuse.net"
        };

        final String[] serversUrl = new String[]{
                "https://devicetag.io/app",
                "https://us.devicetag.io/app",
                "http://devicehub-client.ereuse.net"
        };

        ListView serversView = findViewById(R.id.serversView);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, serversName);
        serversView.setAdapter(adapter);

        serversView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(MainActivity.this, Devicehub.class);
                i.putExtra(Devicehub.URL, serversUrl[position]);
                startActivity(i);
            }
        });
    }

}
