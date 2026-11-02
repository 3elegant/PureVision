package com.deenelife.purevison

import android.os.Build
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deenelife.purevison.data.AppPreferences
import com.deenelife.purevison.databinding.ActivityFaqBinding
import com.deenelife.purevison.databinding.ItemFaqBinding
import java.util.Locale

class FaqActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFaqBinding
    private val preferences by lazy { AppPreferences.getInstance(this) }

    data class FaqItem(
        val questionRes: Int,
        val answerRes: Int,
        var isExpanded: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        super.onCreate(savedInstanceState)
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarFaq.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val faqList = listOf(
            FaqItem(R.string.faq_q1, R.string.faq_a1, isExpanded = true),
            FaqItem(R.string.faq_q2, R.string.faq_a2),
            FaqItem(R.string.faq_q3, R.string.faq_a3),
            FaqItem(R.string.faq_q4, R.string.faq_a4),
            FaqItem(R.string.faq_q5, R.string.faq_a5),
            FaqItem(R.string.faq_q6, R.string.faq_a6),
            FaqItem(R.string.faq_q7, R.string.faq_a7),
            FaqItem(R.string.faq_q8, R.string.faq_a8),
            FaqItem(R.string.faq_q9, R.string.faq_a9),
            FaqItem(R.string.faq_q10, R.string.faq_a10)
        )

        binding.recyclerFaq.layoutManager = LinearLayoutManager(this)
        binding.recyclerFaq.adapter = FaqAdapter(faqList, binding.recyclerFaq)
    }

    private class FaqAdapter(
        private val items: List<FaqItem>,
        private val recyclerView: RecyclerView
    ) : RecyclerView.Adapter<FaqAdapter.FaqViewHolder>() {

        inner class FaqViewHolder(val binding: ItemFaqBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqViewHolder {
            val binding = ItemFaqBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return FaqViewHolder(binding)
        }

        override fun onBindViewHolder(holder: FaqViewHolder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context

            holder.binding.tvFaqQuestion.text = context.getString(item.questionRes)
            holder.binding.tvFaqAnswer.text = context.getString(item.answerRes)

            holder.binding.layoutAnswer.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            holder.binding.ivFaqChevron.rotation = if (item.isExpanded) 180f else 0f

            holder.binding.layoutQuestionHeader.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    TransitionManager.beginDelayedTransition(recyclerView)
                    item.isExpanded = !item.isExpanded
                    notifyItemChanged(pos)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun loadLocale() {
        val langCode = preferences.getLanguage()
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Locale.forLanguageTag(langCode)
        } else {
            @Suppress("DEPRECATION")
            Locale(langCode)
        }
        Locale.setDefault(locale)
        val res = resources
        val config = res.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
        @Suppress("DEPRECATION")
        baseContext.resources.updateConfiguration(config, res.displayMetrics)
        @Suppress("DEPRECATION")
        applicationContext.resources.updateConfiguration(config, res.displayMetrics)
    }
}
