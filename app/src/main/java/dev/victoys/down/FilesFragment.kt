package dev.victoys.down

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/** 右页：已下载完成的文件 */
class FilesFragment : Fragment(R.layout.fragment_files) {

    private var adapter: FileAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerFiles)
        val empty = view.findViewById<TextView>(R.id.textEmpty)

        adapter = FileAdapter { openFile(it) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        refresh(empty)
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<TextView>(R.id.textEmpty)?.let { refresh(it) }
    }

    private fun refresh(empty: TextView) {
        val dir = File(GopeedService.DOWNLOAD_DIR)
        val files = dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        adapter?.submit(files)
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, guessMime(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "webm", "mov" -> "video/*"
            "mp3", "flac", "wav", "m4a" -> "audio/*"
            "jpg", "jpeg", "png", "gif", "webp" -> "image/*"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "txt", "log", "json", "xml" -> "text/plain"
            else -> "*/*"
        }
    }
}
