package com.poltevab.binchecker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.poltevab.binchecker.databinding.ActivityMainBinding
import com.poltevab.binchecker.databinding.ActivityMainBinding.inflate
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    val listOfRecentRequests = ArrayList<String>()

    lateinit var adapterRecentHistory: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val requestHistory = getSharedPreferences("RequestHistory", Context.MODE_PRIVATE)
        binding.editText.setText(requestHistory.getString("lastCardNumber", ""))



        for (i in 0..4 )
            requestHistory.getString("VALUE_$i", "")?.let { listOfRecentRequests.add(it) }

        val listViewRecentHistory = findViewById<ListView>(R.id.listViewRecentHistory)
        adapterRecentHistory = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOfRecentRequests)
        listViewRecentHistory.adapter = adapterRecentHistory



        listViewRecentHistory.setOnItemClickListener { adapterView: AdapterView<*>, view2: View, i: Int, l: Long ->
            binding.editText.setText(listOfRecentRequests[i])
        }



        binding.button.setOnClickListener {

            if (binding.editText.text.isNotEmpty()) {
                CoroutineScope(IO).launch {
                    val cardNumber = binding.editText.text.toString().toInt()
                    val output = getOutput(cardNumber)
                    if (output != null) {
                        setTextViewValues(output)
                    }
                    refreshAdapter(listViewRecentHistory, adapterRecentHistory)
                }
            } else  {
                Toast.makeText(this,"Enter card number.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.textViewLatitude.setOnClickListener { //выбивает нахой
            if (binding.textViewCountryLatitude != null && binding.textViewCountryLongitude != null) {
                val uri: String = java.lang.String.format(Locale.ENGLISH, "geo:%f,%f", binding.textViewCountryLatitude.text.toString().toDouble(), binding.textViewCountryLongitude.text.toString().toDouble())
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                this.startActivity(intent)
            }

        }

        binding.textViewLongitude.setOnClickListener { //выбивает нахой
            if (binding.textViewCountryLatitude != null && binding.textViewCountryLongitude != null) {
                val uri: String = java.lang.String.format(Locale.ENGLISH, "geo:%f,%f", binding.textViewCountryLatitude.text.toString().toDouble(), binding.textViewCountryLongitude.text.toString().toDouble())
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                this.startActivity(intent)
            }

        }


    }

    suspend fun refreshAdapter(listView: ListView, adapter: ArrayAdapter<String>) {

        if (binding.editText.text.toString() != listOfRecentRequests[0]) {

            if (listOfRecentRequests.contains(binding.editText.text.toString()))
                listOfRecentRequests.remove(binding.editText.text.toString())
            else
                listOfRecentRequests.removeAt(listOfRecentRequests.lastIndex)
            listOfRecentRequests.add(0, binding.editText.text.toString())
        }
        withContext(Main) {
            adapterRecentHistory.notifyDataSetChanged()
        }

    }

    suspend fun getOutput(cardNumber: Int): ParsedData?  {

        var parsedData: ParsedData? = null
        val requestHistory = getSharedPreferences("RequestHistory", Context.MODE_PRIVATE)

        if (requestHistory.contains("$cardNumber")) {
            val data = requestHistory.getString("$cardNumber", "")
            if (data != null)
                parsedData = parseData(data)


        } else {
            val fetchedData = fetchData(cardNumber)

            if (fetchedData != null) {
                parsedData = parseData(fetchedData)
                //setTextViewValues(parsedData)
                requestHistory.edit().putString("$cardNumber", fetchedData).apply()

            }
        }

       return parsedData

    }


    suspend fun fetchData(cardNumber: Int): String? {

        val url = URL("https://lookup.binlist.net/$cardNumber")
        var fetchedData: String
        val connection: HttpURLConnection
            withContext(IO) {
                connection = url.openConnection() as HttpURLConnection
            }

        when (connection.responseCode) {
            200 -> {
                try {
                    fetchedData = url.readText()
                } catch (e: FileNotFoundException) {
                    return null
                }
            }

            404 -> {
                runOnUiThread {
                    Toast.makeText(applicationContext, "No matching card numbers are found.", Toast.LENGTH_SHORT).show()
                }
                return null
            }

            429 -> {
                runOnUiThread {
                    Toast.makeText(applicationContext, "No more than 10 requests per minute.", Toast.LENGTH_SHORT).show()
                }
                return null
            }

            else -> {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Try another number.\nResponse code: ${connection.responseCode}.", Toast.LENGTH_SHORT).show()
                }
                return null
            }

        }
        return fetchedData
    }

    suspend fun setTextViewValues(parsedData: ParsedData): String {

        withContext(Main) {
            binding.textViewNumberLength.text = parsedData.numberLength.toString()
            binding.textViewNumberLuhn.text = parsedData.numberLuhn.toString()

            binding.textViewScheme.text = parsedData.scheme
            binding.textViewType.text = parsedData.type
            binding.textViewBrand.text = parsedData.brand
            binding.textViewPrepaid.text = parsedData.prepaid.toString()

            binding.textViewCountryNumeric.text = parsedData.countryNumeric
            binding.textViewCountryAlpha2.text = parsedData.countryAlpha2
            binding.textViewCountryName.text = parsedData.countryName
            binding.textViewCountryEmoji.text = parsedData.countryEmoji
            binding.textViewCountryCurrency.text = parsedData.countryCurrency
            binding.textViewCountryLatitude.text = parsedData.countryLatitude.toString()
            binding.textViewCountryLongitude.text = parsedData.countryLongitude.toString()

            binding.textViewBankName.text = parsedData.bankName
            binding.textViewBankUrl.text = parsedData.bankUrl
            binding.textViewBankPhone.text = parsedData.bankPhone
            binding.textViewBankCity.text = parsedData.bankCity

          //  binding.textView.text = getOutputAsArrayList(parsedData).toString()
        }

        return getOutputAsArrayList(parsedData).toString()
    }

    fun getOutputAsArrayList(parsedData: ParsedData): ArrayList<String> {
        val arrayList = ArrayList<String>()

        arrayList.add("length: ${parsedData.numberLength}\n")
        arrayList.add("luhn: ${parsedData.numberLuhn}\n")

        arrayList.add("scheme: ${parsedData.scheme}\n")
        arrayList.add("type: ${parsedData.type}\n")
        arrayList.add("brand: ${parsedData.brand}\n")
        arrayList.add("prepaid: ${parsedData.prepaid}\n")

        arrayList.add("numeric: ${parsedData.countryNumeric}\n")
        arrayList.add("alpha2: ${parsedData.countryAlpha2}\n")
        arrayList.add("name: ${parsedData.countryName}\n")
        arrayList.add("emoji: ${parsedData.countryEmoji}\n")
        arrayList.add("currency: ${parsedData.countryCurrency}\n")
        arrayList.add("latitude: ${parsedData.countryLatitude}\n")
        arrayList.add("longitude: ${parsedData.countryLongitude}\n")

        arrayList.add("name: ${parsedData.bankName}\n")
        arrayList.add("url: ${parsedData.bankUrl}\n")
        arrayList.add("phone: ${parsedData.bankPhone}\n")
        arrayList.add("city: ${parsedData.bankCity}")

        return arrayList

    }

    suspend fun parseData(fetchedData: String): ParsedData {

        val jsonObject = JSONObject(fetchedData)

        return ParsedData(

            jsonObject.optJSONObject("number")?.optInt("length"),
            jsonObject.optJSONObject("number")?.optBoolean("luhn"),

            jsonObject.optString("scheme"),
            jsonObject.optString("type"),
            jsonObject.optString("brand"),
            jsonObject.optBoolean("prepaid"),

            jsonObject.optJSONObject("country")?.optString("numeric"),
            jsonObject.optJSONObject("country")?.optString("alpha2"),
            jsonObject.optJSONObject("country")?.optString("name"),
            jsonObject.optJSONObject("country")?.optString("emoji"),
            jsonObject.optJSONObject("country")?.optString("currency"),
            jsonObject.optJSONObject("country")?.optInt("latitude"),
            jsonObject.optJSONObject("country")?.optInt("longitude"),

            jsonObject.optJSONObject("bank")?.optString("name"),
            jsonObject.optJSONObject("bank")?.optString("url"),
            jsonObject.optJSONObject("bank")?.optString("phone"),
            jsonObject.optJSONObject("bank")?.optString("city")
        )
    }

    fun saveData() {

        val requestHistory = getSharedPreferences("RequestHistory", Context.MODE_PRIVATE)
        requestHistory.edit().putString("lastCardNumber", binding.editText.text.toString()).apply()

        for (item in listOfRecentRequests)
            requestHistory.edit().putString("VALUE_${listOfRecentRequests.indexOf(item)}", item).apply()
    }

    override fun onPause(){
        super.onPause()

        saveData()
    }

    override fun onStop() {
        super.onStop()

        saveData()
    }

    override fun onDestroy() {
        super.onDestroy()

        saveData()
    }

}