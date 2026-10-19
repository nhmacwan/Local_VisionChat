package com.example.localvisionchat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ChatScreen(this) } }
    }
}

private data class ChatLine(val fromUser: Boolean, val text: String)

@Composable
private fun ChatScreen(activity: MainActivity) {
    val context = activity
    val lines = remember { mutableStateListOf<ChatLine>() }
    var modelPath by remember { mutableStateOf<String?>(null) }
    var modelStatus by remember { mutableStateOf("Choose a .litertlm model file to begin.") }
    var busy by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var attachedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var attachedName by remember { mutableStateOf<String?>(null) }

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val copied = copyUriToFile(context, uri, "selected-model.litertlm")
                modelPath = copied.absolutePath
                modelStatus = "Model selected. It will load when you send your first message."
            } catch (e: Exception) {
                modelStatus = "Could not copy model: ${e.message}"
            }
        }
    }
    val pickImageOrFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read selected file")
                attachedBytes = bytes
                attachedName = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
            } catch (e: Exception) {
                modelStatus = "Could not read attachment: ${e.message}"
            }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            attachedBytes = stream.toByteArray()
            attachedName = "camera-photo.jpg"
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Local Vision Chat", style = MaterialTheme.typography.headlineSmall)
        Text("On-device model • no cloud API configured", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickModel.launch(arrayOf("*/*")) }) { Text("Choose model") }
            OutlinedButton(onClick = { pickImageOrFile.launch(arrayOf("image/*", "text/*")) }) { Text("Attach file/photo") }
            OutlinedButton(onClick = { takePhoto.launch(null) }) { Text("Camera") }
        }
        Text(modelStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        attachedName?.let {
            Text("Attachment: $it", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { attachedBytes = null; attachedName = null }) { Text("Remove attachment") }
        }
        Divider()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                Surface(
                    color = if (line.fromUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        (if (line.fromUser) "You: " else "AI: ") + line.text,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message your local model…") },
                enabled = !busy,
                maxLines = 4
            )
            Button(
                enabled = !busy && modelPath != null && (draft.isNotBlank() || attachedBytes != null),
                onClick = {
                    val prompt = draft.trim().ifBlank { "Describe this attachment." }
                    val image = attachedBytes
                    val name = attachedName
                    draft = ""
                    attachedBytes = null
                    attachedName = null
                    lines.add(ChatLine(true, prompt + (name?.let { "\n[$it attached]" } ?: "")))
                    busy = true
                    activity.lifecycleScope.launch {
                        try {
                            val answer = runModel(context, modelPath!!, prompt, image)
                            lines.add(ChatLine(false, answer))
                            modelStatus = "Ready • model runs locally"
                        } catch (e: Exception) {
                            lines.add(ChatLine(false, "Error: ${e.message ?: e.javaClass.simpleName}"))
                            modelStatus = "Model error. Check that this is a compatible .litertlm file."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "…" else "Send") }
        }
    }
}

private suspend fun runModel(
    context: Context,
    path: String,
    prompt: String,
    imageBytes: ByteArray?
): String = withContext(Dispatchers.IO) {
    val engine = Engine(
        EngineConfig(
            modelPath = path,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )
    )
    try {
        engine.initialize()
        engine.createConversation().use { conversation ->
            val contents = if (imageBytes != null) {
                Contents.of(Content.ImageBytes(imageBytes), Content.Text(prompt))
            } else {
                Contents.of(Content.Text(prompt))
            }
            val result = conversation.sendMessage(contents)
            result.text
        }
    } finally {
        engine.close()
    }
}

private fun copyUriToFile(context: Context, uri: Uri, name: String): File {
    val target = File(context.filesDir, name)
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Unable to open selected file" }.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
    }
    return target
}
