package com.example.test;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_AUDIO_FILE = 1;
    private MediaPlayer mediaPlayer;
    private SeekBar seekBar;
    private Handler handler = new Handler();
    private Button button, applyButton, detectButton, playButton;
    private TextView resultTextView, textViewTime, audioFileName, currentTimeAudio, timeAudio;
    private String path = null;
    private Uri audioUri;
    private boolean isFileSelected = false;
    private int sampleRate = 22050;  // Tần số mẫu
    private int nFFT = 2048;         // Kích thước FFT
    private int hopLength = 512;     // Kích thước bước nhảy
    private int nMels = 128;         // Số lượng bộ lọc Mel

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        button = findViewById(R.id.button);
        applyButton = findViewById(R.id.apply);
        detectButton = findViewById(R.id.dectect);
        seekBar = findViewById(R.id.seekBar);
        resultTextView = findViewById(R.id.result);
        textViewTime = findViewById(R.id.textView);
        audioFileName = findViewById(R.id.audioFileName);
        currentTimeAudio = findViewById(R.id.curentTimeAudio);
        timeAudio = findViewById(R.id.timeAudio);
        playButton = findViewById(R.id.play);
        playButton.setBackgroundResource(R.drawable.baseline_play_arrow_24);

        button.setOnClickListener(v -> openFileChooser());
        playButton.setOnClickListener(v -> {
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
        });
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
        if (requestCode == PICK_AUDIO_FILE && resultCode == RESULT_OK) {
            if (data != null) {
                audioUri = data.getData();
                isFileSelected = true;
                displayFileName(audioUri);
                playAudio(audioUri);
                playButton.setBackgroundResource(R.drawable.baseline_pause_24);

                String destinationPath = getFilesDir() + "/python/model/";
                File destinationDir = new File(destinationPath);
                if (!destinationDir.exists()) {
                    destinationDir.mkdirs(); // Tạo thư mục nếu chưa tồn tại
                }

                try {
                    // Đọc nội dung file audio thành mảng byte
                    InputStream inputStream = getContentResolver().openInputStream(audioUri);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, length);
                    }
                    byte[] fileBytes = byteArrayOutputStream.toByteArray();
                    inputStream.close();
                    byteArrayOutputStream.close();

                    // Lưu file vào thư mục đã chỉ định
                    String fileName = "temp1.wav"; // Tên file bạn muốn lưu
                    File outputFile = new File(destinationDir, fileName);
                    FileOutputStream fos = new FileOutputStream(outputFile);
                    fos.write(fileBytes);
                    fos.close();

                    // Kiểm tra nếu file đã được lưu
                    Log.d("FileSave", "File saved successfully: " + outputFile.getAbsolutePath());

                } catch (IOException e) {
                    e.printStackTrace();
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        Python py = Python.getInstance();
                        PyObject pyf = py.getModule("test");

                        // Gọi hàm Python `extract_tflite` và nhận kết quả sau khi đã xử lý
                        PyObject result = pyf.callAttr("extract_tflite", "temp.wav", 30);

                        // Lấy kết quả từ Python và xử lý trong Java
                        List<PyObject> highlight = result.asList();
                        int start = highlight.get(0).toInt();
                        int end = highlight.get(1).toInt();

                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // Cập nhật giao diện người dùng
                                resultTextView.setText("Start : " + formatTime(start) + " - End : " + formatTime(end));
                            }
                        });

                        Log.d("start", String.valueOf(start));
                    }
                }).start();

            }
        }
    }

    private String arrayToString(float[][] array) {
        StringBuilder sb = new StringBuilder();
        for (float[] row : array) {
            for (float value : row) {
                sb.append(String.format("%.2f", value)).append(" "); // Định dạng giá trị tới 2 chữ số thập phân
            }
            sb.append("\n"); // Thêm dòng mới cho mỗi hàng
        }
        return sb.toString();
    }

    // Phương thức chuyển đổi Uri thành đường dẫn file
    private String getFilePathFromUri(Uri uri) {
        File file = new File(getCacheDir(), "temp_audio_file.wav");
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file.getPath();
    }


    private void displayFileName(Uri audioUri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(this, audioUri);
        String fileName = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        if (fileName == null) {
            fileName = "Tên không xác định";
        }
        audioFileName.setText("Tên bài hát: " + fileName);
    }

    private String formatTime(int milliseconds) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(minutes);
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
            updateSeekBar();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to play audio", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
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