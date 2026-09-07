package com.atomichabits.tracker.ui.theme

import androidx.compose.ui.graphics.Color

// MyGenetics brand tokens (see the mygenetics-brand-pdf skill's token table).
// Mapped onto Material3 roles below rather than used as bare constants, so
// every screen already reading MaterialTheme.colorScheme.* picks these up
// automatically with no per-screen changes.
//
// --mg-accent (#32CC00) itself is NOT used as Material's `primary`: at full
// brightness it's too light for white text to sit on with proper contrast
// (a filled Button's default onPrimary=white would read ~2.1:1, well under
// the ~4.5:1 accessibility target) - the brand's own template never puts
// white text on the green either, it uses the green for accents/headings on
// WHITE, and dark ink for anything that needs to be read. --mg-accent-hover
// (#28A300) is used for `primary` instead (a filled surface still needs
// SOME base color), paired with the brand's own dark ink as `onPrimary`
// rather than white - true to how the brand actually uses green in print.

// Light scheme - MyGenetics brand tokens
val PrimaryLight = Color(0xFF28A300)            // --mg-accent-hover
val OnPrimaryLight = Color(0xFF1A2B14)          // --mg-text (dark ink, not white - see note above)
val PrimaryContainerLight = Color(0xFFE8F8E0)   // --mg-accent-soft
val OnPrimaryContainerLight = Color(0xFF1A2B14) // --mg-text
val SecondaryLight = Color(0xFF5A6B54)          // --mg-text-dim
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE4EDE0) // --mg-border
val OnSecondaryContainerLight = Color(0xFF1A2B14) // --mg-text
val BackgroundLight = Color(0xFFFFFFFF)         // --mg-bg
val SurfaceLight = Color(0xFFFFFFFF)            // --mg-bg
val SurfaceVariantLight = Color(0xFFF7FBF4)     // --mg-bg-soft
val OnSurfaceLight = Color(0xFF1A2B14)          // --mg-text
val OnSurfaceVariantLight = Color(0xFF5A6B54)   // --mg-text-dim
// Error stays Material's standard red, deliberately untouched - the brand
// has no error token, and this app's HARMFUL-habit sections depend on error/
// errorContainer actually reading as a warning red, not the brand green.
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)

// Dark scheme - the brand kit is print/PDF-only and defines no dark palette,
// so this keeps the same brand green HUE (not a copy of the old emerald
// scheme) lightened and desaturated the way Material3's own light-on-dark
// primaries usually are, with dark ink flipped to a light tint for onPrimary/
// onPrimaryContainer instead of white, for the same contrast reason as light.
val PrimaryDark = Color(0xFF8CE05C)
val OnPrimaryDark = Color(0xFF123404)
val PrimaryContainerDark = Color(0xFF1F4A12)
val OnPrimaryContainerDark = Color(0xFFC5F2A8)
val SecondaryDark = Color(0xFFB4C4A9)
val OnSecondaryDark = Color(0xFF283420)
val SecondaryContainerDark = Color(0xFF3D4A35)
val OnSecondaryContainerDark = Color(0xFFD7E5C9)
val BackgroundDark = Color(0xFF10150D)
val SurfaceDark = Color(0xFF171C13)
val SurfaceVariantDark = Color(0xFF2B3324)
val OnSurfaceDark = Color(0xFFE3E8DD)
val OnSurfaceVariantDark = Color(0xFFC2CAB8)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)

// Habit accent presets offered in the color picker - deliberately NOT
// restyled to the brand palette. These are the user's own per-habit
// personalization choices (like the 7 life-sphere colors in Categories.kt,
// which were explicitly asked to stay as they are), not app chrome.
val HabitColorPresets = listOf(
    0xFF7C6CF0, // violet
    0xFF3DBE8B, // green
    0xFFF2A93B, // amber
    0xFFEF6461, // coral
    0xFF3AA6D9, // blue
    0xFFE0609D, // pink
)

// Heatmap intensity (empty -> completed) - this IS chrome (a progress
// visualization, not a user color choice), so it follows the new brand green.
val HeatmapEmpty = Color(0xFFE4EDE0)   // --mg-border
val HeatmapFilled = Color(0xFF28A300) // --mg-accent-hover, matches PrimaryLight
