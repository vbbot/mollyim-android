/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.conversationlist;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.main.MainNavigationListLocation;



public class ConversationListArchiveFragment extends ConversationListFragment
{
  private RecyclerView                foldersList;
  private LifecycleDisposable         lifecycleDisposable = new LifecycleDisposable();

  public static ConversationListArchiveFragment newInstance() {
    return new ConversationListArchiveFragment();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setHasOptionsMenu(false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    foldersList = view.findViewById(R.id.chat_folder_list);

    foldersList.setVisibility(View.GONE);

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        mainNavigationViewModel.goTo(MainNavigationListLocation.CHATS);
      }
    });
  }

  @Override
  protected boolean isArchived() {
    return true;
  }

  @Override
  protected @DrawableRes int getArchiveIconRes() {
    return R.drawable.symbol_archive_up_24;
  }

  @Override
  void updateEmptyState(boolean isConversationEmpty) {
    // Do nothing
  }
}


