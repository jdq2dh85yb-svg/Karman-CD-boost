package de.sgh0hann.boost

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.nio.charset.StandardCharsets

class MainActivity : Activity() {

    private enum class Kind { INFO, OK, WARN, ERROR }

    private class Profile(
        val nameRes: Int,
        val subtitleRes: Int,
        val descriptionRes: Int,
        val values: Map<String, String>
    )

    /** A group of settings that is searched for by keyword in the options.ini. */
    private class Category(
        val titleRes: Int,
        val descriptionRes: Int,
        val keywords: List<String>
    )

    private class Proposal(val key: String, val current: String, val target: String?)

    private val gamePackage = "com.titan.cd.gb"
    private val gameActivity = "com.netease.titanDTS.Launcher"

    private val configPath =
        "/storage/emulated/0/Android/data/com.titan.cd.gb/files/titan/titanDTS.google_play/options.ini"
    private val configDir = configPath.substringBeforeLast('/')

    // Original backup (never overwritten) and the state before the last change
    private val backupPath = "$configPath.sgh0hann-backup"
    private val lastBackupPath = "$configPath.sgh0hann-last"

    private val appVersion = "3.0"
    private val requestCode = 1001

    private val profiles = listOf(
        Profile(
            R.string.profile_normal, R.string.profile_normal_sub, R.string.profile_normal_desc,
            mapOf(
                "quality" to "auto",
                "renderScale" to "auto",
                "shadows" to "auto",
                "antiAliasing" to "auto",
                "bloom" to "auto",
                "effects" to "auto",
                "viewDistance" to "100"
            )
        ),
        Profile(
            R.string.profile_low, R.string.profile_low_sub, R.string.profile_low_desc,
            mapOf(
                "quality" to "low",
                "renderScale" to "0.8",
                "shadows" to "off",
                "antiAliasing" to "off",
                "bloom" to "off",
                "effects" to "low",
                "viewDistance" to "80"
            )
        ),
        Profile(
            R.string.profile_flat, R.string.profile_flat_sub, R.string.profile_flat_desc,
            mapOf(
                "quality" to "lowest",
                "renderScale" to "1.0",
                "shadows" to "off",
                "antiAliasing" to "off",
                "bloom" to "off",
                "effects" to "low",
                "viewDistance" to "100"
            )
        ),
        Profile(
            R.string.profile_ultra, R.string.profile_ultra_sub, R.string.profile_ultra_desc,
            mapOf(
                "quality" to "lowest",
                "renderScale" to "0.65",
                "shadows" to "off",
                "antiAliasing" to "off",
                "bloom" to "off",
                "effects" to "low",
                "viewDistance" to "65"
            )
        ),
        Profile(
            R.string.profile_potato, R.string.profile_potato_sub, R.string.profile_potato_desc,
            mapOf(
                "quality" to "lowest",
                "renderScale" to "0.5",
                "shadows" to "off",
                "antiAliasing" to "off",
                "bloom" to "off",
                "effects" to "low",
                "viewDistance" to "50"
            )
        )
    )

    private val categories = listOf(
        Category(
            R.string.cat_grass, R.string.cat_grass_desc,
            listOf("grass", "foliage", "vegetation", "tree", "bush")
        ),
        Category(
            R.string.cat_ground, R.string.cat_ground_desc,
            listOf("terrain", "ground", "landscape", "road")
        ),
        Category(
            R.string.cat_sky, R.string.cat_sky_desc,
            listOf("sky", "cloud", "atmosphere", "fog", "weather")
        ),
        Category(
            R.string.cat_textures, R.string.cat_textures_desc,
            listOf("texture", "material", "anisotropic", "mipmap", "lod")
        ),
        Category(
            R.string.cat_effects, R.string.cat_effects_desc,
            listOf("particle", "decal", "blur", "dof", "ssao", "reflection", "water", "glow", "light")
        )
    )

    private val tabTitles = listOf(
        R.string.tab_home,
        R.string.tab_profiles,
        R.string.tab_visuals,
        R.string.tab_fps,
        R.string.tab_config,
        R.string.tab_system
    )
    private val tabIcons = listOf(
        IconKind.HOME,
        IconKind.SLIDERS,
        IconKind.EYE,
        IconKind.GAUGE,
        IconKind.LIST,
        IconKind.GEAR
    )

    // ---------- State ----------

    private var tab = 0
    private var lastTab = -1

    private var shizukuRunning = false
    private var shizukuGranted = false

    private var gameInstalled = false
    private var gameVersion: String? = null
    private var gameRunning: Boolean? = null

    private var config: Map<String, String>? = null
    private var configProblem: String? = null
    private var hasOriginal = false
    private var hasLast = false

    private var physSize: Pair<Int, Int>? = null
    private var curSize: Pair<Int, Int>? = null
    private var hasOverride = false
    private var pendingRevert = false
    private var countdown = 0

    private var exploreFilter = ""
    private var pendingText = ""

    private var busy = false
    private var refreshing = false
    private var refreshQueued = false

    private var statusMessage = ""
    private var statusKind = Kind.INFO
    private var statusIsAuto = true

    // ---------- Views ----------

    private lateinit var contentBox: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var statusView: TextView
    private lateinit var chipHolder: FrameLayout
    private val navIcons = ArrayList<IconView>()
    private val navLabels = ArrayList<TextView>()
    private val navPills = ArrayList<FrameLayout>()

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("boost", Context.MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() {
            if (!pendingRevert) return
            countdown--
            if (countdown <= 0) {
                resetStretch()
            } else {
                setStatus(tr(R.string.stretch_confirm, countdown), Kind.WARN)
                render()
                handler.postDelayed(this, 1000)
            }
        }
    }

    // ---------- Shizuku listeners ----------

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            runOnUiThread {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    setStatus(tr(R.string.perm_granted_msg), Kind.OK)
                } else {
                    setStatus(tr(R.string.perm_denied_msg), Kind.ERROR)
                }
                refreshAll()
            }
        }

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            runOnUiThread { refreshAll(resetStatus = true) }
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            runOnUiThread { refreshAll(resetStatus = true) }
        }

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.statusBarColor = Ui.bgTop
            window.navigationBarColor = Ui.navBar
        } catch (_: Throwable) {
        }

        try {
            buildShell()
            setStatus(tr(R.string.status_checking), Kind.INFO, auto = true)
            render()
        } catch (e: Throwable) {
            showFatal(e)
            return
        }

        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {
        }
    }

    override fun onResume() {
        super.onResume()
        if (::contentBox.isInitialized) {
            refreshAll(resetStatus = true)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (tab != 0) {
            tab = 0
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun showFatal(e: Throwable) {
        val t = TextView(this).apply {
            text = tr(R.string.fatal_start, e.javaClass.simpleName, e.message ?: "")
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(13, 14, 23))
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        setContentView(t)
    }

    // ---------- Shell (header, status, content, navigation) ----------

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.bgTop, Ui.bgBottom)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(6))
        }
        header.addView(LogoView(this), lp(dp(46), dp(46)))

        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(
            label("SG0HANN BOOST", 16f, Ui.white, style = 2, spacing = 0.1f).apply {
                maxLines = 1
            }
        )
        titles.addView(
            label(tr(R.string.header_edition), 10f, Ui.accent, style = 1, spacing = 0.3f).apply {
                setPadding(0, dp(2), 0, 0)
            }
        )
        header.addView(titles, lp(0, Ui.WRAP, 1f).apply { leftMargin = dp(12) })

        chipHolder = FrameLayout(this)
        header.addView(chipHolder, lp(Ui.WRAP, Ui.WRAP))
        root.addView(header, lp(Ui.MATCH, Ui.WRAP))

        statusView = TextView(this).apply {
            textSize = 13f
            maxLines = 3
            setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        root.addView(
            statusView,
            lp(Ui.MATCH, Ui.WRAP).apply { setMargins(dp(20), dp(6), dp(20), dp(4)) }
        )

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(28))
        }
        scroll.addView(contentBox, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        root.addView(scroll, lp(Ui.MATCH, 0, 1f))

        root.addView(View(this).apply { setBackgroundColor(Ui.stroke) }, lp(Ui.MATCH, 1))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.navBar)
        }
        for (i in tabTitles.indices) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    tab = i
                    render()
                }
            }
            val pill = FrameLayout(this)
            val icon = IconView(this, tabIcons[i])
            navIcons.add(icon)
            navPills.add(pill)
            pill.addView(icon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            item.addView(pill, lp(dp(50), dp(30)))

            val txt = label(tr(tabTitles[i]), 10f, Ui.muted, style = 1).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(3), 0, 0)
            }
            navLabels.add(txt)
            item.addView(txt, lp(Ui.WRAP, Ui.WRAP))

            nav.addView(item, lp(0, Ui.WRAP, 1f))
        }
        root.addView(nav, lp(Ui.MATCH, Ui.WRAP))

        setContentView(root)
    }

    // ---------- Rendering ----------

    private fun render() {
        if (!::contentBox.isInitialized) return

        val tabChanged = tab != lastTab
        val y = scroll.scrollY

        updateHeader()
        updateNav()
        updateStatusView()

        contentBox.removeAllViews()
        try {
            when (tab) {
                0 -> buildHome()
                1 -> buildProfiles()
                2 -> buildVisuals()
                3 -> buildPerformance()
                4 -> buildConfig()
                else -> buildSystem()
            }
        } catch (e: Throwable) {
            contentBox.addView(
                label(
                    tr(R.string.display_error, e.javaClass.simpleName, e.message ?: ""),
                    13f,
                    Ui.bad
                )
            )
        }

        lastTab = tab
        if (tabChanged) {
            scroll.scrollTo(0, 0)
            contentBox.alpha = 0f
            contentBox.translationY = dp(10).toFloat()
            contentBox.animate().alpha(1f).translationY(0f).setDuration(220).start()
        } else {
            scroll.post { scroll.scrollTo(0, y) }
        }
    }

    private fun updateHeader() {
        val text: String
        val color: Int
        when {
            !shizukuRunning -> {
                text = tr(R.string.chip_disconnected)
                color = Ui.bad
            }
            !shizukuGranted -> {
                text = tr(R.string.chip_permission)
                color = Ui.warn
            }
            else -> {
                text = tr(R.string.chip_connected)
                color = Ui.good
            }
        }
        chipHolder.removeAllViews()
        chipHolder.addView(makeChip(text, color))
    }

    private fun updateNav() {
        for (i in navIcons.indices) {
            val active = i == tab
            navIcons[i].tint = if (active) Ui.accent else Ui.muted
            navLabels[i].setTextColor(if (active) Ui.accent else Ui.muted)
            navPills[i].background =
                if (active) roundRect(Ui.alpha(Ui.accent, 42), 100) else null
        }
    }

    private fun setStatus(message: String, kind: Kind = Kind.INFO, auto: Boolean = false) {
        statusMessage = message
        statusKind = kind
        statusIsAuto = auto
        updateStatusView()
    }

    private fun updateStatusView() {
        if (!::statusView.isInitialized) return
        val color = when (statusKind) {
            Kind.INFO -> Ui.white
            Kind.OK -> Ui.good
            Kind.WARN -> Ui.warn
            Kind.ERROR -> Ui.bad
        }
        statusView.text = statusMessage
        statusView.setTextColor(color)
        statusView.background = roundRect(Ui.panelDark, 16, Ui.alpha(color, 110))
    }

    // ---------- Page: Home ----------

    private fun buildHome() {
        val box = contentBox

        box.addView(label(tr(R.string.home_welcome), 12f, Ui.accent, style = 1, spacing = 0.35f))
        box.addView(
            label("CD REVIVE", 38f, Ui.white, style = 2).apply {
                setPadding(0, dp(4), 0, dp(4))
            }
        )
        box.addView(label(tr(R.string.home_intro), 15f, Ui.muted))
        gap(box, 18)

        box.addView(buildHero(), lp(Ui.MATCH, Ui.WRAP))
        gap(box, 14)

        box.addView(
            primaryButton(tr(R.string.home_choose_profile)) { goTo(1) },
            lp(Ui.MATCH, dp(58))
        )
        gap(box, 28)

        box.addView(label(tr(R.string.home_overview), 24f, Ui.white, style = 2))
        box.addView(
            label(tr(R.string.home_overview_sub), 14f, Ui.muted).apply {
                setPadding(0, dp(2), 0, dp(14))
            }
        )

        val gameLine = when {
            !gameInstalled -> tr(R.string.game_not_found)
            gameVersion != null -> tr(R.string.game_installed_v, gameVersion ?: "")
            else -> tr(R.string.game_installed)
        }
        val backupLine = when {
            !shizukuGranted -> tr(R.string.backup_need_shizuku)
            hasOriginal -> tr(R.string.backup_original_saved)
            else -> tr(R.string.backup_none_yet)
        }
        val fov = config?.let { c -> findFovKey(c)?.let { c[it] } }
        val visualLine = if (fov != null) tr(R.string.visuals_fov_line, fov) else tr(R.string.visuals_line)
        val fpsLine = config?.get("fpsLimit")?.let { tr(R.string.fps_line, it) }
            ?: tr(R.string.fps_line_unknown)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(
            hubCard(IconKind.PLAY, tr(R.string.hub_game), gameLine) { launchGame() },
            lp(0, Ui.MATCH, 1f).apply { rightMargin = dp(6) }
        )
        row1.addView(
            hubCard(IconKind.SHIELD, tr(R.string.hub_backup), backupLine) { goTo(4) },
            lp(0, Ui.MATCH, 1f).apply { leftMargin = dp(6) }
        )
        box.addView(row1, lp(Ui.MATCH, Ui.WRAP))
        gap(box, 12)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(
            hubCard(IconKind.EYE, tr(R.string.hub_visuals), visualLine) { goTo(2) },
            lp(0, Ui.MATCH, 1f).apply { rightMargin = dp(6) }
        )
        row2.addView(
            hubCard(IconKind.GAUGE, tr(R.string.hub_fps), fpsLine) { goTo(3) },
            lp(0, Ui.MATCH, 1f).apply { leftMargin = dp(6) }
        )
        box.addView(row2, lp(Ui.MATCH, Ui.WRAP))
        gap(box, 14)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            secondaryButton(tr(R.string.btn_start_game), IconKind.PLAY) { launchGame() },
            lp(0, dp(52), 1f).apply { rightMargin = dp(6) }
        )
        actions.addView(
            secondaryButton(tr(R.string.btn_stop_game), IconKind.STOP) { stopGame() },
            lp(0, dp(52), 1f).apply { leftMargin = dp(6) }
        )
        box.addView(actions, lp(Ui.MATCH, Ui.WRAP))
    }

    private fun goTo(index: Int) {
        tab = index
        render()
    }

    private fun buildHero(): View {
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(34, 66, 120), Color.rgb(48, 30, 96))
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Ui.alpha(Color.WHITE, 30))
            }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }

        val cfg = config
        val active = activeProfile()

        inner.addView(label(tr(R.string.hero_active), 11f, Ui.accent, style = 1, spacing = 0.3f))

        val title = when {
            cfg == null -> tr(R.string.hero_unavailable)
            active != null -> tr(active.nameRes)
            else -> tr(R.string.hero_custom)
        }
        inner.addView(
            label(title.uppercase(), 30f, Ui.white, style = 2).apply {
                maxLines = 1
                setPadding(0, dp(6), 0, dp(4))
            }
        )

        val desc = when {
            !shizukuRunning -> tr(R.string.hero_no_shizuku)
            !shizukuGranted -> tr(R.string.hero_no_permission)
            configProblem != null -> configProblem.orEmpty()
            active != null -> tr(active.descriptionRes)
            cfg != null -> tr(R.string.hero_custom_desc)
            else -> tr(R.string.hero_loading)
        }
        inner.addView(label(desc, 14f, Color.rgb(196, 206, 228)))

        if (cfg != null) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(16), 0, 0)
            }
            for (c in valueChips(cfg)) {
                row.addView(smallChip(c), lp(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(8) })
            }
            inner.addView(row)
        }

        if (!shizukuGranted) {
            gap(inner, 16)
            inner.addView(
                secondaryButton(tr(R.string.btn_setup_shizuku)) { goTo(5) },
                lp(Ui.MATCH, dp(50))
            )
        }

        hero.addView(inner, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        return hero
    }

    // ---------- Page: Profiles ----------

    private fun buildProfiles() {
        val box = contentBox
        box.addView(sectionHeader(tr(R.string.profiles_title), tr(R.string.profiles_sub)))

        val active = activeProfile()

        for (p in profiles) {
            val isActive = active === p
            val card = card()

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(
                label(tr(p.nameRes).uppercase(), 20f, Ui.white, style = 2).apply { maxLines = 1 },
                lp(0, Ui.WRAP, 1f)
            )
            if (isActive) {
                top.addView(smallChip(tr(R.string.badge_active), Ui.good), lp(Ui.WRAP, Ui.WRAP))
            }
            card.addView(top, lp(Ui.MATCH, Ui.WRAP))

            card.addView(
                label(tr(p.subtitleRes), 13f, Ui.accent, style = 1).apply {
                    setPadding(0, dp(4), 0, dp(6))
                }
            )
            card.addView(label(tr(p.descriptionRes), 13f, Ui.muted))

            val chips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, dp(14))
            }
            for (c in valueChips(p.values)) {
                chips.addView(smallChip(c), lp(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(8) })
            }
            card.addView(chips)

            if (isActive) {
                card.addView(
                    secondaryButton(tr(R.string.btn_reapply)) { applyProfile(p) },
                    lp(Ui.MATCH, dp(52))
                )
            } else {
                card.addView(
                    primaryButton(tr(R.string.btn_apply)) { applyProfile(p) },
                    lp(Ui.MATCH, dp(52))
                )
            }

            box.addView(card, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })
        }

        val optionCard = card()
        optionCard.addView(
            toggleRow(
                tr(R.string.opt_launch_title),
                tr(R.string.opt_launch_desc),
                prefs.getBoolean(PREF_LAUNCH, false)
            ) { on ->
                prefs.edit().putBoolean(PREF_LAUNCH, on).apply()
            }
        )
        box.addView(optionCard, lp(Ui.MATCH, Ui.WRAP))

        box.addView(
            label(tr(R.string.profiles_note), 11f, Ui.muted).apply {
                setPadding(0, dp(16), 0, 0)
            }
        )
    }

    // ---------- Page: Visuals ----------

    private fun buildVisuals() {
        val box = contentBox
        box.addView(sectionHeader(tr(R.string.visuals_title), tr(R.string.visuals_sub)))

        val gap14 = { lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) } }

        // Flat look shortcut
        val flat = profiles[2]
        val flatCard = card()
        flatCard.addView(label(tr(R.string.flat_title), 12f, Ui.accent, style = 1, spacing = 0.3f))
        flatCard.addView(
            label(tr(R.string.flat_desc), 13f, Ui.muted).apply { setPadding(0, dp(6), 0, dp(14)) }
        )
        flatCard.addView(
            if (activeProfile() === flat) {
                secondaryButton(tr(R.string.btn_reapply)) { applyProfile(flat) }
            } else {
                primaryButton(tr(R.string.flat_button)) { applyProfile(flat) }
            },
            lp(Ui.MATCH, dp(52))
        )
        box.addView(flatCard, gap14())

        val cfg = config
        if (cfg == null) {
            val c = card()
            c.addView(label(configProblem ?: tr(R.string.config_no_data), 13f, Ui.muted))
            box.addView(c, gap14())
        } else {
            box.addView(
                buildChoiceCard(
                    R.string.q_title, R.string.q_desc, "quality",
                    listOf("auto", "lowest", "low", "mid", "high", "best"), cfg
                ),
                gap14()
            )
            box.addView(
                buildSliderCard(
                    R.string.scale_title, R.string.scale_desc, "renderScale", cfg["renderScale"],
                    0.5f, 1.0f, 0.05f, listOf("0.5", "0.65", "0.8", "1.0", "auto")
                ) { v -> fmtScale(v) },
                gap14()
            )
            box.addView(
                buildSliderCard(
                    R.string.dist_title, R.string.dist_desc, "viewDistance", cfg["viewDistance"],
                    50f, 100f, 5f, listOf("50", "65", "80", "100")
                ) { v -> v.toInt().toString() },
                gap14()
            )

            val details = card()
            details.addView(
                toggleRow(
                    tr(R.string.opt_shadows), tr(R.string.opt_shadows_desc),
                    cfg["shadows"].equals("on", ignoreCase = true)
                ) { on ->
                    applyValues(tr(R.string.opt_shadows), mapOf("shadows" to if (on) "on" else "off"))
                }
            )
            gap(details, 16)
            details.addView(
                toggleRow(
                    tr(R.string.opt_bloom), tr(R.string.opt_bloom_desc),
                    cfg["bloom"].equals("on", ignoreCase = true)
                ) { on ->
                    applyValues(tr(R.string.opt_bloom), mapOf("bloom" to if (on) "on" else "off"))
                }
            )
            box.addView(details, gap14())

            box.addView(
                buildChoiceCard(
                    R.string.aa_title, R.string.aa_desc, "antiAliasing",
                    listOf("auto", "off", "2", "4"), cfg
                ),
                gap14()
            )
            box.addView(
                buildChoiceCard(
                    R.string.fx_title, R.string.fx_desc, "effects",
                    listOf("auto", "low", "mid", "high"), cfg
                ),
                gap14()
            )
        }

        val na = card()
        na.addView(label(tr(R.string.na_title), 12f, Ui.warn, style = 1, spacing = 0.3f))
        na.addView(
            label(tr(R.string.na_desc), 13f, Ui.muted).apply { setPadding(0, dp(6), 0, 0) }
        )
        box.addView(na, lp(Ui.MATCH, Ui.WRAP))
    }

    private fun fmtScale(v: Float): String {
        val t = String.format(java.util.Locale.US, "%.2f", v).trimEnd('0')
        return if (t.endsWith(".")) t + "0" else t
    }

    private fun optionText(o: String): String = when {
        o.equals("auto", ignoreCase = true) -> tr(R.string.opt_auto)
        o.equals("off", ignoreCase = true) -> tr(R.string.opt_off)
        o.isNotEmpty() && o.all { it.isDigit() } -> o + "x"
        else -> o.replaceFirstChar { it.uppercase() }
    }

    private fun buildChoiceCard(
        titleRes: Int,
        descRes: Int,
        key: String,
        options: List<String>,
        cfg: Map<String, String>
    ): View {
        val c = card()
        c.addView(label(tr(titleRes), 12f, Ui.accent, style = 1, spacing = 0.3f))
        c.addView(
            label(tr(descRes), 13f, Ui.muted).apply { setPadding(0, dp(6), 0, dp(12)) }
        )
        val current = cfg[key]
        for (rowItems in options.chunked(3)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((index, option) in rowItems.withIndex()) {
                val chip = choiceChip(optionText(option), current.equals(option, ignoreCase = true)) {
                    applyValues(tr(titleRes), mapOf(key to option))
                }
                row.addView(
                    chip,
                    lp(0, dp(46), 1f).apply {
                        leftMargin = if (index == 0) 0 else dp(4)
                        rightMargin = if (index == rowItems.size - 1) 0 else dp(4)
                    }
                )
            }
            c.addView(row, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(8) })
        }
        return c
    }

    private fun buildSliderCard(
        titleRes: Int,
        descRes: Int,
        key: String,
        raw: String?,
        lo: Float,
        hi: Float,
        stepBy: Float,
        presets: List<String>,
        fmt: (Float) -> String
    ): View {
        val c = card()
        c.addView(label(tr(titleRes), 12f, Ui.accent, style = 1, spacing = 0.3f))
        c.addView(
            label(tr(descRes), 13f, Ui.muted).apply { setPadding(0, dp(6), 0, 0) }
        )

        val cur = raw?.toFloatOrNull()
        val shown = when {
            raw == null -> "-"
            cur == null -> optionText(raw)
            else -> raw
        }
        val valueLabel = label(shown, 34f, Ui.white, style = 2).apply {
            setPadding(0, dp(10), 0, 0)
        }
        c.addView(valueLabel)

        val slider = SliderView(this).apply {
            this.min = lo
            this.max = hi
            this.step = stepBy
            value = cur ?: hi
            onDrag = { v -> valueLabel.text = fmt(v) }
            onCommit = { v -> safe { applyValues(tr(titleRes), mapOf(key to fmt(v))) } }
        }
        c.addView(slider, lp(Ui.MATCH, dp(44)).apply { topMargin = dp(6) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, p) in presets.withIndex()) {
            val selected = raw.equals(p, ignoreCase = true) ||
                (cur != null && p.toFloatOrNull() == cur)
            val text = if (p.equals("auto", ignoreCase = true)) tr(R.string.opt_auto) else p
            val chip = choiceChip(text, selected) {
                applyValues(tr(titleRes), mapOf(key to p))
            }
            row.addView(
                chip,
                lp(0, dp(44), 1f).apply {
                    leftMargin = if (i == 0) 0 else dp(4)
                    rightMargin = if (i == presets.size - 1) 0 else dp(4)
                }
            )
        }
        c.addView(row, lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(10) })
        return c
    }

    private fun buildFovCard(): View {
        val c = card()
        c.addView(label(tr(R.string.fov_title), 12f, Ui.accent, style = 1, spacing = 0.3f))

        val cfg = config
        val key = cfg?.let { findFovKey(it) }
        val raw = if (key != null) cfg?.get(key) else null
        val cur = raw?.toFloatOrNull()

        if (cfg == null) {
            c.addView(
                label(configProblem ?: tr(R.string.config_no_data), 13f, Ui.muted).apply {
                    setPadding(0, dp(8), 0, 0)
                }
            )
            return c
        }
        if (key == null || raw == null) {
            c.addView(
                label(tr(R.string.fov_missing), 13f, Ui.muted).apply { setPadding(0, dp(8), 0, 0) }
            )
            return c
        }
        if (cur == null || cur < 30f || cur > 179f) {
            c.addView(
                label(tr(R.string.fov_unusual, key, raw), 13f, Ui.muted).apply {
                    setPadding(0, dp(8), 0, 0)
                }
            )
            return c
        }

        val asFloat = raw.contains('.')
        fun fmt(v: Float): String = if (asFloat) String.format(java.util.Locale.US, "%.1f", v) else v.toInt().toString()

        val valueLabel = label(fmt(cur), 34f, Ui.white, style = 2).apply {
            setPadding(0, dp(6), 0, 0)
        }
        c.addView(valueLabel)
        c.addView(label(tr(R.string.fov_key, key), 12f, Ui.muted))

        val slider = SliderView(this).apply {
            min = minOf(50f, cur)
            max = maxOf(140f, cur)
            step = 1f
            value = cur
            onDrag = { v -> valueLabel.text = fmt(v) }
            onCommit = { v ->
                safe { applyValues(tr(R.string.fov_title_short), mapOf(key to fmt(v))) }
            }
        }
        c.addView(slider, lp(Ui.MATCH, dp(44)).apply { topMargin = dp(8) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val presets = listOf(90f, 100f, 110f, 120f)
        for ((i, v) in presets.withIndex()) {
            val chip = choiceChip(fmt(v), cur.toInt() == v.toInt()) {
                applyValues(tr(R.string.fov_title_short), mapOf(key to fmt(v)))
            }
            row.addView(
                chip,
                lp(0, dp(44), 1f).apply {
                    leftMargin = if (i == 0) 0 else dp(4)
                    rightMargin = if (i == presets.size - 1) 0 else dp(4)
                }
            )
        }
        c.addView(row, lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(10) })
        c.addView(
            label(tr(R.string.fov_note), 12f, Ui.muted).apply { setPadding(0, dp(12), 0, 0) }
        )
        return c
    }

    private fun buildStretchCard(): View {
        val c = card()
        c.addView(label(tr(R.string.stretch_title), 12f, Ui.accent, style = 1, spacing = 0.3f))
        c.addView(
            label(tr(R.string.stretch_desc), 13f, Ui.muted).apply { setPadding(0, dp(6), 0, dp(12)) }
        )

        val phys = physSize
        val cur = curSize
        if (!shizukuGranted || phys == null || cur == null) {
            c.addView(label(tr(R.string.stretch_need_shizuku), 13f, Ui.warn))
            return c
        }

        c.addView(
            infoRow(
                tr(R.string.stretch_current),
                "${cur.first}x${cur.second}" +
                    if (hasOverride) "" else " (${tr(R.string.stretch_native)})"
            )
        )
        gap(c, 8)

        if (pendingRevert) {
            c.addView(label(tr(R.string.stretch_confirm, countdown), 13f, Ui.warn))
            gap(c, 10)
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(
                primaryButton(tr(R.string.btn_keep)) { keepStretch() },
                lp(0, dp(52), 1f).apply { rightMargin = dp(6) }
            )
            r.addView(
                secondaryButton(tr(R.string.btn_revert)) { resetStretch() },
                lp(0, dp(52), 1f).apply { leftMargin = dp(6) }
            )
            c.addView(r, lp(Ui.MATCH, Ui.WRAP))
            return c
        }

        val ratios = listOf(16 to 10, 3 to 2, 4 to 3, 5 to 4)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, r) in ratios.withIndex()) {
            val size = stretchSize(phys, r.first, r.second)
            val selected = hasOverride && cur == size
            val chip = choiceChip("${r.first}:${r.second}", selected) { applyStretch(size) }
            row.addView(
                chip,
                lp(0, dp(48), 1f).apply {
                    leftMargin = if (i == 0) 0 else dp(4)
                    rightMargin = if (i == ratios.size - 1) 0 else dp(4)
                }
            )
        }
        c.addView(row, lp(Ui.MATCH, Ui.WRAP))
        gap(c, 10)
        c.addView(
            secondaryButton(tr(R.string.btn_native_res)) { resetStretch() },
            lp(Ui.MATCH, dp(48))
        )
        c.addView(
            label(tr(R.string.stretch_note), 12f, Ui.muted).apply { setPadding(0, dp(12), 0, 0) }
        )
        return c
    }

    private fun buildCategoryCard(cat: Category, cfg: Map<String, String>): View {
        val c = card()
        c.addView(label(tr(cat.titleRes).uppercase(), 15f, Ui.white, style = 2, spacing = 0.05f))
        c.addView(
            label(tr(cat.descriptionRes), 13f, Ui.muted).apply { setPadding(0, dp(4), 0, dp(10)) }
        )

        val found = proposals(cat, cfg)
        if (found.isEmpty()) {
            c.addView(label(tr(R.string.detected_none), 13f, Ui.muted))
            return c
        }

        val shown = found.take(6)
        for (p in shown) {
            val right = if (p.target != null) "${p.current}  →  ${p.target}" else p.current
            c.addView(infoRow(p.key, right, if (p.target != null) Ui.accent else Ui.muted, 12f))
        }
        if (found.size > shown.size) {
            c.addView(
                label(tr(R.string.detected_more, found.size - shown.size), 12f, Ui.muted).apply {
                    setPadding(0, dp(4), 0, 0)
                }
            )
        }

        val actionable = found.filter { it.target != null }
        gap(c, 12)
        if (actionable.isEmpty()) {
            c.addView(label(tr(R.string.detected_manual), 12f, Ui.muted))
        } else {
            val map = LinkedHashMap<String, String>()
            for (p in actionable) map[p.key] = p.target ?: continue
            c.addView(
                primaryButton(tr(R.string.btn_minimize, actionable.size)) {
                    applyValues(tr(cat.titleRes), map)
                },
                lp(Ui.MATCH, dp(50))
            )
            if (found.size > actionable.size) {
                c.addView(
                    label(tr(R.string.detected_manual), 12f, Ui.muted).apply {
                        setPadding(0, dp(10), 0, 0)
                    }
                )
            }
        }
        return c
    }

    private fun proposals(cat: Category, cfg: Map<String, String>): List<Proposal> {
        val result = ArrayList<Proposal>()
        for ((k, v) in cfg) {
            val lower = k.lowercase()
            if (cat.keywords.none { lower.contains(it) }) continue
            if (lower.contains("fov")) continue
            result.add(Proposal(k, v, minimalValue(k, v)))
        }
        return result
    }

    /** Safe "lowest" value for a setting, or null if it must be edited by hand. */
    private fun minimalValue(key: String, current: String): String? {
        val v = current.trim().lowercase()
        val lower = key.lowercase()
        val inverted = lower.contains("disable") || lower.contains("hide") || lower.contains("skip")
        val target = when (v) {
            "true" -> if (inverted) null else "false"
            "false" -> if (inverted) "true" else null
            "on" -> if (inverted) null else "off"
            "off" -> if (inverted) "on" else null
            "ultra", "high", "medium", "normal", "epic", "veryhigh", "very_high", "max" -> "low"
            else -> null
        }
        return target?.let { matchCase(current.trim(), it) }
    }

    private fun matchCase(example: String, value: String): String {
        return when {
            example.length > 1 && example == example.uppercase() -> value.uppercase()
            example.isNotEmpty() && example[0].isUpperCase() ->
                value.replaceFirstChar { it.uppercase() }
            else -> value
        }
    }

    private fun findFovKey(cfg: Map<String, String>): String? {
        val exact = listOf("fov", "FOV", "fieldOfView", "cameraFov", "cameraFOV", "fovValue")
        for (k in exact) if (cfg.containsKey(k)) return k
        return cfg.keys.firstOrNull {
            val l = it.lowercase()
            l.contains("fov") || l.contains("fieldofview")
        }
    }

    private fun stretchSize(phys: Pair<Int, Int>, a: Int, b: Int): Pair<Int, Int> {
        val short = minOf(phys.first, phys.second)
        val stretched = (short * a / b) / 2 * 2
        return if (phys.first <= phys.second) short to stretched else stretched to short
    }

    // ---------- Page: Performance ----------

    private fun buildPerformance() {
        val box = contentBox
        box.addView(sectionHeader(tr(R.string.perf_title), tr(R.string.perf_sub)))

        val cfg = config
        val currentFps = cfg?.get("fpsLimit")

        val fpsCard = card()
        fpsCard.addView(label(tr(R.string.fps_limit), 12f, Ui.accent, style = 1, spacing = 0.3f))
        fpsCard.addView(
            label(
                if (currentFps != null) tr(R.string.fps_current, currentFps)
                else tr(R.string.fps_current_unknown),
                14f,
                Ui.muted
            ).apply { setPadding(0, dp(4), 0, dp(14)) }
        )

        val options = listOf("30", "45", "60", "90", "120", "default")
        for (rowItems in options.chunked(3)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((index, option) in rowItems.withIndex()) {
                val isDefault = option == "default"
                val fps = if (isDefault) "240" else option
                val lobby = if (isDefault) "120" else option
                val text = if (isDefault) tr(R.string.fps_default) else option
                val selected = currentFps == fps
                val chip = choiceChip(text, selected) {
                    applyValues(
                        tr(R.string.fps_applied_title, text),
                        mapOf("fpsLimit" to fps, "lobbyFps" to lobby)
                    )
                }
                row.addView(
                    chip,
                    lp(0, dp(48), 1f).apply {
                        leftMargin = if (index == 0) 0 else dp(4)
                        rightMargin = if (index == rowItems.size - 1) 0 else dp(4)
                    }
                )
            }
            fpsCard.addView(row, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(8) })
        }
        fpsCard.addView(
            label(tr(R.string.fps_note), 12f, Ui.muted).apply { setPadding(0, dp(6), 0, 0) }
        )
        box.addView(fpsCard, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })

        val autoOn = cfg?.get("autoLowerQuality")?.equals("true", ignoreCase = true) ?: false
        val below = cfg?.get("autoLowerBelowFps") ?: "25"

        val autoCard = card()
        autoCard.addView(
            toggleRow(
                tr(R.string.auto_title),
                tr(R.string.auto_desc, below),
                autoOn
            ) { on ->
                applyValues(
                    tr(R.string.auto_title_short),
                    mapOf("autoLowerQuality" to if (on) "true" else "false")
                )
            }
        )
        box.addView(autoCard, lp(Ui.MATCH, Ui.WRAP))
    }

    // ---------- Page: Config (backup + explorer) ----------

    private fun buildConfig() {
        val box = contentBox
        box.addView(sectionHeader(tr(R.string.config_title), tr(R.string.config_sub)))

        val originalCard = card()
        originalCard.addView(label(tr(R.string.backup_original), 12f, Ui.accent, style = 1, spacing = 0.3f))
        originalCard.addView(
            label(
                if (hasOriginal) tr(R.string.state_saved) else tr(R.string.state_missing),
                20f,
                if (hasOriginal) Ui.good else Ui.warn,
                style = 2
            ).apply { setPadding(0, dp(6), 0, dp(4)) }
        )
        originalCard.addView(label(tr(R.string.backup_original_desc), 13f, Ui.muted))
        gap(originalCard, 14)
        originalCard.addView(
            secondaryButton(tr(R.string.btn_restore_original)) {
                restore(backupPath, tr(R.string.backup_original))
            },
            lp(Ui.MATCH, dp(52))
        )
        box.addView(originalCard, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })

        val lastCard = card()
        lastCard.addView(label(tr(R.string.backup_last), 12f, Ui.accent, style = 1, spacing = 0.3f))
        lastCard.addView(
            label(
                if (hasLast) tr(R.string.state_available) else tr(R.string.state_missing),
                20f,
                if (hasLast) Ui.good else Ui.warn,
                style = 2
            ).apply { setPadding(0, dp(6), 0, dp(4)) }
        )
        lastCard.addView(label(tr(R.string.backup_last_desc), 13f, Ui.muted))
        gap(lastCard, 14)
        lastCard.addView(
            secondaryButton(tr(R.string.btn_restore_last)) {
                restore(lastBackupPath, tr(R.string.backup_last))
            },
            lp(Ui.MATCH, dp(52))
        )
        box.addView(lastCard, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })

        box.addView(buildExplorerCard(), lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })

        val diag = card()
        diag.addView(label(tr(R.string.diag_title), 12f, Ui.accent, style = 1, spacing = 0.3f))
        diag.addView(
            label(tr(R.string.diag_desc), 13f, Ui.muted).apply { setPadding(0, dp(6), 0, dp(12)) }
        )
        val diagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        diagRow.addView(
            secondaryButton(tr(R.string.btn_copy)) { collectDiagnostics(share = false) },
            lp(0, dp(50), 1f).apply { rightMargin = dp(6) }
        )
        diagRow.addView(
            secondaryButton(tr(R.string.btn_share)) { collectDiagnostics(share = true) },
            lp(0, dp(50), 1f).apply { leftMargin = dp(6) }
        )
        diag.addView(diagRow, lp(Ui.MATCH, Ui.WRAP))
        box.addView(diag, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })

        box.addView(
            secondaryButton(tr(R.string.btn_reset_defaults)) {
                val defaults = HashMap<String, String>(profiles[0].values)
                defaults["fpsLimit"] = "240"
                defaults["lobbyFps"] = "120"
                defaults["autoLowerQuality"] = "true"
                applyValues(tr(R.string.reset_title), defaults)
            },
            lp(Ui.MATCH, dp(52)).apply { bottomMargin = dp(10) }
        )
        box.addView(
            secondaryButton(tr(R.string.btn_reload)) { refreshAll(resetStatus = true) },
            lp(Ui.MATCH, dp(52))
        )
    }

    private fun buildExplorerCard(): View {
        val c = card()
        c.addView(label(tr(R.string.explorer_title), 12f, Ui.accent, style = 1, spacing = 0.3f))
        c.addView(
            label(tr(R.string.explorer_desc), 13f, Ui.muted).apply { setPadding(0, dp(6), 0, dp(12)) }
        )

        val cfg = config
        if (cfg == null) {
            c.addView(label(configProblem ?: tr(R.string.config_no_data), 13f, Ui.muted))
            return c
        }

        val search = EditText(this).apply {
            hint = tr(R.string.explorer_search)
            setHintTextColor(Ui.muted)
            setTextColor(Ui.white)
            textSize = 14f
            setSingleLine(true)
            background = roundRect(Ui.panelDark, 14, Ui.stroke)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(exploreFilter)
        }
        c.addView(search, lp(Ui.MATCH, Ui.WRAP))
        gap(c, 8)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list, lp(Ui.MATCH, Ui.WRAP))

        fun fill() {
            list.removeAllViews()
            val f = exploreFilter.trim().lowercase()
            val all = cfg.entries.filter {
                f.isEmpty() || it.key.lowercase().contains(f) || it.value.lowercase().contains(f)
            }
            val shown = all.take(MAX_ROWS)
            if (all.isEmpty()) {
                list.addView(label(tr(R.string.explorer_empty), 13f, Ui.muted).apply {
                    setPadding(0, dp(8), 0, dp(4))
                })
                return
            }
            list.addView(
                label(tr(R.string.explorer_count, all.size), 11f, Ui.muted).apply {
                    setPadding(0, dp(4), 0, dp(4))
                }
            )
            for (e in shown) {
                list.addView(explorerRow(e.key, e.value))
            }
            if (all.size > shown.size) {
                list.addView(
                    label(tr(R.string.explorer_more, MAX_ROWS), 12f, Ui.muted).apply {
                        setPadding(0, dp(8), 0, 0)
                    }
                )
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                exploreFilter = s?.toString() ?: ""
                fill()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        fill()
        return c
    }

    private fun explorerRow(key: String, value: String): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(11), dp(10), dp(11))
            background = ripple(roundRect(0, 12), 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { safe { editValue(key, value) } }
        }
        r.addView(label(key, 13f, Ui.white, style = 1), lp(0, Ui.WRAP, 1f))
        r.addView(
            label(value, 13f, Ui.accent).apply {
                gravity = Gravity.END
                maxLines = 1
            },
            lp(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(10) }
        )
        return r
    }

    private fun editValue(key: String, current: String) {
        val input = EditText(this).apply {
            setText(current)
            setSingleLine(true)
            setSelection(current.length)
        }
        val holder = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        AlertDialog.Builder(this)
            .setTitle(key)
            .setView(holder)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isEmpty()) {
                    setStatus(tr(R.string.value_empty), Kind.WARN)
                } else {
                    applyValues(key, mapOf(key to value))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Page: System ----------

    private fun buildSystem() {
        val box = contentBox
        box.addView(sectionHeader(tr(R.string.system_title), tr(R.string.system_sub)))

        val shizukuCard = card()
        shizukuCard.addView(label("SHIZUKU", 12f, Ui.accent, style = 1, spacing = 0.3f))
        gap(shizukuCard, 8)
        shizukuCard.addView(
            infoRow(
                tr(R.string.sys_service),
                if (shizukuRunning) tr(R.string.sys_running) else tr(R.string.chip_disconnected),
                if (shizukuRunning) Ui.good else Ui.bad
            )
        )
        shizukuCard.addView(
            infoRow(
                tr(R.string.sys_permission),
                if (shizukuGranted) tr(R.string.sys_granted) else tr(R.string.sys_missing),
                if (shizukuGranted) Ui.good else Ui.warn
            )
        )
        gap(shizukuCard, 12)
        if (!shizukuGranted) {
            shizukuCard.addView(
                primaryButton(tr(R.string.btn_request_permission)) { requestPermission() },
                lp(Ui.MATCH, dp(54))
            )
            gap(shizukuCard, 10)
        }
        shizukuCard.addView(
            secondaryButton(tr(R.string.btn_check_connection)) { refreshAll(resetStatus = true) },
            lp(Ui.MATCH, dp(52))
        )
        shizukuCard.addView(
            label(tr(R.string.shizuku_restart_note), 12f, Ui.muted).apply {
                setPadding(0, dp(12), 0, 0)
            }
        )
        box.addView(shizukuCard, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })

        val gameCard = card()
        gameCard.addView(label("CD REVIVE", 12f, Ui.accent, style = 1, spacing = 0.3f))
        gap(gameCard, 8)
        gameCard.addView(
            infoRow(
                tr(R.string.sys_installation),
                if (gameInstalled) tr(R.string.sys_found) else tr(R.string.game_not_found),
                if (gameInstalled) Ui.good else Ui.bad
            )
        )
        if (gameVersion != null) {
            gameCard.addView(infoRow(tr(R.string.sys_version), gameVersion.orEmpty()))
        }
        val running = gameRunning
        gameCard.addView(
            infoRow(
                tr(R.string.sys_status),
                when (running) {
                    true -> tr(R.string.sys_running)
                    false -> tr(R.string.sys_closed)
                    null -> tr(R.string.sys_unknown)
                },
                if (running == true) Ui.warn else Ui.white
            )
        )
        gap(gameCard, 12)
        val gameActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gameActions.addView(
            secondaryButton(tr(R.string.btn_start), IconKind.PLAY) { launchGame() },
            lp(0, dp(52), 1f).apply { rightMargin = dp(6) }
        )
        gameActions.addView(
            secondaryButton(tr(R.string.btn_stop), IconKind.STOP) { stopGame() },
            lp(0, dp(52), 1f).apply { leftMargin = dp(6) }
        )
        gameCard.addView(gameActions, lp(Ui.MATCH, Ui.WRAP))
        box.addView(gameCard, lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(14) })

        val infoCard = card()
        infoCard.addView(label(tr(R.string.sys_information), 12f, Ui.accent, style = 1, spacing = 0.3f))
        gap(infoCard, 8)
        infoCard.addView(infoRow(tr(R.string.sys_app_version), appVersion))
        infoCard.addView(infoRow(tr(R.string.sys_device), "${Build.MANUFACTURER} ${Build.MODEL}"))
        infoCard.addView(infoRow("Android", Build.VERSION.RELEASE))
        infoCard.addView(
            label(tr(R.string.sys_config_file), 12f, Ui.muted).apply { setPadding(0, dp(10), 0, dp(4)) }
        )
        infoCard.addView(label(configPath, 11f, Ui.white))
        infoCard.addView(
            label(tr(R.string.sys_language_note), 12f, Ui.muted).apply { setPadding(0, dp(12), 0, 0) }
        )
        box.addView(infoCard, lp(Ui.MATCH, Ui.WRAP))
    }

    // ---------- UI building blocks ----------

    private fun tr(id: Int, vararg args: Any): String =
        if (args.isEmpty()) getString(id) else getString(id, *args)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun lp(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height, weight)

    private fun gap(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), lp(1, dp(heightDp)))
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        style: Int = 0,
        spacing: Float = 0f
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            letterSpacing = spacing
            typeface = when (style) {
                2 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
                1 -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
        }
    }

    private fun roundRect(color: Int, radiusDp: Int, strokeColor: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != 0) setStroke(dp(1), strokeColor)
        }
    }

    private fun accentGradient(radiusDp: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Ui.accentLight, Ui.accent2)
        ).apply { cornerRadius = dp(radiusDp).toFloat() }
    }

    private fun ripple(content: GradientDrawable, radiusDp: Int): Drawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(radiusDp).toFloat()
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.argb(70, 255, 255, 255)),
            content,
            mask
        )
    }

    /** Small press animation for tappable views. */
    private fun pressable(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.panelLight, Ui.panel)
            ).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), Ui.stroke)
            }
        }
    }

    private fun sectionHeader(title: String, subtitle: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(18))
        }
        col.addView(label(title.uppercase(), 30f, Ui.white, style = 2))
        col.addView(
            View(this).apply { background = accentGradient(3) },
            lp(dp(44), dp(4)).apply { topMargin = dp(6); bottomMargin = dp(8) }
        )
        col.addView(label(subtitle, 14f, Ui.muted))
        return col
    }

    private fun makeChip(text: String, color: Int): LinearLayout {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundRect(Ui.panelDark, 100, Ui.stroke)
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        chip.addView(dot, lp(dp(8), dp(8)).apply { rightMargin = dp(8) })
        chip.addView(label(text, 12f, Ui.white, style = 1))
        return chip
    }

    private fun smallChip(text: String, color: Int = Ui.accent): View {
        return label(text, 11f, color, style = 1).apply {
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundRect(Ui.alpha(color, 36), 100, Ui.alpha(color, 90))
        }
    }

    private fun primaryButton(text: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
            background = ripple(accentGradient(22), 22)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                safe(onClick)
            }
        }
        pressable(row)
        row.addView(
            label(text.uppercase(), 15f, Ui.dark, style = 2, spacing = 0.12f).apply { maxLines = 2 },
            lp(0, Ui.WRAP, 1f)
        )
        row.addView(
            IconView(this, IconKind.ARROW).apply { tint = Ui.dark },
            lp(dp(24), dp(24))
        )
        return row
    }

    private fun secondaryButton(
        text: String,
        icon: IconKind? = null,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = ripple(roundRect(Ui.panelLight, 18, Ui.stroke), 18)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                safe(onClick)
            }
        }
        pressable(row)
        if (icon != null) {
            row.addView(
                IconView(this, icon).apply { tint = Ui.accent },
                lp(dp(18), dp(18)).apply { rightMargin = dp(8) }
            )
        }
        row.addView(
            label(text.uppercase(), 12f, Ui.white, style = 1, spacing = 0.08f).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }
        )
        return row
    }

    private fun choiceChip(text: String, selected: Boolean, onClick: () -> Unit): View {
        return label(text, 14f, if (selected) Ui.dark else Ui.white, style = 1).apply {
            gravity = Gravity.CENTER
            background = if (selected) {
                ripple(accentGradient(16), 16)
            } else {
                ripple(roundRect(Ui.panelDark, 16, Ui.stroke), 16)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                safe(onClick)
            }
            pressable(this)
        }
    }

    private fun hubCard(
        icon: IconKind,
        title: String,
        desc: String,
        onClick: () -> Unit
    ): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = ripple(roundRect(Ui.panel, 24, Ui.stroke), 24)
            isClickable = true
            isFocusable = true
            setOnClickListener { safe(onClick) }
        }
        pressable(c)
        c.addView(IconView(this, icon).apply { tint = Ui.accent }, lp(dp(28), dp(28)))
        c.addView(
            label(title, 16f, Ui.white, style = 2, spacing = 0.05f).apply {
                setPadding(0, dp(16), 0, dp(4))
            }
        )
        c.addView(label(desc, 12f, Ui.muted))
        return c
    }

    private fun infoRow(
        key: String,
        value: String,
        color: Int = Ui.white,
        size: Float = 14f
    ): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, dp(7))
        }
        r.addView(label(key, size, Ui.muted), lp(0, Ui.WRAP, 1f).apply { rightMargin = dp(10) })
        r.addView(label(value, size, color, style = 1).apply { gravity = Gravity.END })
        return r
    }

    private fun toggleRow(
        title: String,
        desc: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 15f, Ui.white, style = 1))
        texts.addView(label(desc, 12f, Ui.muted).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(texts, lp(0, Ui.WRAP, 1f).apply { rightMargin = dp(14) })

        val toggle = ToggleView(this)
        toggle.checked = checked
        row.addView(toggle, lp(dp(52), dp(30)))

        row.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val next = !toggle.checked
            toggle.checked = next
            safe { onChange(next) }
        }
        return row
    }

    private fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            setStatus(tr(R.string.error_prefix, friendly(e)), Kind.ERROR)
        }
    }

    // ---------- Evaluation ----------

    private fun valueChips(values: Map<String, String>): List<String> {
        fun pretty(v: String): String = when (v.lowercase()) {
            "auto" -> tr(R.string.opt_auto)
            "off" -> tr(R.string.opt_off)
            "on" -> tr(R.string.opt_on)
            else -> v
        }

        val render = values["renderScale"] ?: "auto"
        val renderText = if (render.lowercase() == "auto") tr(R.string.opt_auto) else render + "x"
        val shadows = pretty(values["shadows"] ?: "auto")
        val view = (values["viewDistance"] ?: "100") + "%"

        return listOf(
            tr(R.string.chip_render, renderText),
            tr(R.string.chip_shadows, shadows),
            tr(R.string.chip_view, view)
        )
    }

    private fun activeProfile(): Profile? {
        val cfg = config ?: return null
        return profiles.firstOrNull { p ->
            p.values.all { (k, v) -> cfg[k].equals(v, ignoreCase = true) }
        }
    }

    private fun parseIni(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("[")) {
                return@forEach
            }
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach

            val key = line.substring(0, eq).trim()
            var value = line.substring(eq + 1)
            val comment = value.indexOf(';')
            if (comment >= 0) value = value.substring(0, comment)
            result[key] = value.trim()
        }
        return result
    }

    private fun parseSize(text: String, label: String): Pair<Int, Int>? {
        val m = Regex(label + "[^\\d]*(\\d+)x(\\d+)").find(text) ?: return null
        val w = m.groupValues[1].toIntOrNull() ?: return null
        val h = m.groupValues[2].toIntOrNull() ?: return null
        return w to h
    }

    private fun friendly(t: Throwable): String {
        val m = t.message ?: t.javaClass.simpleName
        return when {
            m.contains("No such file", ignoreCase = true) -> tr(R.string.err_no_file)
            m.contains("Permission denied", ignoreCase = true) -> tr(R.string.err_denied)
            else -> m
        }
    }

    // ---------- Shizuku, game, shell ----------

    private fun updateShizukuState() {
        shizukuRunning = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        shizukuGranted = shizukuRunning && try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun updateGameInfo() {
        try {
            val info = packageManager.getPackageInfo(gamePackage, 0)
            gameInstalled = true
            gameVersion = info.versionName
        } catch (_: Throwable) {
            gameInstalled = false
            gameVersion = null
        }
    }

    private fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun requireShizuku(): Boolean {
        updateShizukuState()
        if (!shizukuRunning) {
            setStatus(tr(R.string.req_not_running), Kind.ERROR)
            return false
        }
        if (!shizukuGranted) {
            setStatus(tr(R.string.req_no_permission), Kind.WARN)
            return false
        }
        return true
    }

    private fun requestPermission() {
        updateShizukuState()
        when {
            !shizukuRunning ->
                setStatus(tr(R.string.perm_start_first), Kind.ERROR)
            Shizuku.isPreV11() ->
                setStatus(tr(R.string.perm_too_old), Kind.ERROR)
            shizukuGranted ->
                setStatus(tr(R.string.perm_already), Kind.OK)
            Shizuku.shouldShowRequestPermissionRationale() ->
                setStatus(tr(R.string.perm_permanent), Kind.WARN)
            else -> {
                Shizuku.requestPermission(requestCode)
                setStatus(tr(R.string.perm_confirm), Kind.INFO)
            }
        }
        render()
    }

    private fun runShell(command: String, input: String? = null): String {
        check(hasShizukuPermission()) { tr(R.string.err_no_shizuku) }

        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)

        if (input != null) {
            process.outputStream.use {
                it.write(input.toByteArray(StandardCharsets.UTF_8))
            }
        } else {
            process.outputStream.close()
        }

        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        val error = process.errorStream.readBytes().toString(StandardCharsets.UTF_8)
        val code = process.waitFor()

        if (code != 0) {
            throw IllegalStateException(
                error.ifBlank { output.ifBlank { tr(R.string.err_shell, code) } }
            )
        }
        return output.trim()
    }

    private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun fileExists(path: String): Boolean =
        runShell("test -f ${q(path)} && echo 1 || echo 0") == "1"

    private fun isGameRunning(): Boolean {
        return runCatching { runShell("pidof $gamePackage").isNotBlank() }.getOrDefault(false)
    }

    // ---------- Refresh ----------

    private fun autoStatus() {
        when {
            !shizukuRunning ->
                setStatus(tr(R.string.auto_no_shizuku), Kind.WARN, auto = true)
            !shizukuGranted ->
                setStatus(tr(R.string.auto_no_permission), Kind.WARN, auto = true)
            !gameInstalled ->
                setStatus(tr(R.string.auto_no_game), Kind.WARN, auto = true)
            configProblem != null ->
                setStatus(configProblem.orEmpty(), Kind.WARN, auto = true)
            gameRunning == true ->
                setStatus(tr(R.string.auto_game_running), Kind.WARN, auto = true)
            config != null -> {
                val active = activeProfile()
                val name = if (active != null) tr(active.nameRes) else tr(R.string.hero_custom)
                setStatus(tr(R.string.auto_ready, name), Kind.OK, auto = true)
            }
            else ->
                setStatus(tr(R.string.hero_loading), Kind.INFO, auto = true)
        }
    }

    private fun refreshAll(resetStatus: Boolean = false) {
        updateShizukuState()
        updateGameInfo()

        if (resetStatus && !pendingRevert) statusIsAuto = true

        if (!shizukuGranted) {
            config = null
            configProblem = null
            hasOriginal = false
            hasLast = false
            gameRunning = null
            physSize = null
            curSize = null
            hasOverride = false
            if (statusIsAuto) autoStatus()
            render()
            return
        }

        if (refreshing) {
            refreshQueued = true
            return
        }
        refreshing = true

        Thread {
            val cfg = runCatching { runShell("cat ${q(configPath)}") }
            val original = runCatching { fileExists(backupPath) }.getOrDefault(false)
            val last = runCatching { fileExists(lastBackupPath) }.getOrDefault(false)
            val running = isGameRunning()
            val wm = runCatching { runShell("wm size") }.getOrNull().orEmpty()

            runOnUiThread {
                refreshing = false
                config = cfg.getOrNull()?.let { parseIni(it) }
                configProblem = cfg.exceptionOrNull()?.let { friendly(it) }
                hasOriginal = original
                hasLast = last
                gameRunning = running

                val phys = parseSize(wm, "Physical size")
                val over = parseSize(wm, "Override size")
                physSize = phys
                hasOverride = over != null
                curSize = over ?: phys

                if (statusIsAuto) autoStatus()
                render()

                if (refreshQueued) {
                    refreshQueued = false
                    refreshAll()
                }
            }
        }.start()
    }

    private fun runTask(
        startMessage: String,
        after: (() -> Unit)? = null,
        work: () -> String
    ) {
        if (busy) {
            setStatus(tr(R.string.busy_wait), Kind.WARN)
            return
        }
        busy = true
        setStatus(startMessage, Kind.INFO)

        Thread {
            val result = runCatching(work)
            runOnUiThread {
                busy = false
                result
                    .onSuccess {
                        setStatus(it, Kind.OK)
                        after?.invoke()
                    }
                    .onFailure {
                        setStatus(friendly(it), Kind.ERROR)
                    }
                refreshAll()
            }
        }.start()
    }

    // ---------- Actions ----------

    private fun applyProfile(p: Profile) {
        val launchAfter = prefs.getBoolean(PREF_LAUNCH, false)
        applyValues(tr(p.nameRes), p.values, launchAfter)
    }

    private fun applyValues(
        title: String,
        values: Map<String, String>,
        launchAfter: Boolean = false
    ) {
        if (!requireShizuku()) {
            render()
            return
        }
        runTask(
            tr(R.string.applying, title),
            after = { if (launchAfter) launchGame() }
        ) {
            "$title: " + writeConfig(values)
        }
    }

    private fun writeConfig(values: Map<String, String>): String {
        if (isGameRunning()) {
            throw IllegalStateException(tr(R.string.err_game_running))
        }

        val original = try {
            runShell("cat ${q(configPath)}")
        } catch (e: Exception) {
            throw IllegalStateException(friendly(e))
        }

        // Backups: original once, last state on every change
        runShell("if [ ! -f ${q(backupPath)} ]; then cp ${q(configPath)} ${q(backupPath)}; fi")
        runShell("cp ${q(configPath)} ${q(lastBackupPath)}")
        check(fileExists(backupPath) && fileExists(lastBackupPath)) {
            tr(R.string.err_backup_failed)
        }

        var updated = original
        val applied = ArrayList<String>()
        val missing = ArrayList<String>()

        for ((key, value) in values) {
            val regex = Regex(
                "(?m)^(" + Regex.escape(key) + "[ \\t]*=[ \\t]*)[^;\\r\\n]*?([ \\t]*)(;.*)?$"
            )
            if (regex.containsMatchIn(updated)) {
                updated = regex.replace(updated) { m ->
                    m.groupValues[1] + value + m.groupValues[2] + m.groupValues[3]
                }
                applied.add(key)
            } else {
                missing.add(key)
            }
        }

        if (applied.isEmpty()) {
            throw IllegalStateException(tr(R.string.err_no_keys))
        }

        if (!updated.endsWith("\n")) updated += "\n"

        try {
            runShell("cat > ${q(configPath)}", updated)

            val saved = parseIni(runShell("cat ${q(configPath)}"))
            for (key in applied) {
                val expected = values[key] ?: continue
                if (!expected.equals(saved[key], ignoreCase = true)) {
                    throw IllegalStateException(tr(R.string.err_not_saved, key))
                }
            }
        } catch (e: Exception) {
            runCatching { runShell("cp ${q(lastBackupPath)} ${q(configPath)}") }
            throw IllegalStateException(tr(R.string.err_save_failed, friendly(e)))
        }

        return if (missing.isEmpty()) {
            tr(R.string.saved_verified)
        } else {
            tr(R.string.saved_missing, missing.joinToString(", "))
        }
    }

    private fun restore(path: String, title: String) {
        if (!requireShizuku()) {
            render()
            return
        }
        runTask(tr(R.string.restoring, title)) {
            if (isGameRunning()) {
                throw IllegalStateException(tr(R.string.err_game_running))
            }
            if (!fileExists(path)) {
                throw IllegalStateException(tr(R.string.err_no_backup))
            }
            runShell("cp ${q(path)} ${q(configPath)}")
            tr(R.string.restored, title)
        }
    }

    private fun launchGame() {
        updateGameInfo()
        if (!gameInstalled) {
            setStatus(tr(R.string.launch_not_installed), Kind.ERROR)
            render()
            return
        }
        try {
            val intent = packageManager.getLaunchIntentForPackage(gamePackage)
                ?: Intent().setClassName(gamePackage, gameActivity)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            setStatus(tr(R.string.launch_ok), Kind.OK)
        } catch (e: Exception) {
            setStatus(tr(R.string.launch_failed, friendly(e)), Kind.ERROR)
        }
    }

    private fun stopGame() {
        if (!requireShizuku()) {
            render()
            return
        }
        runTask(tr(R.string.stopping)) {
            runShell("am force-stop $gamePackage")
            tr(R.string.stopped)
        }
    }

    // ---------- Stretched resolution ----------

    private fun applyStretch(size: Pair<Int, Int>) {
        if (!requireShizuku()) {
            render()
            return
        }
        val text = "${size.first}x${size.second}"
        runTask(tr(R.string.stretch_applying, text), after = { startCountdown() }) {
            runShell("wm size $text")
            // Safety net: resets the resolution by itself even if the app is killed
            runShell(
                "(sleep 25; wm size reset; rm -f $REVERT_FILE) >/dev/null 2>&1 </dev/null & " +
                    "echo \$! > $REVERT_FILE"
            )
            tr(R.string.stretch_applied, text)
        }
    }

    private fun startCountdown() {
        handler.removeCallbacks(tick)
        pendingRevert = true
        countdown = 15
        handler.postDelayed(tick, 1000)
        render()
    }

    private fun keepStretch() {
        handler.removeCallbacks(tick)
        pendingRevert = false
        if (!requireShizuku()) {
            render()
            return
        }
        runTask(tr(R.string.stretch_keeping)) {
            runShell("kill \$(cat $REVERT_FILE 2>/dev/null) 2>/dev/null; rm -f $REVERT_FILE; echo ok")
            tr(R.string.stretch_kept)
        }
    }

    private fun resetStretch() {
        handler.removeCallbacks(tick)
        pendingRevert = false
        if (!requireShizuku()) {
            render()
            return
        }
        runTask(tr(R.string.stretch_resetting)) {
            runShell("kill \$(cat $REVERT_FILE 2>/dev/null) 2>/dev/null; rm -f $REVERT_FILE; wm size reset")
            tr(R.string.stretch_reset_done)
        }
    }

    // ---------- Diagnostics ----------

    private fun collectDiagnostics(share: Boolean) {
        if (!requireShizuku()) {
            render()
            return
        }
        runTask(
            tr(R.string.diag_collecting),
            after = { if (share) shareText(pendingText) else copyText(pendingText) }
        ) {
            val sb = StringBuilder()
            sb.append("SG0HANN BOOST $appVersion\n")
            sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n")
            sb.append("Game: $gamePackage ${gameVersion ?: "-"}\n\n")
            sb.append("== wm size ==\n")
            sb.append(runCatching { runShell("wm size") }.getOrDefault("?")).append("\n\n")
            sb.append("== files ==\n")
            sb.append(
                runCatching { runShell("find ${q(configDir)} -maxdepth 2 | head -80") }
                    .getOrDefault("?")
            ).append("\n\n")
            sb.append("== options.ini ==\n")
            sb.append(runCatching { runShell("cat ${q(configPath)}") }.getOrDefault("?"))

            for (name in listOf("neox.xml", "user_data.xml", "PlatformConfig.xml", "patch_config.json")) {
                sb.append("\n\n== $name ==\n")
                val file = q("$configDir/$name")
                sb.append(runCatching { runShell("head -c 8000 $file") }.getOrDefault("?"))
            }

            val cdr = q("$configDir/Documents/cdrevive")
            val usr = q("$configDir/Documents/UserData")
            sb.append("\n\n== Documents/cdrevive + UserData ==\n")
            sb.append(
                runCatching { runShell("find $cdr $usr -maxdepth 3 | head -150") }.getOrDefault("?")
            )
            sb.append("\n\n== small text files in cdrevive ==\n")
            sb.append(
                runCatching {
                    runShell(
                        "find $cdr -maxdepth 2 -type f -size -20k " +
                            "\\( -name '*.ini' -o -name '*.json' -o -name '*.xml' -o -name '*.cfg' -o -name '*.txt' \\) " +
                            "| head -8 | while read f; do echo \"-- \$f\"; head -c 4000 \"\$f\"; echo; done"
                    )
                }.getOrDefault("?")
            )
            pendingText = sb.toString()
            if (share) tr(R.string.diag_ready_share) else tr(R.string.diag_ready_copy)
        }
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SG0HANN BOOST", text))
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, null))
    }

    private companion object {
        const val PREF_LAUNCH = "launch_after_apply"
        const val REVERT_FILE = "/data/local/tmp/sgh0hann_revert.pid"
        const val MAX_ROWS = 60
    }
}
