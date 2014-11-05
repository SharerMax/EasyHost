package sharermax.net.easyhosts;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


public class MainActivity extends Activity {

    private static final String HOSTS_PATH = "/system/etc/hosts\n";
    private static final String HOSTS_VERSION = "http://easyhosts.qiniudn.com/hostsversion.json";
    private static final String HOSTS_SOURCE = "http://easyhosts.qiniudn.com/hosts";
    private static final String PREFERENCE_CURRENT = "current_version";
    private static final String PREFERENCE_LAST = "last_version";
    private Button mUpdateButton;
    private Button mApplyButton;
    private Button mOriginalButton;
    private TextView mCurrentTextView;
    private TextView mLastTextView;
    private String mCurrentVersion;
    private String mLastVersion;
    private SharedPreferences mSharedPreferences;
    private Boolean mIsCheckUpdataed;



    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mIsCheckUpdataed = false;

        mSharedPreferences = getSharedPreferences("version", Context.MODE_PRIVATE);
        mCurrentVersion = mSharedPreferences.getString(PREFERENCE_CURRENT, getString(R.string.unknown_hosts));
        mLastVersion = mSharedPreferences.getString(PREFERENCE_LAST, getString(R.string.unknown_hosts));
        mCurrentTextView = (TextView)findViewById(R.id.current_textview);
        mLastTextView = (TextView)findViewById(R.id.last_textview);
        mUpdateButton = (Button)findViewById(R.id.update_button);
        mApplyButton = (Button)findViewById(R.id.apply_button);
        mOriginalButton = (Button)findViewById(R.id.original_button);

        mCurrentTextView.setText(mCurrentVersion);
        mLastTextView.setText(mLastVersion);

        if (mIsCheckUpdataed) {
            mApplyButton.setEnabled(true);
        } else {
            mApplyButton.setEnabled(false);
        }
        //check hosts version on server
        mUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    URL url = new URL(HOSTS_VERSION);
                    CheckUpdateTask checkUpdateTask = new CheckUpdateTask();
                    checkUpdateTask.execute(url);

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "不能解析网址", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //apply service hosts
        mApplyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    URL url = new URL(HOSTS_SOURCE);
                    ServerDataTask serverDataTask = new ServerDataTask();
                    serverDataTask.execute(url);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "不能解析网址", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //restore original hosts
        mOriginalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeHosts("echo 127.0.0.1\t\t localhost");
                Toast.makeText(MainActivity.this, "Hosts恢复成功", Toast.LENGTH_SHORT).show();
                mSharedPreferences.edit().putString(PREFERENCE_CURRENT, getString(R.string.original_hosts)).commit();
                mCurrentTextView.setText(getString(R.string.original_hosts));
            }
        });
    }

    /**
     * @param command : execute command with root permission
     */
    private void changeHosts (String command) {
        Process process = null;
        DataOutputStream processos = null;
        try {

            process = new ProcessBuilder().command("su").redirectErrorStream(true).start();
            processos = new DataOutputStream(process.getOutputStream());
            processos.writeBytes("mount -o remount,rw /system\n");
            processos.writeBytes("chmod 777 " + HOSTS_PATH);
            processos.writeBytes(command + " > " + HOSTS_PATH);
            processos.writeBytes("mount -o remount,ro /system\n");
            processos.writeBytes("exit\n");

            processos.flush();
            process.waitFor();
            processos.close();

        } catch (Exception e) {

        } finally {
            if (processos != null) {
                try {
                    processos.close();
                } catch (Exception e) {

                }
                process.destroy();
            }
        }

    }

    private class CheckUpdateTask extends AsyncTask<URL, Void, Object> {
        @Override
        protected void onPreExecute() {
            Toast.makeText(MainActivity.this, "正在检查服务器Hosts版本...", Toast.LENGTH_SHORT).show();
        }
        @Override
        protected Object doInBackground(URL... urls) {
            return getServiceData(urls[0], false);
        }
        @Override
        protected void onPostExecute(Object result) {
            String versioninfo = (String)result;
            if (versioninfo == null || versioninfo.isEmpty()) {
                Toast.makeText(MainActivity.this, "检查服务器Hosts版本失败！！！", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject jsonObject = new JSONObject(versioninfo);
                mLastVersion = jsonObject.getString("version_name");
                mLastTextView.setText(mLastVersion);
                mSharedPreferences.edit().putString(PREFERENCE_LAST,mLastVersion).commit();
                mIsCheckUpdataed = true;
                mApplyButton.setEnabled(true);


            } catch (JSONException e){
                e.printStackTrace();
            }

        }

    }

    private class ServerDataTask extends AsyncTask<URL, Void, Object> {
        @Override
        protected void onPreExecute() {
            Toast.makeText(MainActivity.this, "正在获取Hosts...", Toast.LENGTH_SHORT).show();
        }
        @Override
        protected Object doInBackground(URL... urls) {

            return getServiceData(urls[0], true);
        }
        @Override
        protected void onPostExecute(Object result) {
            changeHosts("cat " + MainActivity.this.getFilesDir().getPath() + "/hosts");
            Toast.makeText(MainActivity.this, "Hosts 修改成功", Toast.LENGTH_SHORT).show();
            mSharedPreferences.edit().putString(PREFERENCE_CURRENT, mLastVersion).commit();
            mCurrentTextView.setText(mLastVersion);
        }

    }

    private Object getServiceData(URL url, Boolean isHosts) {
        HttpURLConnection httpURLConnection = null;
        BufferedReader bufferedReader = null;
        try {
            httpURLConnection = (HttpURLConnection)url.openConnection();
            httpURLConnection.setConnectTimeout(3000);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setRequestMethod("GET");

            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = httpURLConnection.getInputStream();
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String verInfo = "";
                String lineString = null;

                if (isHosts) {
                    File filePath = this.getFilesDir();
                    File hosts = new File(filePath, "hosts");
                    FileOutputStream hostsOutputStream = new FileOutputStream(hosts);
                    DataOutputStream dataOutputStream = new DataOutputStream(hostsOutputStream);
                    while ((lineString = bufferedReader.readLine()) != null) {

                        dataOutputStream.writeBytes(lineString + "\n");
                    }
                    return true;

                } else {
                    while ((lineString = bufferedReader.readLine()) != null) {
                        verInfo += lineString;
                    }
                    return verInfo;
                }

            } else {

                return null;
            }
        }catch (IOException e) {
            e.printStackTrace();
        }finally {
            httpURLConnection.disconnect();
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }
}
