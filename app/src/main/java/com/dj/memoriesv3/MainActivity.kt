package com.dj.memoriesv3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.dj.memoriesv3.databinding.UiRepositoriesBinding
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: UiRepositoriesBinding
    private var fullRepoList: List<String> = emptyList()

    // Optimize: Initialize Retrofit service once rather than on every resume
    private val service: GitHubService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = UiRepositoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.idMaterialToolbar)

        checkSettings()

        binding.btnSettings.visibility = View.GONE
        setupSearch()

        binding.idCreateNewRepo.setOnClickListener { view ->
            Snackbar.make(view, "Create new repository action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        binding.idListOfRepo.setOnItemClickListener { parent, _, position, _ ->
            val repoName = parent.getItemAtPosition(position) as String
            val intent = Intent(this, GalleryActivity::class.java)
            intent.putExtra("REPO_NAME", repoName)
            startActivity(intent)
        }
    }

    private fun setupSearch() {
        binding.editTextText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterRepositories(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_repositories, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun filterRepositories(query: String) {
        val filteredList = if (query.isEmpty()) fullRepoList else fullRepoList.filter { it.contains(query, true) }
        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, filteredList)
        binding.idListOfRepo.adapter = adapter
    }

    private fun checkSettings() {
        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, null)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null)

        if (orgName.isNullOrEmpty() || token.isNullOrEmpty()) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadRepositories(orgName: String, token: String) {
        binding.idProgressBar.visibility = View.VISIBLE

        // Start fetching from page 1 with an empty list
        fetchRepoPage(orgName, token, 1, mutableListOf())
    }

    private fun fetchRepoPage(orgName: String, token: String, page: Int, allRepos: MutableList<GitHubRepo>) {
        val call = service.getOrgRepos(orgName, "Bearer $token", 100, page)

        call.enqueue(object : Callback<List<GitHubRepo>> {
            override fun onResponse(
                call: Call<List<GitHubRepo>>,
                response: Response<List<GitHubRepo>>
            ) {
                if (response.isSuccessful) {
                    val repos = response.body() ?: emptyList()
                    allRepos.addAll(repos)

                    if (repos.size == 100) {
                        // If we got a full page, there might be more. Fetch next page.
                        fetchRepoPage(orgName, token, page + 1, allRepos)
                    } else {
                        // We have all repositories
                        binding.idProgressBar.visibility = View.GONE
                        fullRepoList = allRepos.sortedByDescending { it.updatedAt }.map { it.name }
                        filterRepositories(binding.editTextText.text.toString())
                    }
                } else {
                    binding.idProgressBar.visibility = View.GONE
                    Snackbar.make(binding.root, "Error: ${response.code()}", Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<List<GitHubRepo>>, t: Throwable) {
                binding.idProgressBar.visibility = View.GONE
                Snackbar.make(binding.root, "Failure: ${t.message}", Snackbar.LENGTH_LONG).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Refresh if we came back from settings
        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, null)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null)
        if (!orgName.isNullOrEmpty() && !token.isNullOrEmpty()) {
            loadRepositories(orgName, token)
        }
    }
}