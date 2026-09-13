/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * A flavor of [InsetAwareConstraintLayout] that allows "replacing" the keyboard with our
 * own input fragment.
 */
class InputAwareConstraintLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : InsetAwareConstraintLayout(context, attrs, defStyleAttr) {

  private var inputId: Int? = null
  private var input: Fragment? = null
  private var inputPresentation: Presentation? = null
  private var activeContainerId: Int? = null
  private var wasKeyboardVisibleBeforeToggle: Boolean = false
  private val listeners: MutableSet<Listener> = mutableSetOf()

  val isInputShowing: Boolean
    get() = input != null

  lateinit var fragmentManager: FragmentManager

  fun addInputListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeInputListener(listener: Listener) {
    listeners.remove(listener)
  }

  fun showSoftkey(editText: EditText) {
    ViewUtil.focusAndShowKeyboard(editText)
    hideInput(resetKeyboardGuideline = false)
  }

  fun hideAll(imeTarget: EditText) {
    wasKeyboardVisibleBeforeToggle = false
    ViewUtil.hideKeyboard(context, imeTarget)
    hideInput(resetKeyboardGuideline = true)
  }

  fun runAfterAllHidden(imeTarget: EditText, onHidden: () -> Unit) {
    if (isInputShowing || isKeyboardShowing) {
      val listener = object : Listener, KeyboardStateListener {
        override fun onInputHidden() {
          onHidden()
          removeInputListener(this)
          removeKeyboardStateListener(this)
        }

        override fun onKeyboardHidden() {
          onHidden()
          removeInputListener(this)
          removeKeyboardStateListener(this)
        }

        override fun onInputShown(fragmentCreatorId: Int) = Unit
        override fun onKeyboardShown() = Unit
      }

      addInputListener(listener)
      addKeyboardStateListener(listener)
      hideAll(imeTarget)
    } else {
      onHidden()
    }
  }

  fun toggleInput(fragmentCreator: FragmentCreator, imeTarget: EditText, showSoftKeyOnHide: Boolean = wasKeyboardVisibleBeforeToggle) {
    if (fragmentCreator.id == inputId) {
      if (showSoftKeyOnHide) {
        showSoftkey(imeTarget)
      } else {
        hideInput(resetKeyboardGuideline = true)
      }
    } else {
      wasKeyboardVisibleBeforeToggle = isKeyboardShowing
      hideInput(resetKeyboardGuideline = false)
      showInput(fragmentCreator, imeTarget)
    }
  }

  fun hideInput() {
    hideInput(resetKeyboardGuideline = true)
    wasKeyboardVisibleBeforeToggle = false
  }

  fun hideKeyboard(imeTarget: EditText, keepHeightOverride: Boolean = false) {
    if (isKeyboardShowing) {
      if (keepHeightOverride) {
        overrideKeyboardGuidelineWithPreviousHeight()
      }
      ViewUtil.hideKeyboard(context, imeTarget)
    }
  }

  private fun showInput(fragmentCreator: FragmentCreator, imeTarget: EditText) {
    val createdInput = fragmentCreator.create()
    val presentation = fragmentCreator.presentation
    val containerId = presentation.containerId

    inputId = fragmentCreator.id
    input = createdInput
    inputPresentation = presentation
    activeContainerId = containerId
    findViewById<View>(containerId).isVisible = true

    fragmentManager
      .beginTransaction()
      .replace(containerId, createdInput)
      .runOnCommit { (createdInput as? InputFragment)?.show() }
      .commit()

    if (presentation == Presentation.KEYBOARD_HEIGHT) {
      overrideKeyboardGuidelineWithPreviousHeight()
    }
    ViewUtil.hideKeyboard(context, imeTarget)

    listeners.forEach { it.onInputShown(fragmentCreator.id) }
  }

  private fun hideInput(resetKeyboardGuideline: Boolean) {
    val inputHidden = input != null
    val hiddenPresentation = inputPresentation
    val hiddenContainerId = activeContainerId

    input?.let {
      (input as? InputFragment)?.hide()
      fragmentManager
        .beginTransaction()
        .remove(it)
        .commit()
    }
    hiddenContainerId?.let { findViewById<View>(it).isVisible = false }
    input = null
    inputId = null
    inputPresentation = null
    activeContainerId = null

    // A full-screen input never owns the keyboard guideline. With no active input, retain the old
    // reset behavior so hideAll/showSoftkey can still clean up ordinary IME geometry.
    if (hiddenPresentation != Presentation.FULL_SCREEN) {
      if (resetKeyboardGuideline) {
        resetKeyboardGuideline()
      } else {
        clearKeyboardGuidelineOverride()
      }
    }

    if (inputHidden) {
      listeners.forEach { it.onInputHidden() }
    }
  }

  enum class Presentation(@IdRes val containerId: Int) {
    KEYBOARD_HEIGHT(R.id.input_container),
    FULL_SCREEN(R.id.full_screen_input_container)
  }

  interface FragmentCreator {
    val id: Int
    val presentation: Presentation
      get() = Presentation.KEYBOARD_HEIGHT

    fun create(): Fragment
  }

  interface Listener {
    fun onInputShown(fragmentCreatorId: Int)
    fun onInputHidden()
  }

  interface InputFragment {
    fun show()
    fun hide()
  }
}
