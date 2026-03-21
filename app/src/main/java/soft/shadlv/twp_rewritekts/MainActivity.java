package soft.shadlv.twp_rewritekts;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.Intent;
import android.os.Build;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private EditText port_et;
    private EditText host_et;
    private EditText dcip_et;
    private Button proxy_switch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        port_et = findViewById(R.id.port);
        host_et = findViewById(R.id.host);
        dcip_et = findViewById(R.id.dcip);
        proxy_switch = findViewById(R.id.toggleButton);
        proxy_switch.setOnClickListener(this);
        Button configSave = findViewById(R.id.save_button);
        configSave.setOnClickListener(this);
        Button configLoad = findViewById(R.id.load_button);
        configLoad.setOnClickListener(this);
    }

    private void load_conf() {
        String filename = "config.conf";
        StringBuilder stringBuilder = new StringBuilder();

        try (FileInputStream fis = openFileInput(filename); Scanner scanner = new Scanner(fis)) {
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine());
            }
        } catch (IOException e) {
            Log.v("idk","file not found");
        }

        String loadedContents = stringBuilder.toString();
        Log.d("idk", loadedContents);
        for (String line: loadedContents.split("&")) {
            switch (line.split("=")[0]){
                case "host":
                    host_et.setText(line.split("=")[1]);
                    break;
                case "port":
                    port_et.setText(line.split("=")[1]);
                    break;
                case "dcip":
                    dcip_et.setText(line.split("=")[1].replace(';', '\n'));
                    break;
                default:
                    Log.e("idk", "config variable not found");
                    break;
            }
        }
    }

    private void save_conf() {
        String filename = "config.conf";
        String fileContents = String.format("host=%s&port=%s&dcip=%s", getUHost(), getUPort(), getUDCIP().replace('\n', ';'));
        try {
            FileOutputStream fos = openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write(fileContents.getBytes());
            fos.close();
        } catch (IOException e) {
            Log.e("idk", "some io error idfk");
        }
    }

    private int getUPort() {
        String userInput = port_et.getText().toString();
        return Integer.parseInt(userInput);
    }

    private String getUHost(){
        return host_et.getText().toString();
    }

    private String getUDCIP(){
        return dcip_et.getText().toString();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.toggleButton) {
            Intent intent = new Intent(this, ProxyService.class);
            intent.putExtra("host", getUHost());
            intent.putExtra("port", getUPort());
            intent.putExtra("dcip", getUDCIP());
            if (proxy_switch.getText().toString().equals(getString(R.string.proxy_off)))
            {
                proxy_switch.setText(R.string.proxy_on);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }
            else
            {
                proxy_switch.setText(R.string.proxy_off);
                stopService(intent);
            }
        }
        else if (v.getId() == R.id.save_button) {
            save_conf();
        }
        else if (v.getId() == R.id.load_button) {
            load_conf();
        }
    }
}
