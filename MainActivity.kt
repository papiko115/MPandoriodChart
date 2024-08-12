package com.example.gurahu

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


class MainActivity : AppCompatActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            val today = LocalDate.now() //現在の日付取得
            val day1 = LocalDate.parse("2024-06-01")//比較する日付
            val diff = today.compareTo(day1) //日付の差の計算

// グラフに表示するデータのリスト(ここを増やすと表示する商品の数が増える)メモリに関しては自動的に変わる
            val entries = listOf(
                BarEntry(1f, 90f),  // 1月の売上
                BarEntry(2f, 7f) ,  // 2月の売上
                BarEntry(3f,30f),
                BarEntry(4f,30f),
                BarEntry(5f,30f)
                // 他の月も同様に追加可能
            )

// データセットの作成(ここを増やすと左下のデータの説明の種類が増える。消費期限しか出さないし一つでもいいかも)
            val dataSet = BarDataSet(entries, "残りの消費期限日数").apply{
                valueTextSize = 16f  // ラベルの文字サイズを設定
                valueTextColor = Color.RED  // ラベルの文字色を設定
            }

// グラフにデータをセット
            val barChart: BarChart = findViewById(R.id.barChart)
            barChart.data = BarData(dataSet)

// アニメーションの追加（ｙ軸にアニメーション）
            barChart.animateY(2000)  // 縦方向のアニメーションを2秒で実行
//縦軸のラベルを変更
            barChart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()} 日"
                }
            }

            // 右下のDescription Labelを非表示
            barChart.description.isEnabled = false

// ------ X軸------
            // x軸のラベルを下に表示
            barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            // ------ Y軸右側 ------
// Y軸右側ラベルを非表示
            barChart.axisRight.isEnabled = false

// グラフの更新
            barChart.invalidate()

        }

}


