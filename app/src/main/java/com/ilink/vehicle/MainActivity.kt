package com.ilink.vehicle

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ilink.vehicle.service.PickupSessionService
import com.ilink.vehicle.ui.AppRoot
import com.ilink.vehicle.ui.MainViewModel
import com.ilink.vehicle.ui.theme.ILinkTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    // 픽업 중 위치 텔레메트리(SIG-01)에 필요. 거절해도 앱은 동작하고 서비스만 일반 모드로 돈다
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PickupSessionService.hasLocationPermission(this)) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        setContent { ILinkTheme { AppRoot(vm) } }
    }
}
