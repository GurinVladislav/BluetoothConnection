package com.example.bluetoothconnection

import java.sql.Time

data class Settings( var fan_c: Int,
                     var fan_h: Int,
                     var heat: Boolean,
                     var servo: Int,
                     var mod: Int,
                     var time: Array<Int> = arrayOf(0, 0, 0))