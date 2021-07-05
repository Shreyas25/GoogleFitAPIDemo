package com.demo.demogooglefitapi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.demo.demogooglefitapi.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.material.snackbar.Snackbar
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.TimeUnit


/*Reference :
# https://developers.google.com/fit/android/api-client-example
# https://developers.google.com/fit/rest/v1/get-started?hl=en_US
# https://developers.google.com/oauthplayground/

 */


enum class FitActionRequestCode {
    READ_DATA
}

class MainActivity : AppCompatActivity() {

    var GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
    private val TAG = "MainActivity"
    private lateinit var fitnessOptions: FitnessOptions
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

        binding.btnFetchData.setOnClickListener { v ->
            subscribeStepCount() // Alternate approach
//            readHistoricStepCount()
        }
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
            .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEIGHT, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
            .build()


        val googleAccount = getGoogleAccount()

        if (!GoogleSignIn.hasPermissions(googleAccount, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                googleAccount,
                fitnessOptions
            )
        }
    }

    private fun getGoogleAccount(): GoogleSignInAccount {
        return GoogleSignIn.getAccountForExtension(this, fitnessOptions)
    }

    //Alternate Approach
    private fun subscribeStepCount() {
        Fitness.getRecordingClient(this, getGoogleAccount())
            .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE);

        readHistoricStepCount()
    }

    private fun readHistoricStepCount() {
        // Invoke the History API to fetch the data with the query
        Fitness.getHistoryClient(this, getGoogleAccount())
            .readData(queryFitnessData())
            .addOnSuccessListener { dataReadResponse -> printData(dataReadResponse) }
            .addOnFailureListener { e ->
                Log.e(
                    TAG,
                    "There was a problem reading the historic data.",
                    e
                );
            }

    }

    private fun queryFitnessData(): DataReadRequest {

        // As the data fetched does no match with Google Fit App data,
        // below code is used as a workaround for steps
        val ESTIMATED = DataSource.Builder()
            .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
            .setType(DataSource.TYPE_DERIVED)
            .setStreamName("estimated_steps")
            .setAppPackageName("com.google.android.gms")
            .build()

        //Joda time
        /*val dt = DateTime()
        val endTime = dt.millis
        val startTime = dt.minusWeeks(1).millis*/

        val cal = Calendar.getInstance()
        val _endTime = cal.time
        cal.time = Date()
        val endTime = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -30)
        val _startTime = cal.time
        val startTime = _startTime.time

        Log.i(TAG, "START TIME (millis): $startTime")
        Log.i(TAG, "END TIME (millis): $endTime")

        return DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build();
    }

    private fun printData(dataReadResult: DataReadResponse) {
        val result = StringBuilder()

        dataReadResult?.let { dataReadResult ->
            if (dataReadResult.buckets.size > 0) {
                Log.i(
                    TAG,
                    "Number of returned buckets of DataSets is: " + dataReadResult.buckets.size
                )

                for (bucket in dataReadResult.buckets) {
                    val dataSets = bucket.dataSets
                    for (dataSet in dataSets) {
                        result.append(formatDataSet(dataSet))
                    }
                }
            } else if (dataReadResult.dataSets.size > 0) {
                Log.i(
                    TAG,
                    "Number of returned DataSets is: " + dataReadResult.dataSets.size
                )
                for (dataSet in dataReadResult.dataSets) {
                    result.append(formatDataSet(dataSet))
                }
            }
        }

        showData(result)

    }

    private fun showData(result: StringBuilder) {
        binding.tvStepsRecord.text = result.toString()
    }

    private fun formatDataSet(dataSet: DataSet): String {
        val result = java.lang.StringBuilder()
        dataSet.let { dataSet ->
            for (dp in dataSet.dataPoints) {
                val sDT = DateTime(dp.getStartTime(TimeUnit.MILLISECONDS))
                val eDT = DateTime(dp.getEndTime(TimeUnit.MILLISECONDS))

                result.append(
                    String.format(
                        Locale.ENGLISH,
                        "%s %s %s to %s %s %s\n",
                        sDT.toLocalDate(),
                        sDT.dayOfWeek().asShortText,
                        sDT.toLocalTime().toString("HH:mm"),
                        eDT.toLocalDate(),
                        eDT.dayOfWeek().asShortText,
                        eDT.toLocalTime().toString("HH:mm")
                    )
                )

                result.append(
                    String.format(
                        Locale.ENGLISH,
                        "%s: %s %s\n",
                        sDT.dayOfWeek().asShortText,
                        dp.getValue(dp.dataType.fields[0]).toString(),
                        dp.dataType.fields[0].name
                    )
                )
            }
        }

        return result.toString()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE)
            subscribeStepCount()
        else
            Log.e(TAG, " resultCode : $resultCode")
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
                            arrayOf(
                                Manifest.permission.ACTIVITY_RECOGNITION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
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
                    arrayOf(
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
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
                            "com.demo.demogooglefitapi", null
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