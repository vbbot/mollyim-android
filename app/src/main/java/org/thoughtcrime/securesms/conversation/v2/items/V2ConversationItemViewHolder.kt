/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Base ViewHolder to share some common properties shared among conversation items.
 */
abstract class V2ConversationItemViewHolder<Model : MappingModel<Model>>(
  root: V2ConversationItemLayout,
  appearanceInfoProvider: V2ConversationContext,
  /**
   * Every colour an item paints itself with comes from here, so supplying a different one is how a
   * design language re-colours the whole item at once -- including the presenters that run on partial
   * re-binds, which a post-hoc fix-up in `bind` would miss.
   */
  protected val themeDelegate: V2ConversationItemTheme = V2ConversationItemTheme(root.context, appearanceInfoProvider)
) : MappingViewHolder<Model>(root) {
  protected val shapeDelegate = V2ConversationItemShape(appearanceInfoProvider)
}
