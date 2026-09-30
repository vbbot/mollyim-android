package org.thoughtcrime.securesms.components.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.ui.logging.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightBarAction
import org.thoughtcrime.securesms.light.LightTopBarView
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * The DSL API can be completely replaced by compose.
 * See ComposeFragment or ComposeBottomSheetFragment for an alternative to this API
 *
 * ## Chrome
 *
 * The default layout (`dsl_settings_fragment`) now hosts [LightTopBarView] -- a back chevron,
 * a centred title and at most one action -- in place of the Material toolbar, on a true-black
 * surface. That is the frame every un-converted settings destination is sitting inside, so converting
 * it here is what makes a Light list stop being a Light list in a Material frame.
 *
 * A destination may still hand this fragment its own `layoutId`; the ones that do today
 * (`DonateToSignalFragment`, `ManageDonationsFragment`, `ConversationSettingsFragment`,
 * `DonationReceiptDetailFragment`, and the stories ones) all include `dsl_settings_toolbar.xml` and
 * so still carry a real Material toolbar. Those are destinations, not chrome, and they belong to
 * other work: [lightTopBar] is null for them and they keep the previous path untouched. `dsl_settings_toolbar.xml`
 * itself is not edited, because eighteen layouts outside this class include it.
 */
abstract class DSLSettingsFragment(
  @StringRes private val titleId: Int = -1,
  @MenuRes private val menuId: Int = -1,
  @LayoutRes layoutId: Int = R.layout.dsl_settings_fragment,
  protected var layoutManagerProducer: (Context) -> RecyclerView.LayoutManager = { context -> LinearLayoutManager(context) }
) : LoggingFragment(layoutId) {

  protected var recyclerView: RecyclerView? = null
    private set

  private var toolbar: Toolbar? = null

  /**
   * Non-null only when the destination is on the default (Light) layout. Its presence is what
   * selects the Light chrome path over the legacy Material one.
   */
  private var lightTopBar: LightTopBarView? = null

  @CallSuper
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    toolbar = view.findViewById(R.id.toolbar)
    lightTopBar = view.findViewById(R.id.light_top_bar)

    if (lightTopBar != null) {
      bindLightTopBar(lightTopBar!!)
    } else {
      bindMaterialToolbar(toolbar)
    }

    val config = ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build()
    val settingsAdapters = createAdapters()
    val settingsAdapter: RecyclerView.Adapter<out RecyclerView.ViewHolder> = when {
      settingsAdapters.size > 1 -> ConcatAdapter(config, *settingsAdapters)
      settingsAdapters.size == 1 -> settingsAdapters.first()
      else -> error("Require one or more settings adapters.")
    }

    recyclerView = view.findViewById<RecyclerView>(R.id.recycler).apply {
      edgeEffectFactory = EdgeEffectFactory()
      layoutManager = layoutManagerProducer(requireContext())
      adapter = settingsAdapter

      // A Light bar has no collapsing Material surface to tint, so the scroll helper has nothing to
      // do and is deliberately not consulted. Skipping the call matters as much as skipping the
      // attach: several destinations override this and do `toolbar!!`, which would throw.
      if (lightTopBar == null) {
        getMaterial3OnScrollHelper(toolbar)?.let {
          it.attach(this)
        }
      }
    }

    when (settingsAdapter) {
      is ConcatAdapter -> bindAdapters(settingsAdapter)
      is MappingAdapter -> bindAdapter(settingsAdapter)
      else -> error("Illegal adapter subtype: ${settingsAdapter.javaClass.simpleName}")
    }
  }

  /**
   * The Light path. The bar owns the title, the back affordance and any menu action; the hidden
   * toolbar underneath stays the `MenuProvider` host and the popup anchor the DSL menu contract
   * depends on.
   */
  private fun bindLightTopBar(bar: LightTopBarView) {
    bar.leftAction = LightTopBarView.backAction(
      contentDescription = getString(R.string.DSLSettingsToolbar__navigate_up),
      onClick = { onToolbarNavigationClicked() }
    )

    if (titleId != -1) {
      bar.title = getString(titleId)
    }

    if (menuId != -1) {
      toolbar?.inflateMenu(menuId)
      toolbar?.setOnMenuItemClickListener { onOptionsItemSelected(it) }

      // `Toolbar.getMenu()` hands back the same instance the inflate above filled, so the items read
      // here are the very `MenuItem`s `onOptionsItemSelected` expects -- not a detached copy.
      val actions = toolbar?.menu?.let { m ->
        (0 until m.size()).map(m::getItem).filter { it.isVisible }
      }.orEmpty()

      if (actions.size == 1) {
        bar.rightAction = actions[0].icon?.let { icon ->
          LightBarAction.HostDrawable(
            contentDescription = actions[0].title?.toString(),
            // A `MenuItem`'s icon is usually backed by a shared `ConstantState`: the same instance
            // the hidden toolbar is holding. Copy it, so recolouring the bar's glyph cannot
            // recolour the drawable anywhere else that state is used from.
            icon = icon.constantState?.newDrawable(resources)?.mutate() ?: icon,
            onClick = { onOptionsItemSelected(actions[0]) }
          )
        }
      } else {
        // `LightTopBar` has one right slot. With no action the slot is empty, and with several it
        // stays empty too: rendering the first would silently drop the rest, so the menu is left to
        // the toolbar's own overflow instead. No settings screen is in that state today, and when
        // one is, the overflow is visible rather than an action gone missing.
        bar.rightAction = null
      }
    }
  }

  private fun bindMaterialToolbar(toolbar: Toolbar?) {
    if (titleId != -1) {
      toolbar?.setTitle(titleId)
    }

    toolbar?.setNavigationOnClickListener {
      onToolbarNavigationClicked()
    }

    if (menuId != -1) {
      toolbar?.inflateMenu(menuId)
      toolbar?.setOnMenuItemClickListener { onOptionsItemSelected(it) }
    }
  }

  open fun getMaterial3OnScrollHelper(toolbar: Toolbar?): Material3OnScrollHelper? {
    if (toolbar == null) {
      return null
    }

    return Material3OnScrollHelper(
      activity = requireActivity(),
      views = listOf(toolbar),
      lifecycleOwner = viewLifecycleOwner
    )
  }

  open fun onToolbarNavigationClicked() {
    requireActivity().onBackPressed()
  }

  override fun onDestroyView() {
    recyclerView = null
    toolbar = null
    lightTopBar = null
    super.onDestroyView()
  }

  fun setTitle(@StringRes resId: Int) {
    toolbar?.setTitle(resId)
    lightTopBar?.title = getString(resId)
  }

  fun setTitle(title: CharSequence) {
    toolbar?.title = title
    lightTopBar?.title = title.toString()
  }

  open fun createAdapters(): Array<MappingAdapter> {
    return arrayOf(DSLSettingsAdapter())
  }

  open fun bindAdapter(adapter: MappingAdapter) {
    throw UnsupportedOperationException("This method is not implemented.")
  }

  open fun bindAdapters(adapter: ConcatAdapter) {
    throw UnsupportedOperationException("This method is not implemented.")
  }

  private class EdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
      return super.createEdgeEffect(view, direction).apply {
        if (Build.VERSION.SDK_INT > 21) {
          color =
            requireNotNull(ContextCompat.getColor(view.context, R.color.settings_ripple_color))
        }
      }
    }
  }
}
