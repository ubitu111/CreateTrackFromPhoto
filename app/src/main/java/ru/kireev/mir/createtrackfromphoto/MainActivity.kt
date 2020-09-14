package ru.kireev.mir.createtrackfromphoto

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import kotlinx.android.synthetic.main.activity_main.*
import net.rdrei.android.dirchooser.DirectoryChooserActivity
import net.rdrei.android.dirchooser.DirectoryChooserConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var stringBuilder = StringBuilder()
    private val fileName = "/track.plt"
    private var selectedDirectory = ""
    private val arrayExif = mutableListOf<ExifInterface>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonChooseDirectory.setOnClickListener(this)
        buttonChooseAnotherApp.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.buttonChooseDirectory -> {
                getPermissionStorage(1)
            }
            R.id.buttonChooseAnotherApp -> {
                getPermissionStorage(2)
            }
        }
    }

    private fun selectPhoto() {
        val intent = Intent(this, DirectoryChooserActivity::class.java)
        val config = DirectoryChooserConfig.builder()
            .newDirectoryName("Test")
            .allowReadOnlyDirectory(true)
            .allowNewDirectoryNameModification(true)
            .build()
        intent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config)
        startActivityForResult(intent, 1)
    }

    private fun selectByAnother() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Выберите программу"), 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
            val directory = data?.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR)
            if (directory != null){
                val files = File(directory).listFiles()
                files?.let {
                    selectedDirectory = directory
                    for (image in it) {
                        if (image.isFile) {
                            val exif = ExifInterface(image.path)
                            Log.d("matag", image.path)
                            arrayExif.add(exif)
                        }
                    }
                    sortAndAddToStringExif()
                }
            }
        } else
            if (requestCode == 2 && resultCode == RESULT_OK) {
            if (data?.clipData != null) {
                val count = data.clipData?.itemCount
                if (count != null) {
                    for (i in 0 until count) {
                        val imageUri = data.clipData?.getItemAt(i)?.uri
                        imageUri?.let { addExifFromUri(it) }
                    }
                }
                sortAndAddToStringExif()
            } else if (data?.data != null) {
                val imageUri = data.data
                imageUri?.let { addExifFromUri(it) }
                sortAndAddToStringExif()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun sortAndAddToStringExif(){
        stringBuilder.append("OziExplorer Track Point File Version 2.1\n")
            .append("WGS 84\n")
            .append("Altitude is in Feet\n")
            .append("Reserved 3\n")
            .append("0,2,255,Android Track Log File,1\n")
            .append("0\n")

        arrayExif.sortBy { it.dateTime }
        for (exif in arrayExif) {
            val latLongArray = exif.latLong
            if (latLongArray != null && latLongArray[0].toInt() != 0) {
                for (latLong in latLongArray) {
                        val result = String.format("%.6f", latLong).replace(",", ".")
                        stringBuilder.append("$result,")
                }

                stringBuilder.append("0,")

                val altitude = exif.getAltitude(0.0)
                stringBuilder.append("$altitude,")

                stringBuilder.append(",")

                val unixDate = exif.dateTime
                val date = Date(unixDate)
                val format = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
                format.timeZone = TimeZone.getTimeZone("GMT")
                val correctDate = format.format(date)
                stringBuilder.append("$correctDate,")

                val formatTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                formatTime.timeZone = TimeZone.getTimeZone("GMT")
                val correctTime = formatTime.format(date)
                stringBuilder.append(correctTime)
                stringBuilder.append("\n")
            }
        }
        createLpt()
    }

    private fun createLpt() {
        try {
                getExternalFilesDir(null)?.absolutePath?.let {
                    val file = File("$it/$fileName")
                    file.createNewFile()
                    val outputStream = FileOutputStream(file)
                    outputStream.write(stringBuilder.toString().toByteArray())
                    outputStream.close()
                    textViewNumberOfPhoto.text = "Запись прошла успешно, файл находится в $it"
                    stringBuilder = StringBuilder()
                    arrayExif.clear()
                    return
                }
            textViewNumberOfPhoto.text = "Произошла ошибка (Путь не существует)"
        } catch (e: Exception) {
            textViewNumberOfPhoto.text = "Произошла ошибка - ${e.message}"
        }
    }

    private fun getPermissionStorage(id: Int) {

        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted() {
                when (id) {
                    1 -> selectPhoto()
                    2 -> selectByAnother()
                }
            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                Toast.makeText(applicationContext, "da", Toast.LENGTH_SHORT).show()
            }
        }

        TedPermission.with(this)
            .setPermissionListener(permissionListener)
            .setDeniedMessage("net")
            .setPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .check()
    }

    private fun addExifFromUri(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream != null) {
            arrayExif.add(ExifInterface(inputStream))
        }
    }
}