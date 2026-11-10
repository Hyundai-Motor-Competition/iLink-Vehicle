package com.ilink.vehicle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ilink.vehicle.ILinkApplication
import com.ilink.vehicle.ui.component.DemoPanel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilink.vehicle.domain.VehicleScreen
import com.ilink.vehicle.ui.screen.*

@Composable
fun AppRoot(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val demo = (context.applicationContext as ILinkApplication).container.demo
    // V00 '나중에 하기': 앱을 뒤로 보낸다 (연결 전이라 갈 화면이 없음)
    val activity = context as? android.app.Activity
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (s.screen) {
                VehicleScreen.V00 -> V00Connect(s, onReissue = vm::startLink, onLater = { activity?.moveTaskToBack(true) })
                VehicleScreen.V01 -> V01Today(s, onStart = { vm.startPickup(it) }, onSettings = { vm.openSettings() })
                VehicleScreen.V02 -> V02Recommend(s, onRoute = vm::routeTo)
                VehicleScreen.V03 -> V03Progress(s, onHold = { vm.holdCall() }, onRelease = { vm.releaseCallHold() })
                VehicleScreen.V04 -> V04Call(s, onHold = { vm.holdCall() }, onRelease = { vm.releaseCallHold() })
                VehicleScreen.V05 -> V05Complete(s, onReject = { vm.rejectBoarding(it) }, onManual = { vm.completeManually() }, onDone = vm::finish)
                VehicleScreen.V06 -> V06Exception(s, onCancel = { vm.cancelPickup() })
                VehicleScreen.V07 -> V07Settings(s, onBack = { vm.go(VehicleScreen.V01) }, onLogout = { vm.logout() })
            }
            demo?.let {
                DemoPanel(it, onReset = { it.reset(); vm.logout() }, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}
