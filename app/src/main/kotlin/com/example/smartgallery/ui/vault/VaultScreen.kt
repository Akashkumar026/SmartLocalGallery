package com.example.smartgallery.ui.vault

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.smartgallery.data.db.GalleryDatabase
import com.example.smartgallery.data.db.VaultItemEntity
import com.example.smartgallery.data.settings.AppSettings
import com.example.smartgallery.data.settings.SettingsRepository
import com.example.smartgallery.vault.VaultAndFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    val db: GalleryDatabase,
    val vaultManager: VaultAndFileManager,
    val settings: SettingsRepository
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    val vaultItems: StateFlow<List<VaultItemEntity>> = db.vaultItemDao().getAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    var isUnlocked by mutableStateOf(false)
    var pinInput by mutableStateOf("")

    fun lock() {
        isUnlocked = false
        pinInput = ""
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            settings.setVaultPin(pin)
            isUnlocked = true
            pinInput = ""
        }
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = settings.verifyVaultPin(pin)
            if (valid) {
                isUnlocked = true
                pinInput = ""
            }
            onResult(valid)
        }
    }

    fun unlockViaBiometric() {
        viewModelScope.launch {
            settings.clearVaultLockout()
            isUnlocked = true
        }
    }

    fun restoreItem(item: VaultItemEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            val success = vaultManager.restoreVaultItemToGallery(item)
            if (success) {
                onComplete()
            }
        }
    }

    fun permanentlyDeleteItem(item: VaultItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(item.vaultFilePath).delete()
                item.thumbnailFilePath?.let { File(it).delete() }
                db.vaultItemDao().deleteById(item.id)
            } catch (_: Exception) {}
        }
    }

    suspend fun loadBitmap(item: VaultItemEntity, isThumb: Boolean = false): Bitmap? {
        return vaultManager.loadDecryptedBitmap(item, isThumb = isThumb)
    }

    suspend fun loadBytes(item: VaultItemEntity, isThumb: Boolean = true): ByteArray? {
        return vaultManager.loadDecryptedBytes(item, isThumb = isThumb)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    navController: NavController,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appSettings by viewModel.appSettings.collectAsState()
    val vaultItems by viewModel.vaultItems.collectAsState()

    var activeViewerItem by remember { mutableStateOf<VaultItemEntity?>(null) }

    // Lock on pause/backgrounding
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.lock()
                activeViewerItem = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Lockout countdown timer
    var lockoutRemainingSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(appSettings.vaultLockoutUntilMs) {
        while (true) {
            val now = System.currentTimeMillis()
            val remaining = ((appSettings.vaultLockoutUntilMs - now) / 1000).toInt()
            if (remaining > 0) {
                lockoutRemainingSeconds = remaining
                delay(1000)
            } else {
                lockoutRemainingSeconds = 0
                break
            }
        }
    }

    // Auto-launch biometric prompt when locked and biometric is enabled and not locked out
    val hasPinConfigured = appSettings.vaultPinHash.isNotBlank()
    LaunchedEffect(viewModel.isUnlocked, hasPinConfigured, lockoutRemainingSeconds) {
        if (!viewModel.isUnlocked && hasPinConfigured && appSettings.vaultBiometricEnabled && lockoutRemainingSeconds == 0) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            viewModel.unlockViaBiometric()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Private Vault")
                    .setSubtitle("Confirm your biometric credentials")
                    .setNegativeButtonText("Use PIN")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    if (!viewModel.isUnlocked) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Private Vault") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPinConfigured) {
                    // PIN Setup Flow
                    VaultSetupPinCard(
                        onSetPin = { newPin ->
                            viewModel.setPin(newPin)
                            Toast.makeText(context, "Vault PIN created successfully", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    // PIN Verification Flow
                    VaultUnlockCard(
                        pinInput = viewModel.pinInput,
                        onPinChange = { viewModel.pinInput = it },
                        lockoutSeconds = lockoutRemainingSeconds,
                        failedAttempts = appSettings.vaultFailedAttempts,
                        biometricEnabled = appSettings.vaultBiometricEnabled,
                        onUnlock = {
                            viewModel.verifyPin(viewModel.pinInput) { success ->
                                if (!success) {
                                    val attemptsLeft = (5 - (appSettings.vaultFailedAttempts + 1)).coerceAtLeast(0)
                                    val msg = if (attemptsLeft > 0) {
                                        "Incorrect PIN ($attemptsLeft attempts remaining)"
                                    } else {
                                        "Too many failed attempts. Vault locked."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onBiometricClick = {
                            val activity = context as? FragmentActivity ?: return@VaultUnlockCard
                            val executor = ContextCompat.getMainExecutor(context)
                            val prompt = BiometricPrompt(activity, executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        viewModel.unlockViaBiometric()
                                    }
                                })
                            prompt.authenticate(
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Unlock Private Vault")
                                    .setNegativeButtonText("Cancel")
                                    .build()
                            )
                        }
                    )
                }
            }
        }
        return
    }

    // ─── Unlocked Vault Screen ────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Private Vault", fontWeight = FontWeight.Bold)
                        Text("${vaultItems.size} encrypted items", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.lock() }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Now")
                    }
                }
            )
        }
    ) { padding ->
        if (vaultItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Your Vault is Empty", fontWeight = FontWeight.Bold)
                    Text(
                        "Select photos or videos in Gallery and choose 'Move to Vault'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(vaultItems, key = { it.id }) { item ->
                    VaultGridItem(
                        item = item,
                        viewModel = viewModel,
                        onClick = { activeViewerItem = item },
                        onRestore = {
                            viewModel.restoreItem(item) {
                                Toast.makeText(context, "Restored to Gallery", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            viewModel.permanentlyDeleteItem(item)
                            Toast.makeText(context, "Purged from Vault", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // Active In-Memory Viewer Dialog
        activeViewerItem?.let { item ->
            Dialog(onDismissRequest = { activeViewerItem = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.originalDisplayName,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { activeViewerItem = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val isVideo = item.originalMediaType.equals("VIDEO", ignoreCase = true)

                        if (isVideo) {
                            // Video Vault Item Preview & Direct Streaming Launch
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = "Play Video",
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Encrypted Video Stream",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Streams on-the-fly via AES-256 CipherDataSource",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                val vaultUri = "vault://${item.uuid}"
                                                activeViewerItem = null
                                                navController.navigate("video_player?uri=${Uri.encode(vaultUri)}&title=${Uri.encode(item.originalDisplayName)}")
                                            }
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Play in Cinema Player")
                                        }
                                    }
                                }
                            }
                        } else {
                            // Image Preview
                            val fullBitmap by produceState<Bitmap?>(initialValue = null, key1 = item.id) {
                                value = viewModel.loadBitmap(item, isThumb = false)
                            }

                            if (fullBitmap != null) {
                                Image(
                                    bitmap = fullBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.permanentlyDeleteItem(item)
                                    activeViewerItem = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete")
                            }

                            Button(
                                onClick = {
                                    viewModel.restoreItem(item) {
                                        activeViewerItem = null
                                        Toast.makeText(context, "Restored to Gallery", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultSetupPinCard(onSetPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Text(
            "Setup Vault PIN",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Create a 4 to 8 digit Master PIN. Your media is hardware-encrypted using AES-256-GCM keys.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                    pin = it
                    errorMessage = null
                }
            },
            label = { Text("New PIN (4-8 digits)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmPin,
            onValueChange = {
                if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                    confirmPin = it
                    errorMessage = null
                }
            },
            label = { Text("Confirm PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Text(
                errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                if (pin.length < 4) {
                    errorMessage = "PIN must be at least 4 digits"
                } else if (pin != confirmPin) {
                    errorMessage = "PINs do not match"
                } else {
                    onSetPin(pin)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create PIN & Enter Vault")
        }
    }
}

@Composable
fun VaultUnlockCard(
    pinInput: String,
    onPinChange: (String) -> Unit,
    lockoutSeconds: Int,
    failedAttempts: Int,
    biometricEnabled: Boolean,
    onUnlock: () -> Unit,
    onBiometricClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Text(
            "Vault is Locked",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Enter PIN or authenticate via Biometrics to access AES-256 encrypted storage",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (lockoutSeconds > 0) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Too many failed attempts. Locked for ${lockoutSeconds}s",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            OutlinedTextField(
                value = pinInput,
                onValueChange = { input ->
                    if (input.length <= 8 && input.all { it.isDigit() }) {
                        onPinChange(input)
                    }
                },
                label = { Text("Vault PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.weight(1f),
                    enabled = pinInput.length >= 4
                ) {
                    Text("Unlock")
                }

                if (biometricEnabled) {
                    OutlinedButton(onClick = onBiometricClick) {
                        Icon(Icons.Default.Fingerprint, contentDescription = "Biometric")
                    }
                }
            }
        }
    }
}

@Composable
fun VaultGridItem(
    item: VaultItemEntity,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val thumbBitmap by produceState<Bitmap?>(initialValue = null, key1 = item.id) {
        value = viewModel.loadBitmap(item, isThumb = true)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (item.originalMediaType.equals("VIDEO", true)) Icons.Default.Videocam else Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Video overlay badge
        if (item.originalMediaType.equals("VIDEO", true)) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "VIDEO",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay options button
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Restore to Gallery") },
                onClick = {
                    showMenu = false
                    onRestore()
                },
                leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete Permanently") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}
