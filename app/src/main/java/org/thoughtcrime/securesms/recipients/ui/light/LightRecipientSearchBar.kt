/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.light

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R

/** The search field sits on the list's row rhythm, so it is the same height as a contact row. */
private const val FIELD_HEIGHT_UNITS = 4.5f

/** The leading glyph's slot, the same width as the row's selection marker slot. */
private const val ICON_SLOT_UNITS = 1f

/** Gap between the glyph and the text, matching the row's marker-to-name gap. */
private const val COLUMN_GAP_UNITS = 0.25f

/** Field side margin, matching the row's. */
private const val HORIZONTAL_PADDING_UNITS = 0.5f

/** Underline thickness, in the SDK's design-space vertical pixels. Straight from `LightTextField`. */
private const val UNDERLINE_THICKNESS_PX = 3f

/**
 * The contact picker's search field, in the Light idiom.
 *
 * This replaces `RecipientSearchBar`, which is a Material 3 `SearchBar` wrapping a filled `TextField`
 * -- a rounded grey capsule with a floating placeholder, which is about as far from the Light design
 * language as a control gets. Unlike every other input this port has had to deal with, this one is
 * the point of its screen: a Light Phone's contacts list has no alphabet index and no fast scroller,
 * so search *is* how you find someone. It therefore gets a real Light control rather than being
 * covered over.
 *
 * **The shape** is the SDK's own text-entry shape, taken from `LightTextField` (which is a display
 * field that opens an editor, so it could not be reused directly, only followed): the value in
 * `Copy`, with a rule beneath it. The columns are the list's -- a one-grid-unit leading slot holding
 * [LightIcons.SEARCH] where a row's selection marker goes, then the text where a row's name starts,
 * 1.75 grid units in. Typing therefore happens in the same column the results appear in.
 *
 * **No keyboard is embedded, and none is needed.** The Light Phone III's keyboard
 * (`app.lightphonekeyboard/.LightImeService`) is the device's default IME, so an ordinary Compose
 * text field raises the Light keyboard exactly as an `EditText` does. The SDK's own
 * `LightTextInputEditor` -- which drives a keyboard directly and takes over the whole screen -- is
 * not vendored here, and would be the wrong shape anyway: it is a modal editor, and this field has
 * to filter the list underneath it as you type.
 *
 * **Signal's text/dialpad toggle is dropped.** It swapped the IME's input type between
 * `TYPE_CLASS_TEXT` and `TYPE_CLASS_PHONE`. The Light keyboard has its own numeric layer, "find by
 * phone number" is its own row in the list below, and a mode toggle is a second control in a design
 * that wants one; the reference client's contacts panel has nothing of the kind.
 */
@Composable
fun LightRecipientSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  hint: String = stringResource(R.string.RecipientSearchBar__search_name_or_number),
  focusRequester: FocusRequester? = null
) {
  val colors = LightThemeTokens.colors
  val keyboardController = LocalSoftwareKeyboardController.current

  Column(
    modifier = modifier
      .fillMaxWidth()
      // Opaque: this sits over Molly's Material scaffold, whose surface colour is not the Light
      // theme's background.
      .background(colors.background)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = FIELD_HEIGHT_UNITS.gridUnitsAsDp())
        .padding(horizontal = HORIZONTAL_PADDING_UNITS.gridUnitsAsDp()),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier.width(ICON_SLOT_UNITS.gridUnitsAsDp()),
        contentAlignment = Alignment.CenterStart
      ) {
        LightIcon(
          icon = LightIcons.SEARCH,
          size = ICON_SLOT_UNITS,
          contentDescription = null
        )
      }

      Box(modifier = Modifier.width(COLUMN_GAP_UNITS.gridUnitsAsDp()))

      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = LightThemeTokens.typography.copy.copy(color = colors.content),
        cursorBrush = SolidColor(colors.content),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The list is already filtered by the time this can be pressed -- every keystroke has gone
        // through onQueryChange -- so the search key's only remaining job is to get the keyboard out
        // of the way of the results it just produced.
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        modifier = Modifier
          .weight(1f)
          .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        // The Box is explicit rather than relying on how `BasicTextField` happens to lay its
        // decoration out. Both arrangements measure the same today -- the hint and the entry end up
        // on one line either way -- but "the hint sits behind the entry" is the thing this field
        // needs to be true, and an overlay is how you say it rather than how you get it.
        decorationBox = { innerTextField ->
          Box(contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
              LightText(
                text = hint,
                variant = LightTextVariant.Copy,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            innerTextField()
          }
        }
      )

      if (query.isNotEmpty()) {
        Box(modifier = Modifier.width(COLUMN_GAP_UNITS.gridUnitsAsDp()))
        Box(
          modifier = Modifier
            .width(ICON_SLOT_UNITS.gridUnitsAsDp())
            .lightClickable { onQueryChange("") },
          contentAlignment = Alignment.CenterEnd
        ) {
          LightIcon(
            icon = LightIcons.CLOSE,
            size = ICON_SLOT_UNITS,
            contentDescription = stringResource(R.string.RecipientSearchBar_accessibility_clear_search)
          )
        }
      }
    }

    // The rule that makes this read as somewhere to type rather than as another list row. Full
    // bleed, unlike `LightTextField`'s 80% rule, because this field spans the screen rather than
    // sitting in a form.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
        .background(colors.content)
    )
  }
}
