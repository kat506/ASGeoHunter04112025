package com.example.geohunter;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;

// CameraX
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

// ML Kit
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private List<Circle> zonasPeligrosas = new ArrayList<>();
    private LinearLayout panelEstado;
    private TextView txtEstado, txtDistancia;
    private Button btnReiniciar, btnAnalizar;
    private PreviewView previewView;
    private Handler handler = new Handler();
    private MediaPlayer alerta;
    private boolean enPeligro = false;
    private boolean enZonaPeligrosa = false;
    // ML/Cámara
    private boolean analisisActivo = false;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageAnalysis imageAnalysis;
    private Preview cameraPreview;
    // Ventana para suavizar el riesgo de contexto
    private final Deque<Float> contextoVentana = new ArrayDeque<>();
    private static final int MAX_VENTANA = 10; // ~1s si ~10 FPS

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        panelEstado = findViewById(R.id.panelEstado);
        txtEstado = findViewById(R.id.txtEstado);
        txtDistancia = findViewById(R.id.txtDistancia);
        btnReiniciar = findViewById(R.id.btnReiniciar);
        btnAnalizar = findViewById(R.id.btnAnalizar);
        previewView = findViewById(R.id.previewView);

        alerta = MediaPlayer.create(this, R.raw.alerta); // coloca un sonido en res/raw/alerta.mp3
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        btnReiniciar.setOnClickListener(v -> generarZonasAleatorias());
        btnAnalizar.setOnClickListener(v -> toggleAnalisis());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        verificarPermisos();
    }

    private void verificarPermisos() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 2);
        }
        iniciarJuego();
    }

    private void iniciarJuego() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Permiso de ubicación no concedido.", Toast.LENGTH_SHORT).show();
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng actual = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(actual, 17f));
                generarZonasAleatorias();
                iniciarVerificacion();
            }
        });
    }


    private void generarZonasAleatorias() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mMap.clear();
        zonasPeligrosas.clear();

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) return;
            LatLng origen = new LatLng(location.getLatitude(), location.getLongitude());

            // Dibujar una zona segura verde en el centro
            mMap.addCircle(new CircleOptions()
                    .center(origen)
                    .radius(15)
                    .strokeColor(Color.GREEN)
                    .fillColor(0x2200FF00)
                    .strokeWidth(3));

            // Crear 3 zonas rojas alrededor
            for (int i = 0; i < 3; i++) {
                double latOffset = (Math.random() - 0.5) / 2000; // ~25 m
                double lonOffset = (Math.random() - 0.5) / 2000;
                LatLng centro = new LatLng(origen.latitude + latOffset, origen.longitude + lonOffset);

                Circle c = mMap.addCircle(new CircleOptions()
                        .center(centro)
                        .radius(10) // radio peligro
                        .strokeColor(Color.RED)
                        .fillColor(0x22FF0000)
                        .strokeWidth(3));

                zonasPeligrosas.add(c);
            }
        });
    }

    private void iniciarVerificacion() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                verificarZona();
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void verificarZona() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) return;

            float distanciaMin = Float.MAX_VALUE;
            for (Circle c : zonasPeligrosas) {
                float[] resultados = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                        c.getCenter().latitude, c.getCenter().longitude, resultados);
                if (resultados[0] < distanciaMin)
                    distanciaMin = resultados[0];
            }

            txtDistancia.setText(String.format("Distancia a la zona más cercana: %.1f m", distanciaMin));

            if (distanciaMin < 10) {
                // En zona peligrosa
                if (!enPeligro) {
                    enPeligro = true;
                    enZonaPeligrosa = true;
                    panelEstado.setBackgroundColor(Color.parseColor("#AAFF0000"));
                    txtEstado.setText("⚠️ ¡PELIGRO! Sal de la zona roja");
                    // Activar cámara automáticamente para análisis
                    if (!analisisActivo) iniciarAnalisis();
                }
            } else {
                // Zona segura
                if (enPeligro) alerta.pause();
                enPeligro = false;
                enZonaPeligrosa = false;
                panelEstado.setBackgroundColor(Color.parseColor("#AA00FF00"));
                txtEstado.setText("Zona segura ✅");
                if (analisisActivo) detenerAnalisis();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (alerta != null) alerta.release();
        detenerAnalisis();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }

    // ====== Integración CameraX + Image Labeling (no altera la lógica existente) ======

    private void toggleAnalisis() {
        if (analisisActivo) {
            detenerAnalisis();
        } else {
            iniciarAnalisis();
        }
    }

    private void iniciarAnalisis() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 2);
            return;
        }
        analisisActivo = true;
        previewView.setVisibility(View.VISIBLE);
        btnAnalizar.setText("Detener análisis");
        configurarCamara();
    }

    private void detenerAnalisis() {
        analisisActivo = false;
        btnAnalizar.setText("Analizar entorno");
        previewView.setVisibility(View.GONE);
        contextoVentana.clear();
        if (cameraProviderFuture != null) {
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                provider.unbindAll();
            } catch (Exception ignored) { }
        }
    }

    private void configurarCamara() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                cameraPreview = new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::procesarFrame);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageAnalysis);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "No se pudo iniciar la cámara", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void procesarFrame(ImageProxy imageProxy) {
        if (!analisisActivo) {
            imageProxy.close();
            return;
        }
        try {
            if (imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), rotationDegrees);

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        boolean hayPersona = faces != null && !faces.isEmpty();

                        // Sonido solo si se detecta rostro dentro de zona peligrosa
                        if (enZonaPeligrosa && hayPersona) {
                            if (!alerta.isPlaying()) alerta.start();
                        } else {
                            if (alerta.isPlaying() && enPeligro) alerta.pause();
                        }
                    })
                    .addOnFailureListener(Throwable::printStackTrace)
                    .addOnCompleteListener(task -> imageProxy.close());
        } catch (Exception e) {
            e.printStackTrace();
            imageProxy.close();
        }
    }

    // Ya no se necesita conversión; ML Kit espera grados (0/90/180/270) directamente

    // Eliminamos lógica de contexto basada en etiquetas para simplificar al usar rostro

    private void aplicarRiesgoContextual(float contextRiskAvg) {
        // Mantener por si luego se desea usar el promedio de contexto para UI;
        // ahora no modifica sonido ni color (la UI principal la gestiona verificarZona).
    }
}