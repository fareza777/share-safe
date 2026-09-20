package com.sharesafe.app.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.sharesafe.app.R
import com.sharesafe.app.core.model.KindGroup
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.model.SensitiveKind
import com.sharesafe.app.ui.theme.KindAccount
import com.sharesafe.app.ui.theme.KindCard
import com.sharesafe.app.ui.theme.KindCode
import com.sharesafe.app.ui.theme.KindDob
import com.sharesafe.app.ui.theme.KindEmail
import com.sharesafe.app.ui.theme.KindFace
import com.sharesafe.app.ui.theme.KindAddress
import com.sharesafe.app.ui.theme.KindAvatar
import com.sharesafe.app.ui.theme.KindLocation
import com.sharesafe.app.ui.theme.KindName
import com.sharesafe.app.ui.theme.KindNetwork
import com.sharesafe.app.ui.theme.KindPassport
import com.sharesafe.app.ui.theme.KindPlate
import com.sharesafe.app.ui.theme.KindIdentity
import com.sharesafe.app.ui.theme.KindManual
import com.sharesafe.app.ui.theme.KindNumber
import com.sharesafe.app.ui.theme.KindOtp
import com.sharesafe.app.ui.theme.KindPhone
import com.sharesafe.app.ui.theme.KindSecret

@Composable
fun kindLabel(kind: SensitiveKind): String = stringResource(
    when (kind) {
        SensitiveKind.PHONE -> R.string.category_phone
        SensitiveKind.NAME -> R.string.category_name
        SensitiveKind.AVATAR -> R.string.category_avatar
        SensitiveKind.EMAIL -> R.string.category_email
        SensitiveKind.CARD -> R.string.category_card
        SensitiveKind.ID_NUMBER -> R.string.category_id_number
        SensitiveKind.PASSPORT -> R.string.category_passport
        SensitiveKind.ACCOUNT_NUMBER -> R.string.category_account
        SensitiveKind.OTP -> R.string.category_otp
        SensitiveKind.DOB -> R.string.category_dob
        SensitiveKind.SECRET -> R.string.category_secret
        SensitiveKind.LOCATION -> R.string.category_location
        SensitiveKind.NETWORK -> R.string.category_network
        SensitiveKind.PLATE -> R.string.category_plate
        SensitiveKind.ADDRESS -> R.string.category_address
        SensitiveKind.LONG_NUMBER -> R.string.category_number
        SensitiveKind.QR -> R.string.category_qr
        SensitiveKind.FACE -> R.string.category_face
        SensitiveKind.MANUAL -> R.string.category_manual
    },
)

fun kindColor(kind: SensitiveKind): Color = when (kind) {
    SensitiveKind.PHONE -> KindPhone
    SensitiveKind.NAME -> KindName
    SensitiveKind.AVATAR -> KindAvatar
    SensitiveKind.EMAIL -> KindEmail
    SensitiveKind.CARD -> KindCard
    SensitiveKind.ID_NUMBER -> KindIdentity
    SensitiveKind.PASSPORT -> KindPassport
    SensitiveKind.ACCOUNT_NUMBER -> KindAccount
    SensitiveKind.OTP -> KindOtp
    SensitiveKind.DOB -> KindDob
    SensitiveKind.SECRET -> KindSecret
    SensitiveKind.LOCATION -> KindLocation
    SensitiveKind.NETWORK -> KindNetwork
    SensitiveKind.PLATE -> KindPlate
    SensitiveKind.ADDRESS -> KindAddress
    SensitiveKind.LONG_NUMBER -> KindNumber
    SensitiveKind.QR -> KindCode
    SensitiveKind.FACE -> KindFace
    SensitiveKind.MANUAL -> KindManual
}

fun kindIcon(kind: SensitiveKind): ImageVector = when (kind) {
    SensitiveKind.PHONE -> Icons.Rounded.Phone
    SensitiveKind.NAME -> Icons.Rounded.Person
    SensitiveKind.AVATAR -> Icons.Rounded.AccountCircle
    SensitiveKind.EMAIL -> Icons.Rounded.Email
    SensitiveKind.CARD -> Icons.Rounded.CreditCard
    SensitiveKind.ID_NUMBER -> Icons.Rounded.Badge
    SensitiveKind.PASSPORT -> Icons.Rounded.Flight
    SensitiveKind.ACCOUNT_NUMBER -> Icons.Rounded.AccountBalance
    SensitiveKind.OTP -> Icons.Rounded.Lock
    SensitiveKind.DOB -> Icons.Rounded.Cake
    SensitiveKind.SECRET -> Icons.Rounded.Key
    SensitiveKind.LOCATION -> Icons.Rounded.LocationOn
    SensitiveKind.NETWORK -> Icons.Rounded.Router
    SensitiveKind.PLATE -> Icons.Rounded.DirectionsCar
    SensitiveKind.ADDRESS -> Icons.Rounded.Home
    SensitiveKind.LONG_NUMBER -> Icons.Rounded.Numbers
    SensitiveKind.QR -> Icons.Rounded.QrCode2
    SensitiveKind.FACE -> Icons.Rounded.Face
    SensitiveKind.MANUAL -> Icons.Rounded.Edit
}

fun kindGroupOf(kind: SensitiveKind): KindGroup = kind.group

fun styleIcon(style: RedactionStyle): ImageVector = when (style) {
    RedactionStyle.BLUR -> Icons.Rounded.BlurOn
    RedactionStyle.PIXELATE -> Icons.Rounded.GridOn
    RedactionStyle.BLACK_BAR -> Icons.Rounded.CropSquare
    RedactionStyle.TINT -> Icons.Rounded.Palette
}

@Composable
fun styleLabel(style: RedactionStyle): String = stringResource(
    when (style) {
        RedactionStyle.BLUR -> R.string.editor_style_blur
        RedactionStyle.PIXELATE -> R.string.editor_style_pixelate
        RedactionStyle.BLACK_BAR -> R.string.editor_style_black
        RedactionStyle.TINT -> R.string.editor_style_color
    },
)
