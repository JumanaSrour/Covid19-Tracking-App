package com.example.covid19trackingapp

import android.os.Bundle
import android.util.Log
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(Companion.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService = retrofit.create(CovidService::class.java)
        // fetch the national data
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {

                Log.e("---", "onResponse: $response",)
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.e("---", "Did not receive a valid response body")
                    return
                }
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i("---", "Update graph with national data")
                // update graph with national data
                updateDisplayWithData(nationalDailyData)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e("---", "onFailure: $t ",)
            }
        })

        // fetch the state data
        covidService.getStatesData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.e("---", "onResponse: $response",)
                val statesData = response.body()
                if (statesData == null) {
                    Log.e("---", "Did not receive a valid response body")
                    return
                }
                perStateDailyData = statesData.reversed().groupBy { it.state }
                Log.i("---", "Update spinner with state data")
                // Update spinner with state names
                updateSpinnerWithStateData(perStateDailyData.keys)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e("---", "onFailure: $t ",)
            }
        })
    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0, Companion.ALL_STATES)

        // Add state list as data source for the spinner
//        spinnerSelect.attachDataSource(stateAbbreviationList)
//        spinnerSelect.setOnSpinnerItemSelectedListener{ parent, _, position, _ ->
//            val selectedState = parent.getItemAtPosition(position) as String
//            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
//            updateDisplayWithData(selectedData)
//        }
    }

    private fun setupEventListeners() {
        // Add listener for user scrubbing on chart
        sparkView.isScrubEnabled = true
        sparkView.setScrubListener { itemData ->
            if (itemData is CovidData) {
                updateInfoForDate(itemData)
            }
        }
        // Respond to radio button selected events
        rgTimeSelection.setOnCheckedChangeListener { _, checkedId ->
            adapter.daysAgo = when (checkedId) {
                R.id.rbWeek -> TimeScale.WEEK
                R.id.rbMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        rgMetricsSelection.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.rbNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.rbDeath -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        // Update chart color
        val colorRes = when (metric) {
            Metric.POSITIVE -> R.color.positive
            Metric.NEGATIVE -> R.color.negative
            Metric.DEATH -> R.color.death
        }

        @ColorInt val colorInt = ContextCompat.getColor(this, colorRes)
        sparkView.lineColor = colorInt
        tvMetricLabel.setTextColor(colorInt)

        // Update metric on the adapter
        adapter.metric = metric
        adapter.notifyDataSetChanged()

        // Reset number and date shown in bottom text views
        updateInfoForDate(currentlyShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
        // Create a new SparkAdapter with the data
        adapter = CovidSparkAdapter(dailyData)
        sparkView.adapter = adapter
        // Update dario buttons to select the positive cases and max time by default
        rbPositive.isChecked = true
        rbMax.isChecked = true
        // Display metric for the most recent data
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when (adapter.metric) {
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
        tvMetricLabel.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)
    }

    companion object {
        private const val BASE_URL = "https://covidtracking.com/api/v1/"
        private const val ALL_STATES: String = "All (Nationwide)"
    }
}
