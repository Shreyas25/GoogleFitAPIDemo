package com.demo.demogooglefitapi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.demo.demogooglefitapi.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.material.snackbar.Snackbar
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

enum class FitActionRequestCode {
    READ_DATA
}

class MainActivity : AppCompatActivity(), OnSuccessListener<Any> {

    var GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
    private val TAG = "MainActivity"
    private lateinit var fitnessOptions: FitnessOptions
    private lateinit var fitnessDataResponseModel: FitnessDataResponseModel
    private lateinit var binding: ActivityMainBinding

    private val runningQOrLater =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initialization()
        checkPermissions()
    }

    private fun initialization() {
        fitnessDataResponseModel = FitnessDataResponseModel()

        binding.btnLastWeekData.setOnClickListener { v -> requestForHistory() }
    }

    private fun checkPermissions() {
        //Request runtime permissions
        if (permissionApproved()) {
            checkGoogleFitPermission()
        } else {
            requestRuntimePermissions(FitActionRequestCode.READ_DATA)
        }
    }

    private fun checkGoogleFitPermission() {
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .build()


        val googleAccount = getGoogleAccount()

        if (!GoogleSignIn.hasPermissions(googleAccount, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                googleAccount,
                fitnessOptions
            )
        } else {
            startReadingData()
        }
    }

    private fun getGoogleAccount(): GoogleSignInAccount {
        return GoogleSignIn.getAccountForExtension(this, fitnessOptions)
    }

    private fun startReadingData() {
        getTodayData()
    }

    //Fetch data for current day
    private fun getTodayData() {
        Fitness.getHistoryClient(this, getGoogleAccount())
            .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
            .addOnSuccessListener(this)
        Fitness.getHistoryClient(this, getGoogleAccount())
            .readDailyTotal(DataType.TYPE_CALORIES_EXPENDED)
            .addOnSuccessListener(this)
        Fitness.getHistoryClient(this, getGoogleAccount())
            .readDailyTotal(DataType.TYPE_DISTANCE_DELTA)
            .addOnSuccessListener(this)
    }

    private fun requestForHistory() {
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusWeeks(1)
        Log.i(TAG, "Range Start: $startTime")
        Log.i(TAG, "Range End: $endTime")


        /*val cal = Calendar.getInstance()
        cal.time = Date()
        val endTime = cal.timeInMillis
        cal.set(2021, 2, 5)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis*/


        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
            .aggregate(DataType.AGGREGATE_STEP_COUNT_DELTA)
            .aggregate(DataType.TYPE_CALORIES_EXPENDED)
            .aggregate(DataType.AGGREGATE_CALORIES_EXPENDED)
            .aggregate(DataType.TYPE_DISTANCE_DELTA)
            .aggregate(DataType.AGGREGATE_DISTANCE_DELTA)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(), TimeUnit.SECONDS)
            .build()

        Fitness.getHistoryClient(this, getGoogleAccount())
            .readData(readRequest)
            .addOnSuccessListener(this)
    }

    override fun onSuccess(d: Any?) {
        when (d) {
            is DataSet -> {
                var dataSet = d
                dataSet.let {
                    getDataFromDataSet(dataSet)
                }
            }

            is DataReadResponse -> {
                Toast.makeText(this, "Data Read Response", Toast.LENGTH_SHORT).show()
                fitnessDataResponseModel.steps = 0f
                fitnessDataResponseModel.calories = 0f
                fitnessDataResponseModel.distance = 0f

                val dataReadResponse = d as DataReadResponse

                if (dataReadResponse.buckets != null && dataReadResponse.buckets.isNotEmpty()) {
                    val bucketList = dataReadResponse.buckets

                    if (bucketList != null && bucketList.isNotEmpty()) {
                        for (bucket in bucketList) {
                            val stepsDataSet = bucket.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                            getDataFromDataReadResponse(stepsDataSet)
                            val caloriesDataSet = bucket.getDataSet(DataType.TYPE_CALORIES_EXPENDED)
                            getDataFromDataReadResponse(caloriesDataSet)
                            val distanceDataSet = bucket.getDataSet(DataType.TYPE_DISTANCE_DELTA)
                            getDataFromDataReadResponse(distanceDataSet)
                        }
                    }
                }
            }
        }

    }

    private fun getDataFromDataReadResponse(dataSet: DataSet) {
        val dataPoints: List<DataPoint> = dataSet.dataPoints
        for (dataPoint in dataPoints) {
            for (field in dataPoint.dataType.fields) {
                Log.e(TAG, " data manual history : " + dataPoint.originalDataSource.streamName)
                val value = dataPoint.getValue(field).toString().toFloat()
                Log.e(TAG, " data : $value")
                if (field.name == Field.FIELD_STEPS.name) {
                    fitnessDataResponseModel.steps =
                        DecimalFormat("#.##").format(value + fitnessDataResponseModel.steps)
                            .toFloat()
                } else if (field.name == Field.FIELD_CALORIES.name) {
                    fitnessDataResponseModel.calories =
                        DecimalFormat("#.##").format(value + fitnessDataResponseModel.calories)
                            .toFloat()
                } else if (field.name == Field.FIELD_DISTANCE.name) {
                    fitnessDataResponseModel.distance =
                        DecimalFormat("#.##").format(value + fitnessDataResponseModel.distance)
                            .toFloat()
                }
            }
        }

        setFitnessHistoryData(fitnessDataResponseModel)

    }

    private fun setFitnessHistoryData(fitnessDataResponseModel: FitnessDataResponseModel) {
        with(binding) {
            tvStepsHistory.text = fitnessDataResponseModel.steps.toString()
            tvDistanceHistory.text = fitnessDataResponseModel.distance.toString()
            tvCaloriesHistory.text = fitnessDataResponseModel.calories.toString()
        }

        Log.e(TAG, " steps HISTORY: ${fitnessDataResponseModel.steps}")
        Log.e(TAG, " calories HISTORY: ${fitnessDataResponseModel.calories}")
        Log.e(TAG, " distance HISTORY: ${fitnessDataResponseModel.distance}")
    }

    private fun getDataFromDataSet(dataSet: DataSet) {
        val dataPoints = dataSet.dataPoints
        for (dataPoint in dataPoints) {
            Log.e(TAG, " data manual : " + dataPoint.originalDataSource.streamName)

            for (field in dataPoint.dataType.fields) {
//                var value = dataPoint.getValue(field).asString()
                val value = dataPoint.getValue(field).toString().toFloat()
                Log.e(TAG, " data : $value")

                when (field.name) {
                    Field.FIELD_STEPS.name -> {
                        fitnessDataResponseModel.steps =
                            DecimalFormat("#.##").format(value.toDouble()).toFloat()
                    }
                    Field.FIELD_CALORIES.name -> {
                        fitnessDataResponseModel.calories =
                            DecimalFormat("#.##").format(value.toDouble()).toFloat()
                    }
                    Field.FIELD_DISTANCE.name -> {
                        fitnessDataResponseModel.distance =
                            DecimalFormat("#.##").format(value.toDouble()).toFloat()
                    }
                }
            }
        }

        setDataToUI(fitnessDataResponseModel)

    }

    private fun setDataToUI(fitnessDataResponseModel: FitnessDataResponseModel) {
        with(binding) {
            tvSteps.text = fitnessDataResponseModel.steps.toString()
            tvDistance.text = fitnessDataResponseModel.distance.toString()
            tvCalories.text = fitnessDataResponseModel.calories.toString()
        }
        Log.e(TAG, " steps UI: ${fitnessDataResponseModel.steps}")
        Log.e(TAG, " calories UI: ${fitnessDataResponseModel.calories}")
        Log.e(TAG, " distance UI: ${fitnessDataResponseModel.distance}")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE)
            startReadingData()
        else
            Log.e(TAG, " distance : ${fitnessDataResponseModel.distance}")
    }


    //PERMISSIONS
    private fun permissionApproved(): Boolean {
        val approved = if (runningQOrLater) {
            PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            true
        }
        return approved
    }

    private fun requestRuntimePermissions(requestCode: FitActionRequestCode) {
        val shouldProvideRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            )

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        requestCode.let {
            if (shouldProvideRationale) {
                Log.i(TAG, "Displaying permission rationale to provide additional context.")
                Snackbar.make(
                    findViewById(R.id.main_activity_view),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(R.string.ok) {
                        // Request permission
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                            requestCode.ordinal
                        )
                    }
                    .show()
            } else {
                Log.i(TAG, "Requesting permission")
                // Request permission. It's possible this can be auto answered if device policy
                // sets the permission in a given state or the user denied the permission
                // previously and checked "Never ask again".
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    requestCode.ordinal
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when {
            grantResults.isEmpty() -> {
                // If user interaction was interrupted, the permission request
                // is cancelled and you receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
            }
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                // Permission was granted.
                val fitActionRequestCode = FitActionRequestCode.values()[requestCode]
                fitActionRequestCode.let {
                    checkGoogleFitPermission()
                }
            }
            else -> {
                // Permission denied.

                // In this Activity we've chosen to notify the user that they
                // have rejected a core permission for the app since it makes the Activity useless.
                // We're communicating this message in a Snackbar since this is a sample app, but
                // core permissions would typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.

                Snackbar.make(
                    findViewById(R.id.main_activity_view),
                    R.string.permission_denied_explanation,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(R.string.settings) {
                        // Build intent that displays the App settings screen.
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts(
                            "package",
                            "com.google.android.gms.fit.samples.stepcounterkotlin", null
                        )
                        intent.data = uri
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    .show()
            }
        }
    }

}