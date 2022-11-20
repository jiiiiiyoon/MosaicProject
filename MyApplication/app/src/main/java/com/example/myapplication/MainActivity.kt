package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityMainBinding
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var filePath: String
    private var imgBitmap: Bitmap? = null
    var isConnected = false
    var base64bitmap: Bitmap? = null
    var base64: String? = null
    private lateinit var mSocket: Socket
    var bitmap: Bitmap? = null
    var itemList = arrayListOf<RecyclerViewData>()      // 아이템 배열
    val listAdapter = RecyclerViewAdapter(itemList)     // 어댑터
    lateinit var faceList: ArrayList<Int>
    var currentTime : Long = 0
    val dataFormat = SimpleDateFormat("yyyyMMdd-hhmmss")

    @SuppressLint("SimpleDateFormat", "NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerView.adapter = listAdapter

        faceList = ArrayList()

        listAdapter.setItemClickListener(object : RecyclerViewAdapter.OnItemClickListener{
            @SuppressLint("NotifyDataSetChanged")
            override fun onClick(v: View, position: Int) {
                Log.e("flag", itemList.get(position).flag.toString())
                if (itemList.get(position).flag) {
                    faceList.remove(position)
                    itemList.add(position, RecyclerViewData(itemList.get(position).imgBitmap, false))
                    itemList.removeAt(position + 1)

                } else {
                    faceList.add(position)
                    itemList.add(position, RecyclerViewData(itemList.get(position).imgBitmap, true))
                    itemList.removeAt(position + 1)

                }
                runOnUiThread {
                    listAdapter.notifyDataSetChanged()
                }


                Log.e("리스트", faceList.toString())
            }
        })

        SocketHandler.setSocket()
        SocketHandler.establishConnection()

        mSocket = SocketHandler.getSocket()

        val requestGalleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult())
        {
            try {
                val calRatio = calculateInSampleSize(
                    it.data!!.data!!,
                    resources.getDimensionPixelSize(R.dimen.imgSize),
                    resources.getDimensionPixelSize(R.dimen.imgSize)
                )

                val option = BitmapFactory.Options()
                option.inSampleSize = calRatio

                var inputStream = contentResolver.openInputStream(it.data!!.data!!)
                val bitmap = BitmapFactory.decodeStream(inputStream, null, option)
                inputStream!!.close()
                inputStream = null
                imgBitmap = bitmap

                base64bitmap = imgBitmap
                base64 = bitmapToString(imgBitmap)

                currentTime = System.currentTimeMillis()
                mSocket.emit("uploadImg", dataFormat.format(currentTime), base64)

                bitmap?.let {
                    binding.choiceImage.setImageBitmap(bitmap)
                }
                mSocket.on("get_faces", imageSet)

//                bitmap?.let {
//                    binding.choiceImage.setImageBitmap(bitmap)
//                } ?: let {
//                    Log.d("log", "bitmap null")
//                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            requestGalleryLauncher.launch(intent)
        }

        val requestCameraFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) {
            val calRatio = calculateInSampleSize(
                Uri.fromFile(File(filePath)),
                resources.getDimensionPixelSize(R.dimen.imgSize),
                resources.getDimensionPixelSize(R.dimen.imgSize)
            )


            val option = BitmapFactory.Options()
            option.inSampleSize = calRatio
            bitmap = BitmapFactory.decodeFile(filePath, option)
            imgBitmap = bitmap

            base64bitmap = imgBitmap
            base64 = bitmapToString(imgBitmap)

            mSocket.emit("uploadImg", base64)
            bitmap?.let {
                binding.choiceImage.setImageBitmap(bitmap)
            }

            mSocket.on("get_faces", imageSet)
        }

        binding.cameraButton.setOnClickListener {
            val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
            } else {
                val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val file = File.createTempFile(
                    "JPEG_${timeStamp}_",
                    ".jpg",
                    storageDir
                )



                filePath = file.absolutePath
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.example.myapplication.fileprovider",
                    file
                )
                val  intent= Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                requestCameraFileLauncher.launch(intent)
            }
        }

        binding.sendServerButton.setOnClickListener {
            mSocket.emit("getBlurImg", dataFormat.format(currentTime), faceList)
            mSocket.on("get_blur", blurImg)
        }

        binding.clearButton.setOnClickListener {
            faceList.clear()
            itemList.clear()
            val newItemList = arrayListOf<RecyclerViewData>()
            itemList = newItemList

            runOnUiThread {
                listAdapter.notifyDataSetChanged()
                binding.choiceImage.setImageBitmap(null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketHandler.setSocket()
        val mSocket = SocketHandler.getSocket()
        mSocket.off(Socket.EVENT_CONNECT, onConnect)
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError)
        mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        SocketHandler.closeConnection()
    }

    private val onConnect =
        Emitter.Listener { args: Array<Any?>? ->
            if (!isConnected) {
                isConnected = true
                Log.i("Connected", "server-client connected")
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    Toast.makeText(
                        this,
                        "connected",
                        Toast.LENGTH_SHORT
                    ).show()
                }, 0)
            }
        }

    private val onDisconnect = Emitter.Listener { args: Array<Any?>? ->
        Log.i("Disconnected", "server-client disconnected")
        if (isConnected) {
            isConnected = false
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                Toast.makeText(
                    this,
                    "disconnected",
                    Toast.LENGTH_SHORT
                ).show()
            }, 0)
        }
    }

    private val onConnectError =
        Emitter.Listener { args: Array<Any?>? ->
            if (isConnected) {
                Log.e("Disconnected", "socket connection error")
                isConnected = false
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    Toast.makeText(
                        this,
                        "connecting error",
                        Toast.LENGTH_SHORT
                    ).show()
                }, 0)
            }
        }

    @SuppressLint("NotifyDataSetChanged")
    private val imageSet =  Emitter.Listener { args: Array<Any> ->
        val data = args[0] as JSONObject

        try {
            val msg = data.getJSONArray("image")

            for (i in 0..(msg.length() - 1)) {
                val imageBytes = Base64.decode(msg.get(i).toString(), Base64.DEFAULT)
                val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                itemList.add(RecyclerViewData(decodedImage, false))
                Log.e("아이템 수 ", itemList.size.toString())

            }
            runOnUiThread {
                listAdapter.notifyDataSetChanged()
            }


        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private val blurImg = Emitter.Listener { args: Array<Any> ->
        val data = args[0] as JSONObject

        try {
            val msg = data.getString("image")

            val imageBytes = Base64.decode(msg, Base64.DEFAULT)
            val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            runOnUiThread {
                binding.choiceImage.setImageBitmap(decodedImage).let {
                    Toast.makeText(this, "모자이크가 되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun getImageUri(context: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(
            context.getContentResolver(),
            inImage,
            "Title",
            null
        )
        return Uri.parse(path)
    }



    private fun calculateInSampleSize(fileUri: Uri, reqWidth: Int, reqHeight: Int): Int {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        try {
            var inputStream = contentResolver.openInputStream(fileUri)

            //inJustDecodeBounds 값을 true 로 설정한 상태에서 decodeXXX() 를 호출.
            //로딩 하고자 하는 이미지의 각종 정보가 options 에 설정 된다.
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream!!.close()
            inputStream = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        //비율 계산........................
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        //inSampleSize 비율 계산
        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun bitmapToString(bitmap: Bitmap?): String {

        val byteArrayOutputStream = ByteArrayOutputStream()

        bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)

        val byteArray = byteArrayOutputStream.toByteArray()

        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0) {
            if (grantResults[0] == 0) {
                Toast.makeText(this, "카메라 권한 승인", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "카메라 권한 거절", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveFile(fileName: String, mimeType: String, bitmap: Bitmap) {
        var CV = ContentValues()
        CV.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        CV.put(MediaStore.Images.Media.MIME_TYPE, mimeType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            CV.put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, CV)

        if (uri != null) {
            var scriptor = contentResolver.openFileDescriptor(uri, "w")

            if (scriptor != null) {
                val fos = FileOutputStream(scriptor.fileDescriptor)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.close()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    CV.clear()
                    CV.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, CV, null, null)
                }
            }
        }
    }

    fun RandomFileName() : String
    {
        val fineName = SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis())
        return fineName
    }
}