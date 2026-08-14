package tachiyomi.data

import app.cash.sqldelight.ColumnAdapter
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object AnimeUpdateStrategyColumnAdapter : ColumnAdapter<AnimeUpdateStrategy, Long> {
    override fun decode(databaseValue: Long): AnimeUpdateStrategy =
        AnimeUpdateStrategy.entries.getOrElse(databaseValue.toInt()) { AnimeUpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: AnimeUpdateStrategy): Long = value.ordinal.toLong()
}

object FetchTypeColumnAdapter : ColumnAdapter<FetchType, Long> {
    override fun decode(databaseValue: Long): FetchType =
        FetchType.entries.getOrElse(databaseValue.toInt()) { FetchType.Episodes }

    override fun encode(value: FetchType): Long = value.ordinal.toLong()
}

object MemoColumnAdapter : ColumnAdapter<JsonObject, ByteArray> {
    override fun decode(databaseValue: ByteArray): JsonObject {
        return Json.decodeFromString<JsonObject>(databaseValue.decodeToString())
    }

    override fun encode(value: JsonObject): ByteArray {
        return value.toString().encodeToByteArray()
    }
}
