package eu.spyropoulos.anki.zeemote;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class ZeemoteSettings extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}
