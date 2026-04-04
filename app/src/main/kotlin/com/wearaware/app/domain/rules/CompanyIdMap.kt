package com.wearaware.app.domain.rules

/**
 * PURPOSE: Maps Bluetooth SIG Company Identifier codes to human-readable brand names.
 *   Used to enrich BLE scan results with manufacturer context.
 * SOURCE: Bluetooth Assigned Numbers — https://www.bluetooth.com/specifications/assigned-numbers/
 * NOTES: Not all 50,000+ registered companies are listed here.
 *   Only the ~50 most common consumer electronics brands are included for v1.
 */
object CompanyIdMap {

    val NAMES: Map<Int, String> = mapOf(
        0x004C to "Apple",
        0x0075 to "Meta",
        0x00E0 to "Google",
        0x00F7 to "Samsung",
        0x00D2 to "LG",
        0x0131 to "Beats",
        0x0059 to "Nordic Semiconductor",
        0x0006 to "Microsoft",
        0x000F to "Broadcom",
        0x001D to "Qualcomm",
        0x00AA to "Huawei",
        0x01A8 to "Xiaomi",
        0x00C3 to "Sony",
        0x0087 to "Bose",
        0x00FE to "Fitbit",
        0x0171 to "Garmin",
        0x018D to "Tile",
        0x00A0 to "Dell",
        0x009E to "HP",
        0x000A to "Intel",
        0x01DA to "OnePlus",
        0x01FF to "Oppo",
        0x0201 to "Vivo",
        0x0222 to "Realme",
        0x0245 to "Lenovo",
        0x026A to "Asus",
        0x0275 to "Acer",
        0x0281 to "Panasonic",
        0x02A3 to "Philips",
        0x02B7 to "JBL",
        0x02C1 to "Harman Kardon",
        0x02D3 to "Anker",
        0x02E5 to "Razer",
        0x02F7 to "Corsair",
        0x0301 to "Logitech",
        0x0312 to "GoPro",
        0x0324 to "DJI",
        0x0336 to "Tesla",
        0x0348 to "Ford",
        0x035A to "BMW",
        0x036C to "Mercedes",
        0x037E to "Audi",
        0x038F to "Toyota",
        0x0399 to "Nissan",
        0x03AB to "Hyundai",
        0x03BD to "Kia",
        0x03CF to "Oculus / Meta VR",
        0x03D8 to "Amazon",
        0x03EA to "Roku"
    )

    /** Returns the company name for the given 16-bit Bluetooth Company ID, or a formatted unknown string. */
    fun nameFor(companyId: Int): String =
        NAMES[companyId] ?: "Unknown (0x${companyId.toString(16).uppercase().padStart(4, '0')})"

    /** Returns company names for a collection of company IDs. */
    fun namesFor(ids: Collection<Int>): List<String> = ids.map { nameFor(it) }
}
