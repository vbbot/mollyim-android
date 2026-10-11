/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.edit

import android.graphics.BitmapFactory
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import java.util.Locale

/** The LightTextField underline, repeated under the editor's field: 3 design px, 0.5 gu below, 80% wide. */
private const val UNDERLINE_THICKNESS_PX = 3f
private const val UNDERLINE_GAP_GRID_UNITS = 0.5f
private const val UNDERLINE_WIDTH_FRACTION = 0.8f

/** The avatar thumbnail beside "Edit photo". */
private const val AVATAR_GRID_UNITS = 4f

/** Upper bound on the decoded thumbnail's longer edge, so a camera-sized avatar is not held at full size. */
private const val AVATAR_DECODE_MAX_PX = 256

/**
 * The two text fields of the screen. For a profile they are the given and family names; for a group,
 * the group name and the group description -- the same two [EditProfileViewModel] slots.
 */
enum class CreateProfileField {
  GIVEN_NAME,
  FAMILY_NAME
}

/**
 * What a caller's intent extras make of the screen. Fixed for the life of the fragment.
 *
 * [showFamilyField] is false for a group that has no description (MMS and GV1 groups); [showMmsHint]
 * is the MMS group's "only visible to you" caption.
 */
data class CreateProfileConfig(
  val isGroup: Boolean,
  @StringRes val title: Int,
  val showBack: Boolean,
  @StringRes val nextLabel: Int,
  val showFamilyField: Boolean,
  val showMmsHint: Boolean,
  val givenNameLimit: CreateProfileFieldLimit,
  val familyNameLimit: CreateProfileFieldLimit
)

/**
 * Compose-observable copy of what [CreateProfileFragment] shows. The fragment writes it from
 * [EditProfileViewModel]'s LiveData and its own upload state; the screen only reads it.
 */
class CreateProfileState {
  var givenName: String by mutableStateOf("")
  var familyName: String by mutableStateOf("")
  var avatar: ByteArray? by mutableStateOf(null)

  /** The "Who can find me by number?" value, or null where the row does not apply (groups). */
  var whoCanFindMe: String? by mutableStateOf(null)
  var formValid: Boolean by mutableStateOf(false)
  var uploading: Boolean by mutableStateOf(false)

  /** The field whose full-screen editor is open, if any. */
  var editing: CreateProfileField? by mutableStateOf(null)
}

/** Taps, for the Java fragment. */
interface CreateProfileCallbacks {
  fun onBackClicked()
  fun onLearnMoreClicked()
  fun onAvatarClicked()
  fun onFieldClicked(field: CreateProfileField)
  fun onWhoCanFindMeClicked()
  fun onEditorBack()
  fun onEditorDone(field: CreateProfileField, text: String)
  fun onNextClicked()
}

object CreateProfileContent {
  /**
   * The fragment's view. [SignalTheme] as `ComposeFragment` provides it, so the incognito keyboard
   * setting reaches the editor's field; [MollyLightTheme] inside chains with it.
   */
  @JvmStatic
  fun create(fragment: Fragment, config: CreateProfileConfig, state: CreateProfileState, callbacks: CreateProfileCallbacks): View {
    return fragment.content {
      SignalTheme {
        val avatarBytes = state.avatar
        val avatarImage by produceState<ImageBitmap?>(null, avatarBytes) {
          value = avatarBytes?.let { withContext(Dispatchers.Default) { decodeAvatar(it) } }
        }

        CreateProfileScreen(
          config = config,
          givenName = state.givenName,
          familyName = state.familyName,
          hasAvatar = avatarBytes != null,
          avatarImage = avatarImage,
          whoCanFindMe = state.whoCanFindMe,
          editing = state.editing,
          nextEnabled = state.formValid,
          uploading = state.uploading,
          onBackClicked = callbacks::onBackClicked,
          onLearnMoreClicked = callbacks::onLearnMoreClicked,
          onAvatarClicked = callbacks::onAvatarClicked,
          onFieldClicked = callbacks::onFieldClicked,
          onWhoCanFindMeClicked = callbacks::onWhoCanFindMeClicked,
          onEditorBack = callbacks::onEditorBack,
          onEditorDone = callbacks::onEditorDone,
          onNextClicked = callbacks::onNextClicked
        )
      }
    }
  }

  private fun decodeAvatar(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= AVATAR_DECODE_MAX_PX) {
      sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
  }
}

/**
 * Profile setup (first run) and group name/description editing, as a Light form: explanatory copy, a
 * photo row, two display-only fields that each open a full-screen editor, and for a profile the
 * "Who can find me by number?" row. NEXT (or SAVE) sits in the bottom action row.
 *
 * The fields open an editor rather than being typed into in place: with the LP3 keyboard up, a
 * 413dp screen has roughly 200dp left, which would push an inline field under the keyboard or the
 * form out of view. The editor puts the one field just under its bar, where the keyboard cannot
 * reach it -- the pattern the phone number step and chats' FieldEditorScreen use.
 */
@Composable
fun CreateProfileScreen(
  config: CreateProfileConfig,
  givenName: String,
  familyName: String,
  hasAvatar: Boolean,
  avatarImage: ImageBitmap?,
  whoCanFindMe: String?,
  editing: CreateProfileField?,
  nextEnabled: Boolean,
  uploading: Boolean,
  onBackClicked: () -> Unit,
  onLearnMoreClicked: () -> Unit,
  onAvatarClicked: () -> Unit,
  onFieldClicked: (CreateProfileField) -> Unit,
  onWhoCanFindMeClicked: () -> Unit,
  onEditorBack: () -> Unit,
  onEditorDone: (CreateProfileField, String) -> Unit,
  onNextClicked: () -> Unit
) {
  MollyLightTheme {
    val colors = LightThemeTokens.colors
    val controlsEnabled = !uploading

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(colors.background)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
          leftButton = if (config.showBack) {
            LightBarButton.LightIcon(
              icon = LightIcons.BACK,
              onClick = onBackClicked,
              contentDescription = stringResource(R.string.CreateProfileFragment__close_without_saving)
            )
          } else {
            null
          },
          center = LightTopBarCenter.Text(text = stringResource(config.title)),
          modifier = Modifier.statusBarsPadding()
        )

        LightScrollView(
          scrollBarPosition = LightScrollBarPosition.Outside,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          // Start padding only: the Outside scroll bar already reserves the 2 gu gutter at the end.
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 2f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp())
          ) {
            if (!config.isGroup) {
              LightText(
                text = stringResource(R.string.ProfileCreateFragment__profiles_are_visible_to_contacts_and_people_you_message),
                variant = LightTextVariant.Paragraph
              )
              LearnMoreAction(enabled = controlsEnabled, onClick = onLearnMoreClicked)
            }

            AvatarRow(
              hasAvatar = hasAvatar,
              image = avatarImage,
              enabled = controlsEnabled,
              onClick = onAvatarClicked
            )

            val givenNameField = fieldStrings(config, CreateProfileField.GIVEN_NAME)
            LightTextField(
              label = givenNameField.label,
              value = givenName,
              placeholder = givenNameField.placeholder,
              onClick = { if (controlsEnabled) onFieldClicked(CreateProfileField.GIVEN_NAME) },
              modifier = Modifier.padding(vertical = 0.75f.gridUnitsAsDp())
            )

            if (config.showMmsHint) {
              LightText(
                text = stringResource(R.string.CreateProfileActivity_custom_mms_group_names_and_photos_will_only_be_visible_to_you),
                variant = LightTextVariant.Detail
              )
            }

            if (config.showFamilyField) {
              val familyNameField = fieldStrings(config, CreateProfileField.FAMILY_NAME)
              LightTextField(
                label = familyNameField.label,
                value = familyName,
                placeholder = familyNameField.placeholder,
                onClick = { if (controlsEnabled) onFieldClicked(CreateProfileField.FAMILY_NAME) },
                modifier = Modifier.padding(vertical = 0.75f.gridUnitsAsDp())
              )
            }

            if (config.isGroup && config.showFamilyField) {
              LightText(
                text = stringResource(R.string.CreateProfileActivity_group_descriptions_will_be_visible_to_members_of_this_group_and_people_who_have_been_invited),
                variant = LightTextVariant.Paragraph,
                modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp())
              )
            }

            if (whoCanFindMe != null) {
              WhoCanFindMeRow(
                value = whoCanFindMe,
                enabled = controlsEnabled,
                onClick = onWhoCanFindMeClicked
              )
            }
          }
        }

        RegistrationBottomActions(
          right = RegistrationAction(
            label = if (uploading) "…" else stringResource(config.nextLabel),
            onClick = if (uploading) ({}) else onNextClicked,
            enabled = !uploading && nextEnabled
          ),
          modifier = Modifier.navigationBarsPadding()
        )
      }

      if (editing != null) {
        val strings = fieldStrings(config, editing)
        FieldEditor(
          title = strings.editorTitle,
          placeholder = strings.placeholder,
          initialText = if (editing == CreateProfileField.GIVEN_NAME) givenName else familyName,
          limit = if (editing == CreateProfileField.GIVEN_NAME) config.givenNameLimit else config.familyNameLimit,
          multiLine = config.isGroup && editing == CreateProfileField.FAMILY_NAME,
          contentType = when {
            config.isGroup -> null
            editing == CreateProfileField.GIVEN_NAME -> ContentType.PersonFirstName
            else -> ContentType.PersonLastName
          },
          onBack = onEditorBack,
          onDone = { onEditorDone(editing, it) }
        )
      }
    }
  }
}

private data class FieldStrings(val label: String, val placeholder: String, val editorTitle: String)

@Composable
private fun fieldStrings(config: CreateProfileConfig, field: CreateProfileField): FieldStrings {
  return when {
    config.isGroup && field == CreateProfileField.GIVEN_NAME -> FieldStrings(
      label = stringResource(R.string.EditProfileFragment__group_name),
      placeholder = stringResource(R.string.AddGroupDetailsFragment__name_this_group),
      editorTitle = stringResource(R.string.EditProfileFragment__group_name)
    )
    config.isGroup -> FieldStrings(
      label = stringResource(R.string.EditProfileFragment__group_description),
      placeholder = stringResource(R.string.ManageGroupActivity_add_group_description),
      editorTitle = stringResource(R.string.EditProfileFragment__group_description)
    )
    field == CreateProfileField.GIVEN_NAME -> FieldStrings(
      label = stringResource(R.string.CreateProfileActivity_first_name_required),
      placeholder = stringResource(R.string.CreateProfileFragment__add_first_name),
      editorTitle = stringResource(R.string.EditProfileNameFragment_first_name)
    )
    else -> FieldStrings(
      label = stringResource(R.string.CreateProfileActivity_last_name_optional),
      placeholder = stringResource(R.string.CreateProfileFragment__add_last_name),
      editorTitle = stringResource(R.string.NicknameActivity__last_name)
    )
  }
}

/** The upstream "Learn more" link after the explanation, as the PIN screens present theirs. */
@Composable
private fun LearnMoreAction(enabled: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .lightClickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp())
  ) {
    LightText(
      text = stringResource(R.string.LearnMoreTextView_learn_more).uppercase(Locale.getDefault()),
      variant = LightTextVariant.Button
    )
  }
}

/**
 * Opens the avatar picker. Monochrome: no colour circle and no camera badge -- just the label, with
 * the chosen photo beside it once there is one, since the photo is the content.
 */
@Composable
private fun AvatarRow(
  hasAvatar: Boolean,
  image: ImageBitmap?,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(vertical = 0.75f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (image != null) {
      Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .size(AVATAR_GRID_UNITS.gridUnitsAsDp())
          .clip(CircleShape)
      )
      Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
    }

    LightText(
      text = stringResource(if (hasAvatar) R.string.ManageProfileFragment__edit_photo else R.string.AddAProfilePhotoMegaphone__add_photo),
      variant = LightTextVariant.Heading,
      lighten = !enabled,
      maxLines = 1
    )
  }
}

/** chats' row grammar: the setting at Heading, its current value at Detail. Opens the privacy screen. */
@Composable
private fun WhoCanFindMeRow(
  value: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(vertical = 0.75f.gridUnitsAsDp())
  ) {
    LightText(
      text = stringResource(R.string.ProfileCreateFragment__who_can_find_me),
      variant = LightTextVariant.Heading,
      lighten = !enabled
    )
    LightText(
      text = value,
      variant = LightTextVariant.Detail
    )
  }
}

/**
 * Full-screen editor for one field, as the phone number step's: BACK discards, DONE commits. Drawn
 * over the form, opaque and touch-swallowing, with the field just under the bar so the keyboard
 * takes the bottom of the screen without covering it. Every edit goes through [limit] -- the
 * grapheme and byte limits the EditText filters used to apply.
 */
@Composable
private fun FieldEditor(
  title: String,
  placeholder: String,
  initialText: String,
  limit: CreateProfileFieldLimit,
  multiLine: Boolean,
  contentType: ContentType?,
  onBack: () -> Unit,
  onDone: (String) -> Unit
) {
  val colors = LightThemeTokens.colors
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
    mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
  }

  val commit = {
    keyboardController?.hide()
    onDone(value.text)
  }
  val discard = {
    keyboardController?.hide()
    onBack()
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboardController?.show()
  }

  val baseStyle = LightThemeTokens.typography.copy
  val textStyle = baseStyle.copy(
    fontSize = baseStyle.fontSize.scaled(),
    lineHeight = baseStyle.lineHeight.scaled(),
    letterSpacing = baseStyle.letterSpacing.scaled(),
    color = colors.content
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false).consume()
          while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
          }
        }
      }
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = discard,
        contentDescription = stringResource(R.string.CreateProfileFragment__close_without_saving)
      ),
      center = LightTopBarCenter.Text(text = title),
      rightButton = LightBarButton.Text(
        text = stringResource(R.string.CreateProfileFragment__done).uppercase(Locale.getDefault()),
        onClick = commit
      ),
      modifier = Modifier.statusBarsPadding()
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 2f.gridUnitsAsDp())
        .padding(top = 1f.gridUnitsAsDp())
    ) {
      BasicTextField(
        value = value,
        onValueChange = { edited -> value = limit.apply(value, edited) },
        singleLine = !multiLine,
        maxLines = if (multiLine) 5 else 1,
        keyboardOptions = if (multiLine) {
          KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default)
        } else {
          KeyboardOptions(capitalization = KeyboardCapitalization.Words, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
        },
        keyboardActions = KeyboardActions(onDone = { commit() }),
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.content),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier),
        decorationBox = { innerTextField ->
          Box {
            if (value.text.isEmpty()) {
              LightText(
                text = placeholder,
                variant = LightTextVariant.Copy,
                lighten = true,
                maxLines = 1
              )
            }
            innerTextField()
          }
        }
      )
      Spacer(modifier = Modifier.height(UNDERLINE_GAP_GRID_UNITS.gridUnitsAsDp()))
      Spacer(
        modifier = Modifier
          .fillMaxWidth(UNDERLINE_WIDTH_FRACTION)
          .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
          .background(colors.content)
      )
    }
  }
}

/** Editable text does not go through LightText, so the screen-height scaling is applied by hand. */
@Composable
private fun TextUnit.scaled(): TextUnit =
  if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()
