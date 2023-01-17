package com.poltevab.binchecker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
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
        clearTextView()





        val requestHistory = getSharedPreferences("RequestHistory", Context.MODE_PRIVATE)
        binding.editText.setText(requestHistory.getString("lastCardNumber", ""))



        for (i in 0..3 )
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
                    if (output != null)
                        setTextViewValues(output)
                    refreshAdapter(listViewRecentHistory, adapterRecentHistory)
                }
            } else {
                Toast.makeText(this,"Enter card number.", Toast.LENGTH_SHORT).show()
            }
        }


        binding.cardViewCountry.setOnClickListener {

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


            if (parsedData.numberLength.toString() == "null")
                binding.textViewNumberLength.text = "NO DATA"
            else
                 binding.textViewNumberLength.text = parsedData.numberLength.toString()



            if (parsedData.numberLuhn.toString() == "null")
                binding.textViewNumberLuhn.text = "NO DATA"
            else if (parsedData.numberLuhn.toString() == "true")
                binding.textViewNumberLuhn.text = "YES"
            else
                binding.textViewNumberLuhn.text = "NO"



            if (parsedData.scheme.toString() == "null")
                binding.textViewScheme.text = "NO DATA"
            else
                binding.textViewScheme.text = parsedData.scheme



            if (parsedData.type.toString() == "null")
                binding.textViewType.text = "NO DATA"
            else
                binding.textViewType.text = parsedData.type



            if (parsedData.brand.toString() == "null")
                binding.textViewBrand.text = "NO DATA"
            else
                binding.textViewBrand.text = parsedData.brand



            if (parsedData.prepaid.toString() == "null")
                binding.textViewPrepaid.text = "NO DATA"
            else if (parsedData.prepaid.toString() == "true")
                binding.textViewPrepaid.text = "YES"
            else
                binding.textViewPrepaid.text = "NO"

//            binding.textViewCountryNumeric.text = parsedData.countryNumeric
//            binding.textViewCountryAlpha2.text = parsedData.countryAlpha2

            if (parsedData.countryName.toString() == "null")
                binding.textViewCountryName.text = "NO DATA"
            else
                binding.textViewCountryName.text = parsedData.countryName

            binding.textViewCountryEmoji.text = parsedData.countryEmoji
//            binding.textViewCountryCurrency.text = parsedData.countryCurrency

            if (parsedData.countryLatitude.toString() == "null")
                binding.textViewCountryLatitude.text = "NO DATA"
            else
                binding.textViewCountryLatitude.text = parsedData.countryLatitude.toString()

            if (parsedData.countryLongitude.toString() == "null")
                binding.textViewCountryLongitude.text = "NO DATA"
            else
                binding.textViewCountryLongitude.text = parsedData.countryLongitude.toString()



            if (parsedData.bankName.toString() == "null")
                binding.textViewBankName.text = "NO DATA"
            else
                binding.textViewBankName.text = parsedData.bankName



            if (parsedData.bankPhone.toString() == "null" && !parsedData.bankPhone.toString().startsWith("+")) {
                binding.textViewBankPhone.text = ""
            } else if (parsedData.bankPhone.toString().startsWith("+")){
                binding.textViewBankPhone.text = parsedData.bankPhone
            } else {
                val countryPhoneCode = getCountryPhoneCode(parsedData.countryAlpha2.toString())
                val output = "$countryPhoneCode ${parsedData.bankPhone}"
                binding.textViewBankPhone.text = output
            }



            binding.textViewBankUrl.text = parsedData.bankUrl
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

    fun getCountryPhoneCode(countryCode: String): String {
        val countryCodes = mapOf<String, String>(
            "AF" to "+93",
            "AL" to "+355",
            "DZ" to "+213",
            "AS" to "+1-684",
            "AD" to "+376",
            "AO" to "+244",
            "AI" to "+1-264",
            "AQ" to "+672",
            "AG" to "+1-268",
            "AR" to "+54",
            "AM" to "+374",
            "AW" to "+297",
            "AU" to "+61",
            "AT" to "+43",
            "AZ" to "+994",
            "BS" to "+1-242",
            "BH" to "+973",
            "BD" to "+880",
            "BB" to "+1-246",
            "BY" to "+375",
            "BE" to "+32",
            "BZ" to "+501",
            "BJ" to "+229",
            "BM" to "+1-441",
            "BT" to "+975",
            "BO" to "+591",
            "BA" to "+387",
            "BW" to "+267",
            "BR" to "+55",
            "IO" to "+246",
            "VG" to "+1-284",
            "BN" to "+673",
            "BG" to "+359",
            "BF" to "+226",
            "BI" to "+257",
            "KH" to "+855",
            "CM" to "+237",
            "CA" to "+1",
            "CV" to "+238",
            "KY" to "+1-345",
            "CF" to "+236",
            "TD" to "+235",
            "CL" to "+56",
            "CN" to "+86",
            "CX" to "+61",
            "CC" to "+61",
            "CO" to "+57",
            "KM" to "+269",
            "CK" to "+682",
            "CR" to "+506",
            "HR" to "+385",
            "CU" to "+53",
            "CW" to "+599",
            "CY" to "+357",
            "CZ" to "+420",
            "CD" to "+243",
            "DK" to "+45",
            "DJ" to "+253",
            "DM" to "+1-767",
            "DO" to "+1-809",
            "TL" to "+670",
            "EC" to "+593",
            "EG" to "+20",
            "SV" to "+503",
            "GQ" to "+240",
            "ER" to "+291",
            "EE" to "+372",
            "ET" to "+251",
            "FK" to "+500",
            "FO" to "+298",
            "FJ" to "+679",
            "FI" to "+358",
            "FR" to "+33",
            "PF" to "+689",
            "GA" to "+241",
            "GM" to "+220",
            "GE" to "+995",
            "DE" to "+49",
            "GH" to "+233",
            "GI" to "+350",
            "GR" to "+30",
            "GL" to "+299",
            "GD" to "+1-473",
            "GU" to "+1-671",
            "GT" to "+502",
            "GG" to "+44-1481",
            "GN" to "+224",
            "GW" to "+245",
            "GY" to "+592",
            "HT" to "+509",
            "HN" to "+504",
            "HK" to "+852",
            "HU" to "+36",
            "IS" to "+354",
            "IN" to "+91",
            "ID" to "+62",
            "IR" to "+98",
            "IQ" to "+964",
            "IE" to "+353",
            "IM" to "+44-1624",
            "IL" to "+972",
            "IT" to "+39",
            "CI" to "+225",
            "JM" to "+1-876",
            "JP" to "+81",
            "JE" to "+44-1534",
            "JO" to "+962",
            "KZ" to "+7",
            "KE" to "+254",
            "KI" to "+686",
            "XK" to "+383",
            "KW" to "+965",
            "KG" to "+996",
            "LA" to "+856",
            "LV" to "+371",
            "LB" to "+961",
            "LS" to "+266",
            "LR" to "+231",
            "LY" to "+218",
            "LI" to "+423",
            "LT" to "+370",
            "LU" to "+352",
            "MO" to "+853",
            "MK" to "+389",
            "MG" to "+261",
            "MW" to "+265",
            "MY" to "+60",
            "MV" to "+960",
            "ML" to "+223",
            "MT" to "+356",
            "MH" to "+692",
            "MR" to "+222",
            "MU" to "+230",
            "YT" to "+262",
            "MX" to "+52",
            "FM" to "+691",
            "MD" to "+373",
            "MC" to "+377",
            "MN" to "+976",
            "ME" to "+382",
            "MS" to "+1-664",
            "MA" to "+212",
            "MZ" to "+258",
            "MM" to "+95",
            "NA" to "+264",
            "NR" to "+674",
            "NP" to "+977",
            "NL" to "+31",
            "AN" to "+599",
            "NC" to "+687",
            "NZ" to "+64",
            "NI" to "+505",
            "NE" to "+227",
            "NG" to "+234",
            "NU" to "+683",
            "KP" to "+850",
            "MP" to "+1-670",
            "NO" to "+47",
            "OM" to "+968",
            "PK" to "+92",
            "PW" to "+680",
            "PS" to "+970",
            "PA" to "+507",
            "PG" to "+675",
            "PY" to "+595",
            "PE" to "+51",
            "PH" to "+63",
            "PN" to "+64",
            "PL" to "+48",
            "PT" to "+351",
            "PR" to "+1-787",
            "QA" to "+974",
            "CG" to "+242",
            "RE" to "+262",
            "RO" to "+40",
            "RU" to "+7",
            "RW" to "+250",
            "BL" to "+590",
            "SH" to "+290",
            "KN" to "+1-869",
            "LC" to "+1-758",
            "MF" to "+590",
            "PM" to "+508",
            "VC" to "+1-784",
            "WS" to "+685",
            "SM" to "+378",
            "ST" to "+239",
            "SA" to "+966",
            "SN" to "+221",
            "RS" to "+381",
            "SC" to "+248",
            "SL" to "+232",
            "SG" to "+65",
            "SX" to "+1-721",
            "SK" to "+421",
            "SI" to "+386",
            "SB" to "+677",
            "SO" to "+252",
            "ZA" to "+27",
            "KR" to "+82",
            "SS" to "+211",
            "ES" to "+34",
            "LK" to "+94",
            "SD" to "+249",
            "SR" to "+597",
            "SJ" to "+47",
            "SZ" to "+268",
            "SE" to "+46",
            "CH" to "+41",
            "SY" to "+963",
            "TW" to "+886",
            "TJ" to "+992",
            "TZ" to "+255",
            "TH" to "+66",
            "TG" to "+228",
            "TK" to "+690",
            "TO" to "+676",
            "TT" to "+1-868",
            "TN" to "+216",
            "TR" to "+90",
            "TM" to "+993",
            "TC" to "+1-649",
            "TV" to "+688",
            "VI" to "+1-340",
            "UG" to "+256",
            "UA" to "+380",
            "AE" to "+971",
            "GB" to "+44",
            "US" to "+1",
            "UY" to "+598",
            "UZ" to "+998",
            "VU" to "+678",
            "VA" to "+379",
            "VE" to "+58",
            "VN" to "+84",
            "WF" to "+681",
            "EH" to "+212",
            "YE" to "+967",
            "ZM" to "+260",
            "ZW" to "+263"
        )

        return countryCodes[countryCode].toString()
    }

    fun clearTextView() {
        binding.textViewNumberLength.text = ""
        binding.textViewNumberLuhn.text = ""
        binding.textViewScheme.text = ""
        binding.textViewType.text = ""
        binding.textViewBrand.text = ""
        binding.textViewPrepaid.text = ""
        binding.textViewCountryEmoji.text = ""
//        binding.textViewCountryAlpha2.text = ""
//        binding.textViewCountryNumeric.text = ""
//        binding.textViewCountryCurrency.text = ""
        binding.textViewCountryName.text = ""
        binding.textViewBankName.text = ""
        binding.textViewBankUrl.text = ""
        binding.textViewBankPhone.text = ""
        binding.textViewBankCity.text = ""
        binding.textViewCountryLatitude.text = ""
        binding.textViewCountryLongitude.text = ""
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