package cafe.kankipro.ikev2

import android.app.Activity
import android.content.Context
import android.net.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C9CFF), background = Color(0xFF0E1117), surface = Color(0xFF171B24))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var server by remember { mutableStateOf("i.kankipro.cafe") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var caBytes by remember { mutableStateOf<ByteArray?>(null) }
    var caName by remember { mutableStateOf("None (system trust)") }
    var status by remember { mutableStateOf("Disconnected") }
    var connected by remember { mutableStateOf(false) }
    val vm = remember { ctx.getSystemService(Context.VPN_MANAGEMENT_SERVICE) as VpnManager }

    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { connected = true; status = "Connected" }
            override fun onLost(n: Network) { connected = false; status = "Disconnected" }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) runCatching { vm.startProvisionedVpnProfile(); status = "Connecting..." }
            .onFailure { e -> status = "Error: ${e.message}" }
        else status = "Permission denied"
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            caBytes = ctx.contentResolver.openInputStream(it)?.readBytes()
            caName = it.lastPathSegment?.substringAfterLast('/') ?: "Certificate"
        }
    }

    fun connect() {
        try {
            status = "Connecting..."
            val ca = caBytes?.let { CertificateFactory.getInstance("X.509").generateCertificate(it.inputStream()) as X509Certificate }
            val profile = Ikev2VpnProfile.Builder(server.trim(), server.trim())
                .setAuthUsernamePassword(user.trim(), pass, ca).build()
            val intent = vm.provisionVpnProfile(profile)
            if (intent != null) consent.launch(intent) else vm.startProvisionedVpnProfile()
        } catch (e: Exception) { status = "Error: ${e.message}" }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("IKEv2 VPN", fontSize = 30.sp, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) Color(0xFF4ADE80) else Color(0xFFF87171)))
            Spacer(Modifier.width(8.dp)); Text(status, color = Color.LightGray)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(server, { server = it }, label = { Text("Server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedButton({ picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("CA: $caName") }
            }
        }
        Spacer(Modifier.weight(1f))
        Button({ if (connected) { vm.stopProvisionedVpnProfile(); status = "Disconnected" } else connect() },
            Modifier.fillMaxWidth().height(56.dp).navigationBarsPadding(), shape = RoundedCornerShape(16.dp)) {
            Text(if (connected) "Disconnect" else "Connect", fontSize = 18.sp)
        }
    }
}
