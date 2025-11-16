package com.g22.offline_blockchain_payments.ble.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionManager {
    
    /**
     * Obtiene los permisos BLE necesarios según la versión de Android
     */
    fun getBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            // Android < 12
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    /**
     * Obtiene el permiso de cámara
     */
    fun getCameraPermission(): Array<String> {
        return arrayOf(Manifest.permission.CAMERA)
    }
    
    /**
     * Obtiene todos los permisos necesarios para el Host
     */
    fun getHostPermissions(): Array<String> {
        return getBlePermissions()
    }
    
    /**
     * Obtiene todos los permisos necesarios para el Cliente
     */
    fun getClientPermissions(): Array<String> {
        return getBlePermissions() + getCameraPermission()
    }
    
    /**
     * Verifica si todos los permisos en el array están concedidos
     */
    fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Verifica si los permisos BLE están concedidos
     */
    fun areBlePermissionsGranted(context: Context): Boolean {
        return arePermissionsGranted(context, getBlePermissions())
    }
    
    /**
     * Verifica si el permiso de cámara está concedido
     */
    fun isCameraPermissionGranted(context: Context): Boolean {
        return arePermissionsGranted(context, getCameraPermission())
    }
    
    /**
     * Verifica si todos los permisos del Host están concedidos
     */
    fun areHostPermissionsGranted(context: Context): Boolean {
        return arePermissionsGranted(context, getHostPermissions())
    }
    
    /**
     * Verifica si todos los permisos del Cliente están concedidos
     */
    fun areClientPermissionsGranted(context: Context): Boolean {
        return arePermissionsGranted(context, getClientPermissions())
    }
}

