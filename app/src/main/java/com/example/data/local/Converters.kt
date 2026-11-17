package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.local.entity.AmountRoundingMode
import com.example.data.local.entity.FirstDayOfWeek
import com.example.data.local.entity.ReportStatus
import com.example.data.local.entity.ThemeMode
import com.example.data.local.entity.TimeEntryStatus

class Converters {
    @TypeConverter
    fun fromReportStatus(value: ReportStatus): String = value.name

    @TypeConverter
    fun toReportStatus(value: String): ReportStatus = ReportStatus.valueOf(value)

    @TypeConverter
    fun fromTimeEntryStatus(value: TimeEntryStatus): String = value.name

    @TypeConverter
    fun toTimeEntryStatus(value: String): TimeEntryStatus = TimeEntryStatus.valueOf(value)

    @TypeConverter
    fun fromAmountRoundingMode(value: AmountRoundingMode): String = value.name

    @TypeConverter
    fun toAmountRoundingMode(value: String): AmountRoundingMode = AmountRoundingMode.valueOf(value)

    @TypeConverter
    fun fromFirstDayOfWeek(value: FirstDayOfWeek): String = value.name

    @TypeConverter
    fun toFirstDayOfWeek(value: String): FirstDayOfWeek = FirstDayOfWeek.valueOf(value)

    @TypeConverter
    fun fromThemeMode(value: ThemeMode): String = value.name

    @TypeConverter
    fun toThemeMode(value: String): ThemeMode = ThemeMode.valueOf(value)
}
