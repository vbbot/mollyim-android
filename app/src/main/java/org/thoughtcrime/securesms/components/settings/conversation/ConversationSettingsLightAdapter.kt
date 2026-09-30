/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.ClickPreference
import org.thoughtcrime.securesms.components.settings.DividerPreference
import org.thoughtcrime.securesms.components.settings.ExternalLinkPreference
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.SectionHeaderPreference
import org.thoughtcrime.securesms.components.settings.TextPreference
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

/**
 * Conversation-settings-only renderer for standard DSL rows.
 *
 * The DSL models remain the behavior owners: this adapter changes their presentation only, and is
 * intentionally not installed globally. Custom profile/member/media rows continue to register their
 * own dedicated holders in [ConversationSettingsFragment].
 */
class ConversationSettingsLightAdapter : MappingAdapter() {
  init {
    registerFactory(ClickPreference::class.java, LayoutFactory(::ClickHolder, R.layout.conversation_settings_light_row))
    registerFactory(TextPreference::class.java, LayoutFactory(::TextHolder, R.layout.conversation_settings_light_row))
    registerFactory(ExternalLinkPreference::class.java, LayoutFactory(::ExternalLinkHolder, R.layout.conversation_settings_light_row))
    registerFactory(DividerPreference::class.java, LayoutFactory(::DividerHolder, R.layout.conversation_settings_light_divider))
    registerFactory(SectionHeaderPreference::class.java, LayoutFactory(::SectionHeaderHolder, R.layout.conversation_settings_light_header))
  }
}

private abstract class BaseRowHolder<T : PreferenceModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {
  private val icon: ImageView = itemView.findViewById(R.id.icon)
  private val title: TextView = itemView.findViewById(R.id.title)
  private val summary: TextView = itemView.findViewById(R.id.summary)

  override fun bind(model: T) {
    val drawable = model.icon?.resolve(context)?.mutate()?.apply {
      colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, android.R.color.white), PorterDuff.Mode.SRC_IN)
    }
    icon.setImageDrawable(drawable)
    icon.visible = drawable != null

    title.text = model.title?.resolve(context)
    title.visible = model.title != null
    summary.text = model.summary?.resolve(context)
    summary.visible = model.summary != null

    itemView.alpha = if (model.isEnabled) 1f else DISABLED_ALPHA
    itemView.isEnabled = model.isEnabled
    title.isEnabled = model.isEnabled
    summary.isEnabled = model.isEnabled
  }

  companion object {
    private const val DISABLED_ALPHA = 0.45f
  }
}

private class ClickHolder(itemView: View) : BaseRowHolder<ClickPreference>(itemView) {
  override fun bind(model: ClickPreference) {
    super.bind(model)
    // PreferenceViewHolder's legacy contract keeps disabled rows actionable so they can explain why.
    itemView.isEnabled = true
    itemView.isClickable = true
    itemView.setOnClickListener {
      if (model.isEnabled) model.onClick() else model.onDisabledClicked?.invoke()
    }
    itemView.setOnLongClickListener {
      if (model.isEnabled) model.onLongClick?.invoke() ?: false else false
    }
  }
}

private class TextHolder(itemView: View) : BaseRowHolder<TextPreference>(itemView) {
  override fun bind(model: TextPreference) {
    super.bind(model)
    itemView.setOnClickListener(null)
    itemView.setOnLongClickListener(null)
  }
}

private class ExternalLinkHolder(itemView: View) : BaseRowHolder<ExternalLinkPreference>(itemView) {
  override fun bind(model: ExternalLinkPreference) {
    super.bind(model)
    itemView.setOnClickListener {
      CommunicationActions.openBrowserLink(context, context.getString(model.linkId))
    }
  }
}

private class DividerHolder(itemView: View) : MappingViewHolder<DividerPreference>(itemView) {
  override fun bind(model: DividerPreference) = Unit
}

private class SectionHeaderHolder(itemView: View) : MappingViewHolder<SectionHeaderPreference>(itemView) {
  private val title: TextView = itemView.findViewById(R.id.section_header)
  private val icon: ImageView = itemView.findViewById(R.id.icon_end)

  override fun bind(model: SectionHeaderPreference) {
    title.text = model.title.resolve(context).toString().uppercase()
    val drawable = model.iconEnd?.resolve(context)?.mutate()?.apply {
      colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, android.R.color.white), PorterDuff.Mode.SRC_IN)
    }
    icon.setImageDrawable(drawable)
    icon.visible = drawable != null
    icon.setOnClickListener(if (model.onClick == null) null else View.OnClickListener { model.onClick.invoke() })
    itemView.setOnClickListener(if (model.onClick == null) null else View.OnClickListener { model.onClick.invoke() })
  }
}
