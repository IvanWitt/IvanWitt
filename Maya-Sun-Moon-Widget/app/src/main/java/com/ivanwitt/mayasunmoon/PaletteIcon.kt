package com.ivanwitt.mayasunmoon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

object PaletteIcon {
    private const val PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAACzElEQVR42u2dLXbDMBCELT2j+gQ9SXhAUEloSVFv0GP0BkUloSVFBeE5SU7g0JQV1D/SWitpVzsDVSfP/kYj7cap03UQBEEQBEEQBEEQBEEQBEEW5LSc6DiOd+prhmFwMKAQbK2muNaBSzfEWQUvxQhnHXxtI5x28Nef/WTs8XBWY4TTPOPn4HOaUMIIpxF8CD63CTmN8K3CpxxX83p8q/C1mNCXhn/dz2ya53OnReM43jmXI18b/tq4ZBPEGbAVvnUTvAT4lk3wUuDHHk8tLTlL0Rwm9BJiSNXj4czaBxxPt8nY1/NDkY3Za4MfCzcF/to4Nw+vEX4Icir8kib0nXJtXeNj4R5PN9JylD0BVJepTVaJpow6synHU/n4IrM0EqqmjrhKAlLW/hDcluBTOPmSJ7YE2eLMJ2/CXJWPFdixvYG3OOuoVU31KkjyTfTcJqTAj+FmMgGxcHPOfBgQgFwCftdF3pRvcQkqpdBG7AG/bk/Qt3jRn6+XydjLx05/J6wV/tp4bbnUCHHp6fI2GfvevbPAr52EtX1ARALm4K+Np8xwaUnwUuFTTKBClWSClwx/SxK0yUuH37oJ5jvh2uqBIFyt5KwEm0gAtbT8f/wwDG6tVAz9PasBGv7XlmLCHHyOen7ra6olgNpkxRwfMiEFfq4JWXUJijWBYtaSCRzwc5gg4uPotRKTmpRSEGOZsCxBufeBJchS4ce+R8wxjttxzeUmdwpYvxWhpRrSZjI6YU0fRSAF/JyQAE0JaCUFHAXF0ntQ+SAB2hKAFPDN/qQEWDWBE37yEmTNBG74pE7YQpe85YZM6iR0NeNsqd7PXgVZa9K4rtdLPCkr8LP0Aa2boOKOWKsm5Lgur+lkW4PPWgW1WiGpfm6oZiOaenKuJiOafna0ZCNMPT1dkhGmfz+ghiH4BY2CpuAeNgRBEARBEARBEARBEARBf/oFUwaHh8IX2K0AAAAASUVORK5CYII="

    private var cached: Bitmap? = null

    fun bitmap(): Bitmap {
        cached?.let { return it }
        val bytes = Base64.decode(PNG_BASE64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).also { cached = it }
    }
}
