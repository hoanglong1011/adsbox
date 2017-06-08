package com.example.viethq.iav.Services;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.viethq.iav.Global;
import com.example.viethq.iav.Model.Singer;
import com.example.viethq.iav.Model.SingerSong;
import com.example.viethq.iav.Model.Song;
import com.example.viethq.iav.Model.Video;
import com.example.viethq.iav.VideoPlayer;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class SyncService extends Service {

    private final String TAG = SyncService.class.getSimpleName();
    private final String CONNECT = "client connect";
    private final String DOWNLOAD = "download";
    private final String SYNC_SONG = "sync song";
    private final String SYNC_SONG_INFO = "sync song info";
    private final String SYNC_SINGER_INFO = "sync singer info";
    private final String SERVER_REQUEST = "server request";
    private final String CLIENT_RESPONSE_CHECKING = "client response";
    private final String CLIENT_PROCESSING = "client processing";
    private final String CLIENT_FILE_CURRENT = "client file current";
    private final String CLIENT_RESPONSE_FILE_CURRENT = "client response file current";
    private final String CLIENT_SYNC_SONG_PROCESSING = "client song processing";

    private final String SERVER = "http://118.69.55.218:9093";

    private String HOST = "192.168.100.5"; //"icool.somee.com";
    private String FTP_URL = ""; //"/www.icool.somee.com/videos";
    private String FTP_USER = "quangcao"; //"anonymous";
    private String FTP_PASSWORD = "quangcao@123321"; //"";

    private String TOKEN_SONG = "";
    private String TOKEN_ADS = "";

    private String version = "";

    private Timer timer;
    private TimerTask timerTask;
    private Handler handler;

    public SyncService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        SharedPreferences sharedPreferences = getSharedPreferences(Global.SHAREREFERENCE, MODE_PRIVATE);

        Global.coso = Integer.parseInt(sharedPreferences.getString(Global.BRANCH, "0"));
        Global.phong = sharedPreferences.getString(Global.ROOM, "");
        version = Global.getVersionName(this);

        try {
            Global.socket = IO.socket(SERVER);

            Global.socket.connect();

            Global.socket.emit(CONNECT, Global.coso, Global.phong, version);

            Global.socket.on("connect", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Global.socket.emit(CONNECT, Global.coso, Global.phong, version);
                }
            });

            media();
        }
        catch (Exception ex){ ex.printStackTrace();}

        return START_STICKY;
    }

    private void media(){
        Global.socket.on(DOWNLOAD, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    Log.i(TAG, "Download is running!");
                    JSONObject data = (JSONObject) args[0];

                    String source = data.getString("source"); // e.g File's name in FTP server
                    String target = data.getString("target"); // e.g The local path in Android operation
                    int id = Integer.parseInt(data.getString("id"));
                    String name = data.getString("name");
                    String begin = data.getString("begin");
                    String end = data.getString("end");
                    int from = Integer.parseInt(data.getString("from"));
                    int to = Integer.parseInt(data.getString("to"));
                    String days = data.getString("days");
                    int cs = Integer.parseInt(data.getString("coso"));
                    String p = data.getString("phong");
                    String size = data.getString("size");
                    String token = data.getString("token");

                    HOST = data.getString("host");
                    FTP_URL = data.getString("path");
                    FTP_USER = data.getString("username");
                    FTP_PASSWORD = data.getString("password");

                    if(Global.coso == cs && Global.phong.equals(p) && TOKEN_ADS != token){
                        // Android Box
                        String path = Global.convertDirecotyToSystemDirectory(target);
                        TOKEN_ADS = token;

                        new DownloadMediaFileFromServer().execute(new String[]{source, target, size,
                                String.valueOf(id), name, begin, end, String.valueOf(from), String.valueOf(to),
                                days, path});
                    }
                }
                catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });

        Global.socket.on(SERVER_REQUEST, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    Global.socket.emit(CONNECT, Global.coso, Global.phong, version);
                }
                catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });
    }

    public class  DownloadMediaFileFromServer extends AsyncTask<String, Void, Boolean>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(String... params) {

            Boolean status = false;

            //region e.g Check whether it's Internet connected
            if (Global.isInternetConnected(SyncService.this)){
                String source = params[0];
                String target = params[1];

                target = Global.convertDirecotyToSystemDirectory(target);
                File f = new File(target);

                if(!f.getParentFile().isDirectory())
                    f.getParentFile().mkdirs();

                FTPClient client = new FTPClient();
                OutputStream outputStream = null;

                int count;

                //region try ... catch ... finally
                try {
                    client.connect(HOST, 21);
                    client.setFileType(FTP.BINARY_FILE_TYPE);
                    // use local passive mode to pass firewall
                    client.enterLocalPassiveMode();
                    int reply = client.getReplyCode();

                    if(!FTPReply.isPositiveCompletion(reply)){
                        Log.i(TAG, "Error: " + client.getReplyString());
                        Global.socket.emit("client failed", Global.coso, Global.phong, client.getReplyString());
                    }
                    else{
                        Boolean success = client.login(FTP_USER, FTP_PASSWORD);
                        if(success){
                            //region Copy file
                            outputStream = new FileOutputStream(target);

                            // Fetch file from server
                            InputStream inputStream = client.retrieveFileStream(FTP_URL + "/" + source);

                            Double len = Double.parseDouble(params[2]);

                            byte[] data = new byte[2 * 1024 * 1024];

                            long s = 0;
                            while ((count = inputStream.read(data)) != -1){
                                //region Write data to file
                                s += count;
                                outputStream.write(data, 0, count);

                                Double percent = ((s / len) * 100.0);
                                Global.socket.emit(CLIENT_PROCESSING, Global.coso, Global.phong,
                                        String.valueOf(percent));
                                //endregion
                            }
                            //endregion

                            //region Insert video into MediaStore
                            // Kiểm tra dung lượng File sau khi Download
                            File from = new File(target);
                            long d = from.length();
                            if(len != d){
                                Global.socket.emit("client failed", Global.coso, Global.phong,
                                        "The downloading is not completely!");
                            }
                            else {
                                //region Insert video into MediaStore
                                SaveMedia(Integer.parseInt(params[3]), params[4], params[5], params[6],
                                        Integer.parseInt(params[7]), Integer.parseInt(params[8]), params[9], target);
                                //endregion
                            }
                            //endregion

                            inputStream.close();
                        }
                        else {
                            Global.socket.emit("client failed", Global.coso, Global.phong,
                                    "The FTP account is not valid!");
                        }
                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                    Global.socket.emit("client failed", Global.coso, Global.phong, e.getMessage());
                }
                finally {
                    //region finally
                    try {
                        if(outputStream != null){
                            // flushing output
                            outputStream.flush();
                            outputStream.close();
                        }

                        client.disconnect();
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                    //endregion
                }
                //endregion
            }
            else {
                Global.socket.emit("client failed", Global.coso, Global.phong,
                        "Not found the Internet connection!");
            }
            //endregion

            return status;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
        }
    }
}