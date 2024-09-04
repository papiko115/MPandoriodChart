package com.example.dbsyoki

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Color.*
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat





class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: ProductDatabaseHelper
    private lateinit var chart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // パーミッションリクエストの実行
        requestNotificationPermission(this)
        // WorkManagerの制約とスケジュールの設定
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // バッテリーが低くない時に実行
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ExpirationCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)



        // SQLiteデータベースのヘルパーを初期化
        dbHelper = ProductDatabaseHelper(this)

        // BarChartの初期化
        chart = findViewById(R.id.barchart)

        // データベースからすべての商品を取得してグラフに表示
        val products = dbHelper.getAllProducts()

        // データをグラフに変換して表示
        setUpBarChart(products)

    }

    private fun setUpBarChart(products: List<Product>) {
        val entries = ArrayList<BarEntry>()

        // データをEntryに変換（消費期限までの日数をY値として使用）
        products.forEachIndexed { index, product ->
            val daysUntilExpiration = ChronoUnit.DAYS.between(LocalDate.now(), product.expirationDate).toFloat()
            entries.add(BarEntry(index.toFloat(), daysUntilExpiration))
        }

        // BarDataSetの作成
        val dataSet = BarDataSet(entries, "消費期限までの日数")
        dataSet.color = BLUE

        // BarDataの作成
        val barData = BarData(dataSet)
        barData.barWidth = 0.9f // 棒の幅の調整

        // グラフの設定
        chart.data = barData
        chart.setFitBars(true) // 棒が完全に表示されるようにする
        chart.description.isEnabled = false // グラフの説明を非表示
        chart.setDrawGridBackground(false) // グリッド背景を非表示
        chart.animateY(1000) // グラフのアニメーション
        chart.invalidate() // グラフの更新
    }
}

class ProductDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "products.db"
        private const val DATABASE_VERSION = 5
        private const val TABLE_PRODUCTS = "products"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_EXPIRATION_DATE = "expirationDate"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // テーブルの作成
        val createTable = """
            CREATE TABLE $TABLE_PRODUCTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_EXPIRATION_DATE TEXT
            )
        """
        db.execSQL(createTable)

        // 初期データの挿入
        insertInitialData(db)
    }

    // 初期データを挿入するメソッド
    private fun insertInitialData(db: SQLiteDatabase) {
        val initialProducts = listOf(
            Pair("食料", LocalDate.of(2024, 8,30 )),
            Pair("衣服", LocalDate.of(2024, 9, 4)),
            Pair("食料", LocalDate.of(2024, 9, 10))
        )

        for (product in initialProducts) {
            val values = ContentValues().apply {
                put(COLUMN_NAME, product.first)
                put(COLUMN_EXPIRATION_DATE, product.second.toString())
            }
            db.insert(TABLE_PRODUCTS, null, values)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PRODUCTS")
        onCreate(db)
    }

    // すべての商品の取得メソッド
    fun getAllProducts(): List<Product> {
        val products = mutableListOf<Product>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_PRODUCTS,
            arrayOf(COLUMN_ID, COLUMN_NAME, COLUMN_EXPIRATION_DATE),
            null, null, null, null, null
        )

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
                val expirationDate = LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPIRATION_DATE)))
                products.add(Product(id, name, expirationDate))
            } while (cursor.moveToNext())
        }

        cursor.close()
        return products
    }

}

// 商品のデータクラス
data class Product(val id: Int, val name: String, val expirationDate: LocalDate)

class ExpirationCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dbHelper = ProductDatabaseHelper(applicationContext)
        val products = dbHelper.getAllProducts()
        val currentDate = LocalDate.now()

        // 消費期限が3日以内の商品の通知
        for (product in products) {
            val daysUntilExpiration = ChronoUnit.DAYS.between(currentDate, product.expirationDate)
            if (daysUntilExpiration in 0..3) { // 0日から3日以内に期限切れになる商品
                sendNotification(
                    applicationContext,
                    "消費期限が近づいています",
                    "${product.name}の消費期限が${daysUntilExpiration}日後です"
                )
            }
        }

        return Result.success()
    }
}

fun sendNotification(context: Context, title: String, message: String) {
    val channelId = "expiration_notifications"
    val notificationId = 1

    // NotificationChannelの作成（Android 8.0以降が必要）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Expiration Notifications"
        val descriptionText = "Notifications for products nearing expiration"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }

        // NotificationManagerの取得とチャンネルの登録
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.createNotificationChannel(channel)
    }

    // 通知の表示前にパーミッションを確認
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        // パーミッションが許可されている場合、通知を表示
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_background) // 適切なアイコンに変更
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // 通知の表示
        NotificationManagerCompat.from(context).apply {
            notify(notificationId, builder.build())
        }
    } else {
        // パーミッションがない場合、ユーザーにリクエスト
        requestNotificationPermission(context)
    }
}

fun requestNotificationPermission(context: Context) {
    // アクティビティコンテキストが必要な場合があるので、その点に注意
    if (context is MainActivity) {
        val requestPermissionLauncher = context.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // パーミッションが許可された場合
                sendNotification(context, "通知の設定", "アクセスの許可を確認しました")
            } else {
                // パーミッションが拒否された場合の処理

                // 例えば、ユーザーに通知が必要であることを説明する
            }
        }

        // パーミッションリクエストの実行
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
