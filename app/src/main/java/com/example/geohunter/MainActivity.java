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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private List<Circle> zonasPeligrosas = new ArrayList<>();
    private LinearLayout panelEstado;
    private TextView txtEstado, txtDistancia;
    private Button btnReiniciar;
    private Handler handler = new Handler();
    private MediaPlayer alerta;
    private boolean enPeligro = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        panelEstado = findViewById(R.id.panelEstado);
        txtEstado = findViewById(R.id.txtEstado);
        txtDistancia = findViewById(R.id.txtDistancia);
        btnReiniciar = findViewById(R.id.btnReiniciar);

        alerta = MediaPlayer.create(this, R.raw.alerta); // coloca un sonido en res/raw/alerta.mp3
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        btnReiniciar.setOnClickListener(v -> generarZonasAleatorias());
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
                    panelEstado.setBackgroundColor(Color.parseColor("#AAFF0000"));
                    txtEstado.setText("⚠️ ¡PELIGRO! Sal de la zona roja");
                    if (!alerta.isPlaying()) alerta.start();
                }
            } else {
                // Zona segura
                if (enPeligro) alerta.pause();
                enPeligro = false;
                panelEstado.setBackgroundColor(Color.parseColor("#AA00FF00"));
                txtEstado.setText("Zona segura ✅");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (alerta != null) alerta.release();
    }
}