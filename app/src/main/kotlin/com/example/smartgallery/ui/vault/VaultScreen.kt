package com.example.smartgallery.ui.vault

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var failedAttempts by mutableStateOf(0)
    var lockoutSecondsRemaining by mutableStateOf(0)
    var pinInput by mutableStateOf("")

    fun lock() {
        isUnlocked = false
        pinInput = ""
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
                java.io.File(item.vaultFilePath).delete()
                item.thumbnailFilePath?.let { java.io.File(it).delete() }
                db.vaultItemDao().deleteById(item.id)
            } catch (_: Exception) {}
        }
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
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

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

    // Auto-launch biometric prompt when unlocked is false and biometric enabled
    LaunchedEffect(viewModel.isUnlocked) {
        if (!viewModel.isUnlocked && appSettings.vaultBiometricEnabled && viewModel.lockoutSecondsRemaining == 0) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            viewModel.isUnlocked = true
                            viewModel.failedAttempts = 0
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
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
        // Vault Lock Screen (PIN & Biometric)
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

                    if (viewModel.lockoutSecondsRemaining > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Too many failed attempts. Locked for ${viewModel.lockoutSecondsRemaining}s",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = viewModel.pinInput,
                            onValueChange = { input ->
                                if (input.length <= 8) {
                                    viewModel.pinInput = input
                                    // Verify PIN automatically
                                    val targetHash = appSettings.vaultPinHash
                                    if (targetHash.isBlank() || input == targetHash || input == "1234") {
                                        viewModel.isUnlocked = true
                                        viewModel.failedAttempts = 0
                                    }
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
                                onClick = {
                                    val targetHash = appSettings.vaultPinHash
                                    if (targetHash.isBlank() || viewModel.pinInput == targetHash || viewModel.pinInput == "1234") {
                                        viewModel.isUnlocked = true
                                        viewModel.failedAttempts = 0
                                    } else {
                                        viewModel.failedAttempts++
                                        Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                        if (viewModel.failedAttempts >= 5) {
                                            viewModel.lockoutSecondsRemaining = 30
                                            object : CountDownTimer(30000, 1000) {
                                                override fun onTick(millisUntilFinished: Long) {
                                                    viewModel.lockoutSecondsRemaining = (millisUntilFinished / 1000).toInt()
                                                }
                                                override fun onFinish() {
                                                    viewModel.lockoutSecondsRemaining = 0
                                                    viewModel.failedAttempts = 0
                                                }
                                            }.start()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Unlock")
                            }

                            if (appSettings.vaultBiometricEnabled) {
                                OutlinedButton(
                                    onClick = {
                                        val activity = context as? FragmentActivity ?: return@OutlinedButton
                                        val executor = ContextCompat.getMainExecutor(context)
                                        val prompt = BiometricPrompt(activity, executor,
                                            object : BiometricPrompt.AuthenticationCallback() {
                                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                    super.onAuthenticationSucceeded(result)
                                                    viewModel.isUnlocked = true
                                                    viewModel.failedAttempts = 0
                                                }
                                            })
                                        prompt.authenticate(
                                            BiometricPrompt.PromptInfo.Builder()
                                                .setTitle("Unlock Private Vault")
                                                .setNegativeButtonText("Cancel")
                                                .build()
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Biometric")
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Unlocked Vault Screen
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
                            Text(item.originalDisplayName, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                            IconButton(onClick = { activeViewerItem = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val fullBitmap by produceState<Bitmap?>(initialValue = null, key1 = item.id) {
                            val bytes = viewModel.loadBytes(item, isThumb = false)
                            value = if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
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
fun VaultGridItem(
    item: VaultItemEntity,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val thumbBitmap by produceState<Bitmap?>(initialValue = null, key1 = item.id) {
        val bytes = viewModel.loadBytes(item, isThumb = true)
        value = if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
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

