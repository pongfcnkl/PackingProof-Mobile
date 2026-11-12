package app.packingproof.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File

class StorageDirectoryPlugin(
    private val activity: Activity,
    messenger: io.flutter.plugin.common.BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, CHANNEL)
    private var pendingResult: MethodChannel.Result? = null
    private var pendingRequestCode: Int? = null

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "requestAccess" -> requestAccess(result)
            "pickDirectory" -> pickDirectory(result)
            "validateDirectory" -> validateDirectory(call.argument("path"), result)
            else -> result.notImplemented()
        }
    }

    private fun requestAccess(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            result.success(true)
            return
        }
        if (!beginRequest(REQUEST_MANAGE_STORAGE, result)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${activity.packageName}"),
        )
        try {
            activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
        } catch (_: ActivityNotFoundException) {
            activity.startActivityForResult(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                REQUEST_MANAGE_STORAGE,
            )
        }
    }

    private fun pickDirectory(result: MethodChannel.Result) {
        if (!beginRequest(REQUEST_PICK_DIRECTORY, result)) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            setPackage(DOCUMENTS_UI_PACKAGE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
        }
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_DIRECTORY)
        } catch (_: ActivityNotFoundException) {
            finishError("documents_ui_unavailable", "系统文件选择器不可用")
        }
    }

    private fun validateDirectory(rawPath: String?, result: MethodChannel.Result) {
        val path = rawPath?.trim().orEmpty()
        if (path.isEmpty() || !File(path).isAbsolute) {
            result.error("invalid_storage_directory", "请输入完整的绝对路径", null)
            return
        }
        val directory = File(path)
        try {
            if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
                result.error("storage_directory_unavailable", "无法创建保存目录", null)
                return
            }
            val probe = File(directory, ".packingproof-write-test")
            probe.writeText("ok")
            if (!probe.delete()) probe.deleteOnExit()
            result.success(directory.canonicalPath)
        } catch (error: Exception) {
            result.error("storage_directory_denied", "没有该目录的读写权限", error.message)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != pendingRequestCode) return false
        when (requestCode) {
            REQUEST_MANAGE_STORAGE -> finishSuccess(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager(),
            )
            REQUEST_PICK_DIRECTORY -> {
                if (resultCode != Activity.RESULT_OK || data?.data == null) {
                    finishSuccess(null)
                    return true
                }
                val uri = data.data!!
                val flags = data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                runCatching { activity.contentResolver.takePersistableUriPermission(uri, flags) }
                val path = treeUriToPath(uri)
                if (path == null) {
                    finishError("unsupported_storage_provider", "请选择本机存储中的文件夹")
                } else {
                    validatePickedDirectory(path)
                }
            }
        }
        return true
    }

    private fun validatePickedDirectory(path: String) {
        val result = pendingResult ?: return
        pendingResult = null
        pendingRequestCode = null
        validateDirectory(path, result)
    }

    private fun treeUriToPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val parts = documentId.split(':', limit = 2)
        val volumeId = parts.firstOrNull().orEmpty()
        val relative = parts.getOrNull(1).orEmpty()
        val root = if (volumeId.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage", volumeId).takeIf { it.exists() }
        } ?: return null
        return if (relative.isEmpty()) root.path else File(root, relative).path
    }

    private fun beginRequest(requestCode: Int, result: MethodChannel.Result): Boolean {
        if (pendingResult != null) {
            result.error("storage_request_in_progress", "已有存储目录请求正在进行", null)
            return false
        }
        pendingResult = result
        pendingRequestCode = requestCode
        return true
    }

    private fun finishSuccess(value: Any?) {
        pendingResult?.success(value)
        pendingResult = null
        pendingRequestCode = null
    }

    private fun finishError(code: String, message: String) {
        pendingResult?.error(code, message, null)
        pendingResult = null
        pendingRequestCode = null
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
        finishError("storage_request_cancelled", "存储目录请求已取消")
    }

    companion object {
        private const val CHANNEL = "app.packingproof.mobile/storage_directory"
        private const val DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"
        private const val REQUEST_MANAGE_STORAGE = 48120
        private const val REQUEST_PICK_DIRECTORY = 48121
    }
}
