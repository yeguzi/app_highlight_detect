package com.example.test;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_AUDIO_FILE = 1;
    private MediaPlayer mediaPlayer;
    private SeekBar seekBar;
    private Handler handler = new Handler();
    private Button  playButton;
    private TextView audioFileName, currentTimeAudio, timeAudio,button;
    private ImageView icon,thumbnailImageView,icon2,icon3;
    private Uri audioUri;
    private Activity activity;
    private PyObject model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = findViewById(R.id.pick_file);
        playButton = findViewById(R.id.playPauseButton);
        seekBar = findViewById(R.id.seekBar);
        audioFileName = findViewById(R.id.audioFileName);
        currentTimeAudio = findViewById(R.id.currentTime);
        timeAudio = findViewById(R.id.totalTime);
        icon = findViewById(R.id.icon);
        icon2 = findViewById(R.id.icon_2);
        icon3 = findViewById(R.id.icon_3);
        thumbnailImageView = findViewById(R.id.thumbnail);

        playButton.setBackgroundResource(R.drawable.baseline_play_arrow_24);

        // Thiết lập các listener cho các nút
        button.setOnClickListener(v -> openFileChooser());
        playButton.setOnClickListener(v -> togglePlay());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    currentTimeAudio.setText(formatTime(mediaPlayer.getCurrentPosition()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        activity = this;

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        loadModel();
    }
    private void updateIconPosition(int start,int id ) {
        int totalDurationInSeconds = mediaPlayer.getDuration()/ 1000;;
        float seekBarWidth = seekBar.getWidth();
        float ratio = (float) start / totalDurationInSeconds;
        float iconPosition = ratio * seekBarWidth;
        int[] seekBarPosition = new int[2];
        seekBar.getLocationOnScreen(seekBarPosition);
        int seekBarStartX = seekBarPosition[0];
        float iconPositionfix = iconPosition + seekBarStartX - (icon.getWidth() / 2);

        if(id == 1){
            icon.setTranslationX(iconPositionfix);
            icon.setVisibility(View.VISIBLE);
        }else if(id == 2){
            icon2.setTranslationX(iconPositionfix);
            icon2.setVisibility(View.VISIBLE);
        }else if(id == 3){
            icon3.setTranslationX(iconPositionfix);
            icon3.setVisibility(View.VISIBLE);
        }
    }
    private void displayThumbnail(Uri audioUri) throws IOException {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(this, audioUri);

        byte[] artBytes = mmr.getEmbeddedPicture();

        if (artBytes != null) {
            Bitmap thumbnail = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            if (thumbnail != null) {
                thumbnailImageView.setImageBitmap(thumbnail);
            } else {

                thumbnailImageView.setImageResource(R.drawable.default_thumbnail1);
            }
        } else {
            thumbnailImageView.setImageResource(R.drawable.default_thumbnail1);
        }

        mmr.release();
    }

    private void loadModel() {
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject pyf = py.getModule("test");
                model = pyf.callAttr("load_model"); // Gọi hàm load_model trong tệp Python
                Log.d("ModelLoad", "Model loaded successfully");
            } catch (Exception e) {
                Log.e("ModelLoad", "Failed to load model", e);
            }
        }).start();
    }

    private void togglePlay() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                playButton.setBackgroundResource(R.drawable.baseline_play_arrow_24);
            } else {
                mediaPlayer.start();
                playButton.setBackgroundResource(R.drawable.baseline_pause_24);
                updateSeekBar();
            }
        }
    }

    private Runnable updateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                seekBar.setProgress(mediaPlayer.getCurrentPosition());
                currentTimeAudio.setText(formatTime(mediaPlayer.getCurrentPosition()));
                handler.postDelayed(this, 1000);
            }
        }
    };

    private void updateSeekBar() {
        handler.post(updateSeekBarRunnable);
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_FILE && resultCode == RESULT_OK && data != null) {
            audioUri = data.getData();
            displayFileName(audioUri);
            playAudio(audioUri);
            saveAudioFile(audioUri);
            resetIcon();
            try {
                displayThumbnail(audioUri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void displayFileName(Uri audioUri) {
        String fileName = "";
        if (audioUri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(audioUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                fileName = cursor.getString(nameIndex);
                cursor.close();
            }
        } else {
            fileName = audioUri.getLastPathSegment();
        }
        fileName = fileName.substring(0,fileName.length()-4);
        audioFileName.setText("Tên bài hát: " + (fileName.isEmpty() ? "Tên không xác định" : fileName));
    }

    private String formatTime(int milliseconds) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void playAudio(Uri audioUri) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, audioUri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            seekBar.setMax(mediaPlayer.getDuration());
            timeAudio.setText(formatTime(mediaPlayer.getDuration()));
            playButton.setBackgroundResource(R.drawable.baseline_pause_24);
            updateSeekBar();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to play audio", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void saveAudioFile(Uri audioUri) {
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(audioUri);
                 ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, length);
                }

                byte[] audioBytes = byteArrayOutputStream.toByteArray();
                Log.d("FileSave", "File read successfully");

                // Gọi hàm Python và truyền mảng byte
                callPythonFunction(audioBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    private void resetIcon(){
        icon.setTranslationX(0);
        icon2.setTranslationX(0);
        icon3.setTranslationX(0);
        icon.setVisibility(View.INVISIBLE);
        icon2.setVisibility(View.INVISIBLE);
        icon3.setVisibility(View.INVISIBLE);
        icon.setOnClickListener(null);
        icon2.setOnClickListener(null);
        icon3.setOnClickListener(null);
    }
    private void callPythonFunction(byte[] audioBytes) {
        Python py = Python.getInstance();
        PyObject pyf = py.getModule("test");

        // Gọi hàm process() để xử lý âm thanh
        PyObject path = pyf.callAttr("process", audioBytes);
        Log.d("Path ", path.toString());
        PyObject result = pyf.callAttr("extract_tflite", path.toString(), 30);
        List<PyObject> highlight = result.asList();

        int start1 = highlight.get(0).toInt();
        int start2 = highlight.get(1).toInt();
        int start3 = highlight.get(2).toInt();
        activity.runOnUiThread(() -> {
            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playAtHighlightedTime(start1);
                }
            });
            icon2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playAtHighlightedTime(start2);
                }
            });
            icon3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playAtHighlightedTime(start3);
                }
            });
            updateIconPosition(start1,1);
            updateIconPosition(start2,2);
            updateIconPosition(start3,3);
        });
    }
    private void playAtHighlightedTime(int highlightStartTime) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(highlightStartTime * 1000); // Chuyển đổi giây thành mili giây
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacks(updateSeekBarRunnable);
    }
}
