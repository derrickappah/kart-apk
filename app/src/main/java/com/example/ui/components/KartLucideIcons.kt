package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Unified bold Lucide icon registry for Kart.
 * All icons follow the same rounded-cap, rounded-join bold stroke style as the bottom nav bar.
 */
object KartLucideIcons {

    private const val NAV_INACTIVE_STROKE = 2.25f
    private const val NAV_ACTIVE_STROKE = 2.65f
    private const val BOLD_STROKE = 2.5f

    // Bottom Navigation Tabs
    val HomeInactive: ImageVector by lazy { buildHome(NAV_INACTIVE_STROKE) }
    val HomeActive: ImageVector by lazy { buildHome(NAV_ACTIVE_STROKE) }

    val StoreInactive: ImageVector by lazy { buildStore(NAV_INACTIVE_STROKE) }
    val StoreActive: ImageVector by lazy { buildStore(NAV_ACTIVE_STROKE) }

    val PlusCircleInactive: ImageVector by lazy { buildPlusCircle(NAV_INACTIVE_STROKE) }
    val PlusCircleActive: ImageVector by lazy { buildPlusCircle(NAV_ACTIVE_STROKE) }

    val MessageCircleInactive: ImageVector by lazy { buildMessageCircle(NAV_INACTIVE_STROKE) }
    val MessageCircleActive: ImageVector by lazy { buildMessageCircle(NAV_ACTIVE_STROKE) }

    val UserCircleInactive: ImageVector by lazy { buildUserCircle(NAV_INACTIVE_STROKE) }
    val UserCircleActive: ImageVector by lazy { buildUserCircle(NAV_ACTIVE_STROKE) }

    // Core Navigation & Actions
    val Bell: ImageVector by lazy { buildBell(BOLD_STROKE) }
    val BellRing: ImageVector by lazy { buildBellRing(BOLD_STROKE) }
    val HeartOutline: ImageVector by lazy { buildHeart(filled = false, strokeWidth = BOLD_STROKE) }
    val HeartFilled: ImageVector by lazy { buildHeart(filled = true, strokeWidth = BOLD_STROKE) }
    val ArrowLeft: ImageVector by lazy { buildArrowLeft(BOLD_STROKE) }
    val ArrowRight: ImageVector by lazy { buildArrowRight(BOLD_STROKE) }
    val ArrowUp: ImageVector by lazy { buildArrowUp(BOLD_STROKE) }
    val ArrowDown: ImageVector by lazy { buildArrowDown(BOLD_STROKE) }
    val ChevronRight: ImageVector by lazy { buildChevronRight(BOLD_STROKE) }
    val Search: ImageVector by lazy { buildSearch(BOLD_STROKE) }
    val X: ImageVector by lazy { buildX(BOLD_STROKE) }
    val Plus: ImageVector by lazy { buildPlus(BOLD_STROKE) }
    val Minus: ImageVector by lazy { buildMinus(BOLD_STROKE) }
    val SlidersHorizontal: ImageVector by lazy { buildSlidersHorizontal(BOLD_STROKE) }
    val Share2: ImageVector by lazy { buildShare2(BOLD_STROKE) }
    val Flag: ImageVector by lazy { buildFlag(BOLD_STROKE) }
    val Send: ImageVector by lazy { buildSend(BOLD_STROKE) }
    val LogOut: ImageVector by lazy { buildLogOut(BOLD_STROKE) }

    // Trust, Ratings & Status
    val StarOutline: ImageVector by lazy { buildStar(filled = false, strokeWidth = BOLD_STROKE) }
    val StarFilled: ImageVector by lazy { buildStar(filled = true, strokeWidth = BOLD_STROKE) }
    val Shield: ImageVector by lazy { buildShield(BOLD_STROKE) }
    val ShieldCheck: ImageVector by lazy { buildShieldCheck(BOLD_STROKE) }
    val BadgeCheck: ImageVector by lazy { buildBadgeCheck(BOLD_STROKE) }
    val CheckCircle: ImageVector by lazy { buildCheckCircle(BOLD_STROKE) }
    val Flame: ImageVector by lazy { buildFlame(BOLD_STROKE) }
    val MapPin: ImageVector by lazy { buildMapPin(BOLD_STROKE) }
    val Crown: ImageVector by lazy { buildCrown(BOLD_STROKE) }

    // Commerce, Wallet & Seller
    val CreditCard: ImageVector by lazy { buildCreditCard(BOLD_STROKE) }
    val Wallet: ImageVector by lazy { buildWallet(BOLD_STROKE) }
    val Landmark: ImageVector by lazy { buildLandmark(BOLD_STROKE) }
    val ReceiptText: ImageVector by lazy { buildReceiptText(BOLD_STROKE) }
    val Package: ImageVector by lazy { buildPackage(BOLD_STROKE) }
    val Truck: ImageVector by lazy { buildTruck(BOLD_STROKE) }
    val Megaphone: ImageVector by lazy { buildMegaphone(BOLD_STROKE) }
    val BarChart2: ImageVector by lazy { buildBarChart2(BOLD_STROKE) }
    val Gift: ImageVector by lazy { buildGift(BOLD_STROKE) }

    // Profile, Auth & Media
    val User: ImageVector by lazy { buildUser(BOLD_STROKE) }
    val Mail: ImageVector by lazy { buildMail(BOLD_STROKE) }
    val Phone: ImageVector by lazy { buildPhone(BOLD_STROKE) }
    val Lock: ImageVector by lazy { buildLock(BOLD_STROKE) }
    val KeyRound: ImageVector by lazy { buildKeyRound(BOLD_STROKE) }
    val Eye: ImageVector by lazy { buildEye(BOLD_STROKE) }
    val EyeOff: ImageVector by lazy { buildEyeOff(BOLD_STROKE) }
    val IdCard: ImageVector by lazy { buildIdCard(BOLD_STROKE) }
    val Smile: ImageVector by lazy { buildSmile(BOLD_STROKE) }
    val Camera: ImageVector by lazy { buildCamera(BOLD_STROKE) }
    val CameraOff: ImageVector by lazy { buildCameraOff(BOLD_STROKE) }
    val RefreshCw: ImageVector by lazy { buildRefreshCw(BOLD_STROKE) }
    val Image: ImageVector by lazy { buildImage(BOLD_STROKE) }
    val ImagePlus: ImageVector by lazy { buildImagePlus(BOLD_STROKE) }
    val Pencil: ImageVector by lazy { buildPencil(BOLD_STROKE) }
    val Trash2: ImageVector by lazy { buildTrash2(BOLD_STROKE) }

    // Settings & Support
    val Settings: ImageVector by lazy { buildSettings(BOLD_STROKE) }
    val Palette: ImageVector by lazy { buildPalette(BOLD_STROKE) }
    val Globe: ImageVector by lazy { buildGlobe(BOLD_STROKE) }
    val Headphones: ImageVector by lazy { buildHeadphones(BOLD_STROKE) }
    val HelpCircle: ImageVector by lazy { buildHelpCircle(BOLD_STROKE) }

    // Item Conditions & Selection
    val Sparkles: ImageVector by lazy { buildSparkles(BOLD_STROKE) }
    val ThumbsUp: ImageVector by lazy { buildThumbsUp(BOLD_STROKE) }
    val Handshake: ImageVector by lazy { buildHandshake(BOLD_STROKE) }
    val SquareCheckBig: ImageVector by lazy { buildSquareCheckBig(BOLD_STROKE) }
    val Wrench: ImageVector by lazy { buildWrench(BOLD_STROKE) }
    val Check: ImageVector by lazy { buildCheck(BOLD_STROKE) }

    fun conditionIcon(condition: String?): ImageVector = when (condition?.trim()?.lowercase()) {
        "new", "brand new" -> Sparkles
        "like new" -> ThumbsUp
        "good" -> Handshake
        "fair" -> SquareCheckBig
        "acceptable", "used" -> Wrench
        else -> Sparkles
    }

    val allIcons: List<ImageVector>
        get() = listOf(
            HomeInactive, HomeActive, StoreInactive, StoreActive,
            PlusCircleInactive, PlusCircleActive, MessageCircleInactive, MessageCircleActive,
            UserCircleInactive, UserCircleActive,
            Bell, BellRing, HeartOutline, HeartFilled, ArrowLeft, ArrowRight, ArrowUp, ArrowDown,
            ChevronRight, Search, X, Plus, Minus, SlidersHorizontal, Share2, Flag, Send, LogOut,
            StarOutline, StarFilled, Shield, ShieldCheck, BadgeCheck, CheckCircle, Flame, MapPin, Crown,
            CreditCard, Wallet, Landmark, ReceiptText, Package, Truck, Megaphone, BarChart2, Gift,
            User, Mail, Phone, Lock, KeyRound, Eye, EyeOff, IdCard, Smile, Camera, CameraOff, RefreshCw, Image, ImagePlus,
            Pencil, Trash2, Settings, Palette, Globe, Headphones, HelpCircle,
            Sparkles, ThumbsUp, Handshake, SquareCheckBig, Wrench, Check
        )

    private fun lucideIcon(
        name: String,
        strokeWidth: Float,
        vararg paths: String,
        filled: Boolean = false
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { d ->
            val nodes = PathParser().parsePathString(d).toNodes()
            builder.addPath(
                pathData = nodes,
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    private fun buildHome(s: Float) = lucideIcon(
        "LucideHome_$s", s,
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
    )

    private fun buildStore(s: Float) = lucideIcon(
        "LucideStore_$s", s,
        "m2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7",
        "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8",
        "M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4",
        "M2 7h20",
        "M22 7v3a2 2 0 0 1-2 2a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 16 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 12 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 8 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 4 12a2 2 0 0 1-2-2V7"
    )

    private fun buildPlusCircle(s: Float) = lucideIcon(
        "LucidePlusCircle_$s", s,
        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
        "M8 12h8",
        "M12 8v8"
    )

    private fun buildMessageCircle(s: Float) = lucideIcon(
        "LucideMessageCircle_$s", s,
        "M7.9 20A9 9 0 1 0 4 16.1L2 22Z"
    )

    private fun buildUserCircle(s: Float) = lucideIcon(
        "LucideUserCircle_$s", s,
        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
        "M12 7a3 3 0 1 0 0 6 3 3 0 1 0 0-6",
        "M7 20.662V19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v1.662"
    )

    private fun buildBell(s: Float) = lucideIcon(
        "LucideBell_$s", s,
        "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0"
    )

    private fun buildBellRing(s: Float) = lucideIcon(
        "LucideBellRing_$s", s,
        "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0",
        "M4 2C2.8 3.7 2 5.7 2 8",
        "M22 8c0-2.3-.8-4.3-2-6"
    )

    private fun buildHeart(filled: Boolean, strokeWidth: Float) = lucideIcon(
        if (filled) "LucideHeartFilled" else "LucideHeartOutline", strokeWidth,
        "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z",
        filled = filled
    )

    private fun buildArrowLeft(s: Float) = lucideIcon(
        "LucideArrowLeft_$s", s,
        "m12 19-7-7 7-7",
        "M19 12H5"
    )

    private fun buildArrowRight(s: Float) = lucideIcon(
        "LucideArrowRight_$s", s,
        "M5 12h14",
        "m12 5 7 7-7 7"
    )

    private fun buildArrowUp(s: Float) = lucideIcon(
        "LucideArrowUp_$s", s,
        "m5 12 7-7 7 7",
        "M12 19V5"
    )

    private fun buildArrowDown(s: Float) = lucideIcon(
        "LucideArrowDown_$s", s,
        "M12 5v14",
        "m19 12-7 7-7-7"
    )

    private fun buildChevronRight(s: Float) = lucideIcon(
        "LucideChevronRight_$s", s,
        "m9 18 6-6-6-6"
    )

    private fun buildSearch(s: Float) = lucideIcon(
        "LucideSearch_$s", s,
        "M11 3a8 8 0 1 0 0 16 8 8 0 1 0 0-16",
        "m21 21-4.3-4.3"
    )

    private fun buildX(s: Float) = lucideIcon(
        "LucideX_$s", s,
        "M18 6 6 18",
        "m6 6 12 12"
    )

    private fun buildPlus(s: Float) = lucideIcon(
        "LucidePlus_$s", s,
        "M5 12h14",
        "M12 5v14"
    )

    private fun buildMinus(s: Float) = lucideIcon(
        "LucideMinus_$s", s,
        "M5 12h14"
    )

    private fun buildSlidersHorizontal(s: Float) = lucideIcon(
        "LucideSlidersHorizontal_$s", s,
        "M21 4h-7",
        "M10 4H3",
        "M21 12h-9",
        "M8 12H3",
        "M21 20h-5",
        "M12 20H3",
        "M14 2v4",
        "M8 10v4",
        "M16 18v4"
    )

    private fun buildShare2(s: Float) = lucideIcon(
        "LucideShare2_$s", s,
        "M18 2a3 3 0 1 0 0 6 3 3 0 1 0 0-6",
        "M6 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6",
        "M18 16a3 3 0 1 0 0 6 3 3 0 1 0 0-6",
        "M8.59 13.51l6.83 3.98",
        "M15.41 6.51l-6.82 3.98"
    )

    private fun buildFlag(s: Float) = lucideIcon(
        "LucideFlag_$s", s,
        "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z",
        "M4 22V15"
    )

    private fun buildSend(s: Float) = lucideIcon(
        "LucideSend_$s", s,
        "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
        "m21.854 2.147-10.94 10.939"
    )

    private fun buildLogOut(s: Float) = lucideIcon(
        "LucideLogOut_$s", s,
        "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
        "m16 17 5-5-5-5",
        "M21 12H9"
    )

    private fun buildStar(filled: Boolean, strokeWidth: Float) = lucideIcon(
        if (filled) "LucideStarFilled" else "LucideStarOutline", strokeWidth,
        "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
        filled = filled
    )

    private fun buildShield(s: Float) = lucideIcon(
        "LucideShield_$s", s,
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"
    )

    private fun buildShieldCheck(s: Float) = lucideIcon(
        "LucideShieldCheck_$s", s,
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4"
    )

    private fun buildBadgeCheck(s: Float) = lucideIcon(
        "LucideBadgeCheck_$s", s,
        "M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z",
        "m9 12 2 2 4-4"
    )

    private fun buildCheckCircle(s: Float) = lucideIcon(
        "LucideCheckCircle_$s", s,
        "M21.801 10A10 10 0 1 1 17 3.335",
        "m9 11 3 3L22 4"
    )

    private fun buildFlame(s: Float) = lucideIcon(
        "LucideFlame_$s", s,
        "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"
    )

    private fun buildMapPin(s: Float) = lucideIcon(
        "LucideMapPin_$s", s,
        "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
        "M12 7a3 3 0 1 0 0 6 3 3 0 1 0 0-6"
    )

    private fun buildCrown(s: Float) = lucideIcon(
        "LucideCrown_$s", s,
        "M11.562 3.266a.5.5 0 0 1 .876 0L15.39 8.87a1 1 0 0 0 1.516.294L21.183 5.5a.5.5 0 0 1 .798.519l-2.834 10.246a1 1 0 0 1-.956.734H5.81a1 1 0 0 1-.957-.734L2.02 6.02a.5.5 0 0 1 .798-.519l4.276 3.664a1 1 0 0 0 1.516-.294z",
        "M5 21h14"
    )

    private fun buildCreditCard(s: Float) = lucideIcon(
        "LucideCreditCard_$s", s,
        "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M2 10h20"
    )

    private fun buildWallet(s: Float) = lucideIcon(
        "LucideWallet_$s", s,
        "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
        "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4"
    )

    private fun buildLandmark(s: Float) = lucideIcon(
        "LucideLandmark_$s", s,
        "M3 22h18",
        "M6 18v-7",
        "M10 18v-7",
        "M14 18v-7",
        "M18 18v-7",
        "m12 2 8 5H4z"
    )

    private fun buildReceiptText(s: Float) = lucideIcon(
        "LucideReceiptText_$s", s,
        "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z",
        "M14 8H8",
        "M16 12H8",
        "M13 16H8"
    )

    private fun buildPackage(s: Float) = lucideIcon(
        "LucidePackage_$s", s,
        "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
        "M12 22V12",
        "m3.3 7 7.703 4.734a2 2 0 0 0 1.994 0L20.7 7",
        "m7.5 4.27 9 5.15"
    )

    private fun buildTruck(s: Float) = lucideIcon(
        "LucideTruck_$s", s,
        "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2",
        "M15 18H9",
        "M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14",
        "M7 16a2 2 0 1 0 0 4 2 2 0 1 0 0-4",
        "M17 16a2 2 0 1 0 0 4 2 2 0 1 0 0-4"
    )

    private fun buildMegaphone(s: Float) = lucideIcon(
        "LucideMegaphone_$s", s,
        "m3 11 18-5v12L3 14v-3z",
        "M11.6 16.8a3 3 0 1 1-5.8-1.6"
    )

    private fun buildBarChart2(s: Float) = lucideIcon(
        "LucideBarChart2_$s", s,
        "M18 20V10",
        "M12 20V4",
        "M6 20v-6"
    )

    private fun buildGift(s: Float) = lucideIcon(
        "LucideGift_$s", s,
        "M4 8h16a1 1 0 0 1 1 1v3H3V9a1 1 0 0 1 1-1z",
        "M12 8v13",
        "M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7",
        "M7.5 8a2.5 2.5 0 0 1 0-5A4.8 8 0 0 1 12 8a4.8 8 0 0 1 4.5-5 2.5 2.5 0 0 1 0 5"
    )

    private fun buildUser(s: Float) = lucideIcon(
        "LucideUser_$s", s,
        "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
        "M12 3a4 4 0 1 0 0 8 4 4 0 1 0 0-8"
    )

    private fun buildMail(s: Float) = lucideIcon(
        "LucideMail_$s", s,
        "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"
    )

    private fun buildPhone(s: Float) = lucideIcon(
        "LucidePhone_$s", s,
        "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"
    )

    private fun buildLock(s: Float) = lucideIcon(
        "LucideLock_$s", s,
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M7 11V7a5 5 0 0 1 10 0v4"
    )

    private fun buildKeyRound(s: Float) = lucideIcon(
        "LucideKeyRound_$s", s,
        "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z",
        "M16.5 7a.5.5 0 1 0 0 1 .5.5 0 1 0 0-1"
    )

    private fun buildEye(s: Float) = lucideIcon(
        "LucideEye_$s", s,
        "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
        "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6"
    )

    private fun buildEyeOff(s: Float) = lucideIcon(
        "LucideEyeOff_$s", s,
        "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
        "M14.084 14.158a3 3 0 0 1-4.242-4.242",
        "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
        "m2 2 20 20"
    )

    private fun buildIdCard(s: Float) = lucideIcon(
        "LucideIdCard_$s", s,
        "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M16 10h2",
        "M16 14h2",
        "M6.17 15a3 3 0 0 1 5.66 0",
        "M9 9a2 2 0 1 0 0 4 2 2 0 1 0 0-4"
    )

    private fun buildSmile(s: Float) = lucideIcon(
        "LucideSmile_$s", s,
        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
        "M8 14s1.5 2 4 2 4-2 4-2",
        "M9 9h.01",
        "M15 9h.01"
    )

    private fun buildCamera(s: Float) = lucideIcon(
        "LucideCamera_$s", s,
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        "M12 10a3 3 0 1 0 0 6 3 3 0 1 0 0-6"
    )

    private fun buildImage(s: Float) = lucideIcon(
        "LucideImage_$s", s,
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M9 7a2 2 0 1 0 0 4 2 2 0 1 0 0-4",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
    )

    private fun buildImagePlus(s: Float) = lucideIcon(
        "LucideImagePlus_$s", s,
        "M16 5h6",
        "M19 2v6",
        "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        "M9 7a2 2 0 1 0 0 4 2 2 0 1 0 0-4"
    )

    private fun buildPencil(s: Float) = lucideIcon(
        "LucidePencil_$s", s,
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        "m15 5 4 4"
    )

    private fun buildTrash2(s: Float) = lucideIcon(
        "LucideTrash2_$s", s,
        "M3 6h18",
        "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
        "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
        "M10 11v6",
        "M14 11v6"
    )

    private fun buildSettings(s: Float) = lucideIcon(
        "LucideSettings_$s", s,
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6"
    )

    private fun buildPalette(s: Float) = lucideIcon(
        "LucidePalette_$s", s,
        "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
        "M13.5 6a.5.5 0 1 0 0 1 .5.5 0 1 0 0-1",
        "M17.5 10a.5.5 0 1 0 0 1 .5.5 0 1 0 0-1",
        "M6.5 12a.5.5 0 1 0 0 1 .5.5 0 1 0 0-1",
        "M8.5 7a.5.5 0 1 0 0 1 .5.5 0 1 0 0-1"
    )

    private fun buildGlobe(s: Float) = lucideIcon(
        "LucideGlobe_$s", s,
        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20"
    )

    private fun buildHeadphones(s: Float) = lucideIcon(
        "LucideHeadphones_$s", s,
        "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3"
    )

    private fun buildHelpCircle(s: Float) = lucideIcon(
        "LucideHelpCircle_$s", s,
        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
        "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3",
        "M12 17h.01"
    )

    private fun buildSparkles(s: Float) = lucideIcon(
        "LucideSparkles_$s", s,
        "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
        "M20 2v4",
        "M22 4h-4",
        "M4 18a2 2 0 1 0 0 4 2 2 0 1 0 0-4"
    )

    private fun buildThumbsUp(s: Float) = lucideIcon(
        "LucideThumbsUp_$s", s,
        "M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z",
        "M7 10v12"
    )

    private fun buildHandshake(s: Float) = lucideIcon(
        "LucideHandshake_$s", s,
        "m11 17 2 2a1 1 0 1 0 3-3",
        "m14 14 2.5 2.5a1 1 0 1 0 3-3l-3.88-3.88a3 3 0 0 0-4.24 0l-.88.88a1 1 0 1 1-3-3l2.81-2.81a5.79 5.79 0 0 1 7.06-.87l.47.28a2 2 0 0 0 1.42.25L21 4",
        "m21 3 1 11h-2",
        "M3 3 2 14l6.5 6.5a1 1 0 1 0 3-3",
        "M3 4h8"
    )

    private fun buildSquareCheckBig(s: Float) = lucideIcon(
        "LucideSquareCheckBig_$s", s,
        "M21 10.656V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12.344",
        "m9 11 3 3L22 4"
    )

    private fun buildWrench(s: Float) = lucideIcon(
        "LucideWrench_$s", s,
        "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z"
    )

    private fun buildCheck(s: Float) = lucideIcon(
        "LucideCheck_$s", s,
        "M20 6 9 17l-5-5"
    )

    private fun buildRefreshCw(s: Float) = lucideIcon(
        "LucideRefreshCw_$s", s,
        "M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16",
        "M16 21h5v-5"
    )

    private fun buildCameraOff(s: Float) = lucideIcon(
        "LucideCameraOff_$s", s,
        "m2 2 20 20",
        "M7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16",
        "M9.5 4h5L17 7h3a2 2 0 0 1 2 2v7.5",
        "M14.121 15.121A3 3 0 1 1 9.88 10.88"
    )
}
