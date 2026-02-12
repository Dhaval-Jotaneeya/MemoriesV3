package com.dj.memoriesv3

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dj.memoriesv3.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.idMaterialToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load existing values
        binding.edtOrgName.setText(sharedPreferences.getString(Constants.KEY_ORG_NAME, ""))
        binding.idEdtToken.setText(sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, ""))

        binding.idBtnSaveSettings.setOnClickListener {
            val orgName = binding.edtOrgName.text.toString().trim()
            val token = binding.idEdtToken.text.toString().trim()

            if (orgName.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "Please enter both Org Name and Token", Toast.LENGTH_SHORT).show()
            } else {
                sharedPreferences.edit().apply {
                    putString(Constants.KEY_ORG_NAME, orgName)
                    putString(Constants.KEY_GITHUB_TOKEN, token)
                    apply()
                }
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                finish() // Go back to MainActivity
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}