    package com.example.photoblog;

    import androidx.activity.result.ActivityResultLauncher;
    import androidx.activity.result.contract.ActivityResultContracts;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.app.ActivityCompat;
    import androidx.core.content.ContextCompat;

    import android.Manifest;
    import android.content.Intent;
    import android.content.pm.PackageManager;
    import android.database.Cursor;
    import android.net.Uri;
    import android.os.Build;
    import android.os.Bundle;
    import android.os.Handler;
    import android.os.Looper;
    import android.provider.MediaStore;
    import android.util.Log;
    import android.view.View;
    import android.widget.Button;
    import android.widget.Toast;

    import org.json.JSONException;
    import org.json.JSONObject;

    import java.io.BufferedReader;
    import java.io.DataOutputStream;
    import java.io.File;
    import java.io.FileInputStream;
    import java.io.IOException;
    import java.io.InputStream;
    import java.io.InputStreamReader;
    import java.io.OutputStreamWriter;
    import java.net.HttpURLConnection;
    import java.net.MalformedURLException;
    import java.net.URL;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;

    public class MainActivity extends AppCompatActivity {
        private static final int READ_MEDIA_IMAGES_PERMISSION_CODE = 1001;  // 상수 정의
        private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1002;
        // 상수 정의

        //private static final String UPLOAD_URL = "http://127.0.0.1:8000/api_root/Post/";
        private static final String UPLOAD_URL = "http://10.0.2.2:8000/api_root/Post/";
        Uri imageUri = null;

        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());

        private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult( //...코드 계속
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        imageUri = result.getData().getData();
                        String filePath = getRealPathFromURI(imageUri);
                        executorService.execute(() -> {
                            String uploadResult;
                            try {
                                uploadResult = uploadImage(filePath);
                            } catch (IOException e) {
                                uploadResult = "Upload failed: " + e.getMessage();
                            }
                            String finalUploadResult = uploadResult;
                            handler.post(() -> Toast.makeText(MainActivity.this, finalUploadResult, Toast.LENGTH_LONG).show());
                        });
                    }
                }
        );

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            Button uploadButton = findViewById(R.id.uploadButton);
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                                    READ_MEDIA_IMAGES_PERMISSION_CODE);
                        } else {
                            openImagePicker();
                        }
                    } else {
                        if (ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                    READ_EXTERNAL_STORAGE_PERMISSION_CODE);
                        } else {
                            openImagePicker();
                        }
                    }
                }
            });
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == READ_MEDIA_IMAGES_PERMISSION_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openImagePicker();
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private void openImagePicker() {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        }

        private String getRealPathFromURI(Uri contentUri) {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null);
            if (cursor == null) {
                return contentUri.getPath();
            } else {
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                String path = cursor.getString(columnIndex);
                cursor.close();
                return path;
            }
        }

        private String uploadImage(String filePath) throws IOException {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            HttpURLConnection connection = null;
            DataOutputStream outputStream = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(UPLOAD_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "JWT 849d4e718e7bb6c089ebd3698a73488732aeae17");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);
                connection.setDoInput(true);

                outputStream = new DataOutputStream(connection.getOutputStream());

                // Add JSON fields
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("author", 1);
                jsonObject.put("title", "안드로이드-REST API 테스트");
                jsonObject.put("text", "안드로이드로 작성된 REST API 테스트 입력 입니다.");
                jsonObject.put("created_date", "2024-06-03T18:34:00+09:00");
                jsonObject.put("published_date", "2024-06-03T18:34:00+09:00");

                // Add JSON part
                outputStream.writeBytes("--" + boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"post\"\r\n\r\n");
                outputStream.writeBytes(jsonObject.toString() + "\r\n");

                // Add file part
                outputStream.writeBytes("--" + boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"" + new File(filePath).getName() + "\"\r\n");
                outputStream.writeBytes("Content-Type: image/jpeg\r\n\r\n");

                FileInputStream fileInputStream = new FileInputStream(new File(filePath));
                int bytesRead;
                byte[] buffer = new byte[1024];
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();
                outputStream.writeBytes("\r\n");

                outputStream.writeBytes("--" + boundary + "--\r\n");

                outputStream.flush();
                outputStream.close();

                int responseCode = connection.getResponseCode();
                InputStream inputStream;
                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }

                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } catch (MalformedURLException e) {
                return "Upload failed: MalformedURLException: " + e.getMessage();
            } catch (IOException e) {
                return "Upload failed: IOException: " + e.getMessage();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (reader != null) {
                    reader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }