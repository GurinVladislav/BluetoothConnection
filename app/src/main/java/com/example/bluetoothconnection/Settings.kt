package com.example.bluetoothconnection

import java.sql.Time

data class Settings( var fan_c: Boolean,
                     var fan_h: Boolean,
                     var heat: Boolean,
                     var servo: Int,
                     var mod: Int,
                     var time: Array<Int> = arrayOf(0, 0, 0))